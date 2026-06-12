package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD: blank API key must return NoKey without ever touching the network.
 * baseUrl is pointed at 127.0.0.1:1 which would throw IOException immediately
 * if a network call were attempted — the NoKey guard must fire before that.
 */
class BdoAlertsApiTest {

    private val noKeyApi = BdoAlertsApi(
        keyProvider = { "" },
        baseUrl = "http://127.0.0.1:1",
    )

    @Test
    fun `blank key returns NoKey for playerProfile without network`() = runTest {
        val result = noKeyApi.playerProfile("na", "TestFamily")
        assertEquals(ApiResult.NoKey, result)
    }

    @Test
    fun `blank key returns NoKey for coupons without network`() = runTest {
        val result = noKeyApi.coupons()
        assertEquals(ApiResult.NoKey, result)
    }

    @Test
    fun `blank key returns NoKey for bossTimers without network`() = runTest {
        val result = noKeyApi.bossTimers("na")
        assertEquals(ApiResult.NoKey, result)
    }

    @Test
    fun `blank key returns NoKey for maintenanceStatus without network`() = runTest {
        val result = noKeyApi.maintenanceStatus("na")
        assertEquals(ApiResult.NoKey, result)
    }

    @Test
    fun `blank key returns NoKey for news without network`() = runTest {
        val result = noKeyApi.news(boardType = 1)
        assertEquals(ApiResult.NoKey, result)
    }
}
