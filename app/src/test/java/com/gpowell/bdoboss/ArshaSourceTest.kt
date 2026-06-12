package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.market.ArshaSource
import com.gpowell.bdoboss.data.market.MarketPrice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior tests for ArshaSource that never need a live server:
 * baseUrl points at 127.0.0.1:1, so any real network attempt throws
 * IOException immediately and must map to Offline.
 */
class ArshaSourceTest {

    private val source = ArshaSource(baseUrl = "http://127.0.0.1:1")

    @Test fun `empty id list returns Success without network`() = runTest {
        assertEquals(ApiResult.Success(emptyList<MarketPrice>()), source.prices(emptyList()))
    }

    @Test fun `IOException maps to Offline`() = runTest {
        assertEquals(ApiResult.Offline, source.prices(listOf(44195)))
        assertEquals(ApiResult.Offline, source.itemAll(44195))
        assertEquals(ApiResult.Offline, source.waitList())
        assertEquals(ApiResult.Offline, source.history(44195))
    }

    @Test fun `name search is unsupported and returns 404 without network`() = runTest {
        // arsha's /search endpoint is id-based only — name queries never hit the wire.
        assertEquals(ApiResult.HttpError(404), source.search("kzarka"))
    }

    @Test fun `numeric search routes to the id endpoint (Offline against dead host)`() = runTest {
        assertEquals(ApiResult.Offline, source.search("44195,16001"))
    }
}
