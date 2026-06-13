package com.gpowell.bdoboss

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.gpowell.bdoboss.data.market.ItemIconResolver
import com.gpowell.bdoboss.notify.NotificationHelper
import okhttp3.OkHttpClient

/**
 * Provides the process-wide Coil [ImageLoader]. Central Market icons come from
 * bdocodex's image host, which 403s requests with no browser User-Agent — so we
 * attach one here (the default Coil/OkHttp UA gets blocked, which is why icons
 * never appeared). Crossfade keeps the monogram→icon swap calm.
 */
class BdoBossApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        // Load the persisted id→icon-URL map so we don't re-hit bdocodex every launch.
        ItemIconResolver.attach(this)
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
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
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            // Item icons are immutable — cache the bytes on disk and in memory so a
            // seen icon shows instantly with zero network (survives restarts & outages).
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.15).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("item_icons"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
