package com.gpowell.bdoboss.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Live reachability of a data source (for the Settings → Data Sources panel). */
enum class SourceStatus { CHECKING, UP, DOWN, BUNDLED }

/**
 * One external data source / API the app relies on, for the credits-and-status
 * panel in Settings. [healthUrl] null ⇒ bundled data (no live check, shown as
 * [SourceStatus.BUNDLED]); otherwise a cheap GET whose 2xx means "up".
 */
data class DataSource(
    val name: String,
    val role: String,
    val attribution: String,
    val siteUrl: String,
    val healthUrl: String?,
)

object DataSources {

    /**
     * Short call timeout — a status check should never hang the panel. A browser
     * User-Agent + Referer keeps Imperva-fronted hosts (bdocodex) from challenging
     * the probe and falsely reading as "offline".
     */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(7, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
                )
                .header("Referer", "https://bdocodex.com/")
                .build()
            chain.proceed(req)
        }
        .build()

    /** Every source/API behind BDO Info, with what it does and who to credit. */
    val all: List<DataSource> = listOf(
        DataSource(
            name = "arsha.io",
            role = "Primary Central Market source: live prices, category listings, " +
                "and 90-day price history. Reads Pearl Abyss's market API — when PA " +
                "blocks it (Imperva), this shows Offline until PA clears (usually minutes).",
            attribution = "Community market API by Arsha",
            siteUrl = "https://api.arsha.io",
            healthUrl = "https://api.arsha.io/v2/na/item?id=44195&lang=en",
        ),
        DataSource(
            name = "BlackDesertMarket.com",
            role = "Fallback market source for prices & categories. Reads the SAME " +
                "Pearl Abyss API as arsha, so it goes Offline at the same time when PA " +
                "blocks both (no independent data).",
            attribution = "Market mirror by sobekcore",
            siteUrl = "https://blackdesertmarket.com",
            healthUrl = "https://api.blackdesertmarket.com/item/44195?region=na&language=en",
        ),
        DataSource(
            name = "BDO Codex",
            role = "Real item icons and item database / drop-table links.",
            attribution = "bdocodex.com",
            siteUrl = "https://bdocodex.com",
            healthUrl = "https://bdocodex.com/ac.php?l=us&term=a",
        ),
        DataSource(
            name = "BDO Alerts",
            role = "Live world-boss spawn timers and reset countdowns (real-time feed).",
            attribution = "bdoalerts.net by LoadingMagic",
            siteUrl = "https://bdoalerts.net",
            healthUrl = "https://api.bdoalerts.net/openapi.json",
        ),
        DataSource(
            name = "mmotimer.com",
            role = "Boss portraits and the world-boss schedule cross-reference.",
            attribution = "mmotimer.com",
            siteUrl = "https://mmotimer.com",
            healthUrl = null,
        ),
        DataSource(
            name = "Veliainn market resources",
            role = "Central Market category names (bundled in the app).",
            attribution = "github.com/andreivreja",
            siteUrl = "https://github.com/andreivreja/veliainn-market-resources",
            healthUrl = null,
        ),
        DataSource(
            name = "Pearl Abyss / Black Desert",
            role = "Original owner of all game data. BDO Info is an unofficial fan app, " +
                "not affiliated with Pearl Abyss.",
            attribution = "© Pearl Abyss Corp.",
            siteUrl = "https://www.naeu.playblackdesert.com",
            healthUrl = null,
        ),
    )

    /** Live status for one source: BUNDLED if no healthUrl, else UP on a 2xx GET. */
    suspend fun status(source: DataSource): SourceStatus {
        val url = source.healthUrl ?: return SourceStatus.BUNDLED
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (response.isSuccessful) SourceStatus.UP else SourceStatus.DOWN
                }
            } catch (_: IOException) {
                SourceStatus.DOWN
            }
        }
    }
}
