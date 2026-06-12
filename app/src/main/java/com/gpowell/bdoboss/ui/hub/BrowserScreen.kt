package com.gpowell.bdoboss.ui.hub

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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

/**
 * In-app browser for the Hub tab: toolbar (exit / title / bookmark star /
 * refresh / open-in-Chrome), thin progress bar, and a WebView.
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

    val webView = remember {
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            if (Build.VERSION.SDK_INT >= 33) {
                // Lets sites without their own dark theme render dark on API 33+.
                settings.isAlgorithmicDarkeningAllowed = true
            }
            // Default CookieManager is persistent — logins survive app restarts.
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null) pageUrl = url
                    pageTitle = view?.title.orEmpty()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            loadUrl(initialUrl)
        }
    }

    // Flush cookies to disk when the browser goes away so fresh logins stick.
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }

    // System back: in-page history first, then back to the launcher view.
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onExit()
    }

    val isFav = Favorite.findMatch(favorites, FavoriteType.PAGE, url = pageUrl) != null

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
}
