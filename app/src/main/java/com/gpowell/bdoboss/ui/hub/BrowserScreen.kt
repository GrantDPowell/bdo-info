package com.gpowell.bdoboss.ui.hub

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * A current Chrome-on-Android user agent. The platform WebView's default UA
 * advertises "; wv" and an old Chromium build, which Discord (and some other
 * sites) reject with an "unsupported browser" wall that blocks login. Presenting
 * a normal mobile-Chrome UA clears that gate.
 */
private const val HUB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SM-S938U) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

/** Desktop Chrome UA — used by the "desktop site" toggle (e.g. Discord's QR login page). */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

/** Empty 200 body served in place of a blocked ad/tracker request. */
private fun blankAdResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

/** Shared WebView config for both the main view and OAuth popups. */
private fun WebView.applyHubSettings() {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    @Suppress("DEPRECATION")
    settings.databaseEnabled = true
    // hCaptcha / Cloudflare challenge assets sometimes load over mixed schemes.
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.userAgentString = HUB_USER_AGENT
    // Discord (and many) logins open the OAuth consent in a popup window.
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = true
    if (Build.VERSION.SDK_INT >= 33) {
        // Lets sites without their own dark theme render dark on API 33+.
        settings.isAlgorithmicDarkeningAllowed = true
    }
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    // OAuth (e.g. "Log in with Discord") hands the session back across origins.
    cookies.setAcceptThirdPartyCookies(this, true)
}

/** [WebViewClient] that drops ad/tracker requests via [AdBlocker]. */
private open class AdBlockingWebViewClient : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? =
        if (AdBlocker.isAdHost(request?.url?.host)) blankAdResponse() else null
}

/**
 * In-app browser for the Hub tab: toolbar (exit / title / bookmark star /
 * refresh / open-in-Chrome), thin progress bar, and a WebView. Ad/tracker
 * requests are filtered ([AdBlocker]); OAuth login popups open in a full-screen
 * overlay WebView so flows like "Log in with Discord" complete.
 *
 * WebView lifecycle tradeoff: ONE WebView instance per browser-view composition
 * (remember). We deliberately do NOT hoist or cache it above this composable —
 * exiting to the launcher (or leaving the Hub tab) disposes the composition and
 * drops the WebView, losing in-page state (scroll, history, form input). That's
 * acceptable for v1 because cookies live in the process-global CookieManager,
 * which persists to disk — site logins survive WebView disposal and app
 * restarts. We also skip webView.destroy(): the instance simply goes away with
 * the composition, and destroying it eagerly would break the view if Compose
 * ever re-attached it during transitions.
 */
@Composable
fun BrowserScreen(
    initialUrl: String,
    repo: FavoritesRepository,
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by repo.favorites.collectAsState(initial = emptyList())

    var pageTitle by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf(initialUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var desktopMode by remember { mutableStateOf(false) }
    // Non-null while an OAuth login popup is showing as a full-screen overlay.
    var popupView by remember { mutableStateOf<WebView?>(null) }
    // Holder so the OAuth popup's onCloseWindow can reload the main view (forward ref).
    val mainHolder = remember { arrayOfNulls<WebView>(1) }

    val webView = remember {
        WebView(ctx).apply {
            mainHolder[0] = this
            applyHubSettings()
            webViewClient = object : AdBlockingWebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null) pageUrl = url
                    pageTitle = view?.title.orEmpty()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }

                // A site asked to open a new window (OAuth popup). Host it as an
                // overlay WebView with window.opener intact so postMessage-based
                // login handshakes (Discord et al.) can complete.
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    val msg = resultMsg ?: return false
                    val popup = WebView(ctx).apply {
                        applyHubSettings()
                        webViewClient = AdBlockingWebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView?) {
                                // OAuth popups (Discord et al.) run in a separate JS context, so
                                // the window.opener postMessage handshake can't reach the main
                                // page. When the popup finishes and closes, flush cookies and
                                // reload the main view so it picks up the now-set session.
                                popupView = null
                                CookieManager.getInstance().flush()
                                mainHolder[0]?.reload()
                            }
                        }
                    }
                    (msg.obj as WebView.WebViewTransport).webView = popup
                    msg.sendToTarget()
                    popupView = popup
                    return true
                }
            }
            loadUrl(initialUrl)
        }
    }

    // Flush cookies to disk when the browser goes away so fresh logins stick.
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }

    // System back: close an open login popup, then in-page history, then exit.
    BackHandler {
        when {
            popupView != null -> popupView = null
            webView.canGoBack() -> webView.goBack()
            else -> onExit()
        }
    }

    val isFav = Favorite.findMatch(favorites, FavoriteType.PAGE, url = pageUrl) != null

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to hub",
                        tint = BdoGold,
                    )
                }
                Text(
                    pageTitle.ifBlank { pageUrl },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = {
                    val title = pageTitle.ifBlank { Uri.parse(pageUrl).host ?: pageUrl }
                    scope.launch {
                        repo.toggle(FavoriteType.PAGE, title = title, url = pageUrl)
                    }
                }) {
                    Icon(
                        if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isFav) "Remove bookmark" else "Bookmark page",
                        tint = if (isFav) BdoGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    desktopMode = !desktopMode
                    webView.settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else HUB_USER_AGENT
                    webView.settings.useWideViewPort = desktopMode
                    webView.settings.loadWithOverviewMode = desktopMode
                    webView.reload()
                }) {
                    Icon(
                        if (desktopMode) Icons.Filled.PhoneAndroid else Icons.Filled.Computer,
                        contentDescription = if (desktopMode) "Mobile site" else "Desktop site",
                        tint = if (desktopMode) BdoGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { webView.reload() }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in browser",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = BdoGold,
                    trackColor = Color.Transparent,
                )
            }
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        // OAuth login popup overlay (e.g. "Log in with Discord").
        popupView?.let { popup ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        popupView = null
                        CookieManager.getInstance().flush()
                        webView.reload()
                    }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close login window",
                            tint = BdoGold,
                        )
                    }
                    Text(
                        "Sign in",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AndroidView(
                    factory = { popup },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
