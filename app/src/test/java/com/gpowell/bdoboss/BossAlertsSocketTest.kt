package com.gpowell.bdoboss

import com.gpowell.bdoboss.live.BossAlertsSocket
import com.gpowell.bdoboss.live.LiveState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for BossAlertsSocket.handle() — the pure JSON→LiveState parsing logic.
 *
 * All payloads are real shapes captured from wss://api.bdoalerts.net/ws?region=na.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BossAlertsSocketTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var socket: BossAlertsSocket

    @Before
    fun setup() {
        socket = BossAlertsSocket(TestScope())
    }

    // ── connection_ack ─────────────────────────────────────────────────────────

    @Test
    fun `connection_ack populates bosses from cached_data`() {
        socket.handle(
            json.parseToJsonElement(CONNECTION_ACK),
        )
        val state = socket.state.value
        assertTrue(state.bosses.isNotEmpty())
        val sangoon = state.bosses.first { it.bossName == "Sangoon" }
        assertEquals("2026-06-11T10:00:00-07:00", sangoon.nextSpawn)
        assertEquals(0, sangoon.timeUntil!!.hours)
        assertEquals(15, sangoon.timeUntil!!.minutes)
    }

    @Test
    fun `connection_ack populates daily and weekly reset countdowns from cached_data`() {
        socket.handle(json.parseToJsonElement(CONNECTION_ACK))
        val state = socket.state.value
        assertEquals("7h 15m", state.dailyResetCountdown)
        assertEquals("6d 7h 15m", state.weeklyResetCountdown)
    }

    @Test
    fun `connection_ack populates cave status OPEN from cached_data`() {
        socket.handle(json.parseToJsonElement(CONNECTION_ACK))
        assertEquals(true, socket.state.value.caveOpen)
    }

    // ── boss_timers ────────────────────────────────────────────────────────────

    @Test
    fun `boss_timers message overwrites bosses list`() {
        socket.handle(json.parseToJsonElement(BOSS_TIMERS))
        val state = socket.state.value
        val kzarka = state.bosses.firstOrNull { it.bossName == "Kzarka" }
        assertTrue(kzarka != null)
        assertEquals("2026-06-11T10:00:00-07:00", kzarka!!.nextSpawn)
        assertEquals(15, kzarka.timeUntil!!.minutes)
        assertEquals(17, kzarka.timeUntil!!.seconds)
    }

    @Test
    fun `boss_timers preserves reset and cave state unchanged`() {
        // seed reset data first
        socket.handle(json.parseToJsonElement(RESET_TIMERS))
        val resetBefore = socket.state.value.dailyResetCountdown
        socket.handle(json.parseToJsonElement(BOSS_TIMERS))
        assertEquals(resetBefore, socket.state.value.dailyResetCountdown)
    }

    // ── reset_timers ───────────────────────────────────────────────────────────

    @Test
    fun `reset_timers sets daily and weekly countdown strings`() {
        socket.handle(json.parseToJsonElement(RESET_TIMERS))
        val state = socket.state.value
        assertEquals("7h 15m", state.dailyResetCountdown)
        assertEquals("6d 7h 15m", state.weeklyResetCountdown)
    }

    @Test
    fun `reset_timers does not clear bosses`() {
        socket.handle(json.parseToJsonElement(BOSS_TIMERS))
        val bossCount = socket.state.value.bosses.size
        socket.handle(json.parseToJsonElement(RESET_TIMERS))
        assertEquals(bossCount, socket.state.value.bosses.size)
    }

    // ── cave_status ────────────────────────────────────────────────────────────

    @Test
    fun `cave_status OPEN sets caveOpen true`() {
        socket.handle(json.parseToJsonElement(CAVE_STATUS_OPEN))
        assertTrue(socket.state.value.caveOpen == true)
    }

    @Test
    fun `cave_status CLOSED sets caveOpen false`() {
        socket.handle(json.parseToJsonElement(CAVE_STATUS_CLOSED))
        assertFalse(socket.state.value.caveOpen == true)
        assertEquals(false, socket.state.value.caveOpen)
    }

    @Test
    fun `cave_status does not clear bosses or resets`() {
        socket.handle(json.parseToJsonElement(BOSS_TIMERS))
        socket.handle(json.parseToJsonElement(RESET_TIMERS))
        val bossCount = socket.state.value.bosses.size
        val daily = socket.state.value.dailyResetCountdown
        socket.handle(json.parseToJsonElement(CAVE_STATUS_OPEN))
        assertEquals(bossCount, socket.state.value.bosses.size)
        assertEquals(daily, socket.state.value.dailyResetCountdown)
    }

    // ── heartbeat ─────────────────────────────────────────────────────────────

    @Test
    fun `heartbeat message does not change state`() {
        val before = socket.state.value
        socket.handle(json.parseToJsonElement(HEARTBEAT))
        assertEquals(before, socket.state.value)
    }

    // ── unknown type ──────────────────────────────────────────────────────────

    @Test
    fun `unknown message type is silently ignored`() {
        val before = socket.state.value
        socket.handle(json.parseToJsonElement("""{"type":"mystery_event","data":{}}"""))
        assertEquals(before, socket.state.value)
    }

    // ── malformed inputs ──────────────────────────────────────────────────────

    @Test
    fun `boss_timers with missing bosses array is ignored gracefully`() {
        val before = socket.state.value
        socket.handle(json.parseToJsonElement("""{"type":"boss_timers","region":"na","data":{"region":"na"}}"""))
        assertEquals(before, socket.state.value)
    }

    @Test
    fun `cave_status with unknown status string yields null caveOpen`() {
        socket.handle(json.parseToJsonElement("""{"type":"cave_status","region":"na","data":{"status":"MAINTENANCE"}}"""))
        assertNull(socket.state.value.caveOpen)
    }

    // ── initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is disconnected with empty data`() {
        val state = socket.state.value
        assertFalse(state.connected)
        assertTrue(state.bosses.isEmpty())
        assertNull(state.dailyResetCountdown)
        assertNull(state.weeklyResetCountdown)
        assertNull(state.caveOpen)
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    companion object {
        private val CONNECTION_ACK = """
            {"type":"connection_ack","region":"na","subscriptions":["cave_status","reset_timers","boss_timers"],
            "cached_data":{
              "boss_timers":{"region":"na","current_time":"2026-06-11T16:44:08.762177+00:00",
                "bosses":[
                  {"boss_name":"Sangoon","next_spawn":"2026-06-11T10:00:00-07:00","time_until":{"hours":0,"minutes":15,"seconds":51}},
                  {"boss_name":"Kzarka","next_spawn":"2026-06-11T10:00:00-07:00","time_until":{"hours":0,"minutes":15,"seconds":51}}
                ]
              },
              "reset_timers":{"region":"na","current_time":"2026-06-11T16:44:08.763641+00:00",
                "daily_reset":{"next_reset":"2026-06-12T00:00:00+00:00","time_until":{"hours":7,"minutes":15,"seconds":51,"total_seconds":26151},"countdown":"7h 15m"},
                "weekly_reset":{"next_reset":"2026-06-18T00:00:00+00:00","time_until":{"hours":151,"minutes":15,"seconds":51,"total_seconds":544551},"countdown":"6d 7h 15m"}
              },
              "cave_status":{"region":"na","status":"OPEN","last_checked":"2026-06-11T16:08:11+00:00"}
            },
            "server_time":"2026-06-11T16:44:13.218758+00:00"}
        """.trimIndent()

        private val BOSS_TIMERS = """
            {"type":"boss_timers","region":"na","data":{
              "region":"na","current_time":"2026-06-11T16:44:42.260348+00:00",
              "bosses":[
                {"boss_name":"Sangoon","next_spawn":"2026-06-11T10:00:00-07:00","time_until":{"hours":0,"minutes":15,"seconds":17}},
                {"boss_name":"Kzarka","next_spawn":"2026-06-11T10:00:00-07:00","time_until":{"hours":0,"minutes":15,"seconds":17}},
                {"boss_name":"Garmoth","next_spawn":"2026-06-11T12:00:00-07:00","time_until":{"hours":2,"minutes":15,"seconds":17}},
                {"boss_name":"Quint","next_spawn":"2026-06-11T14:00:00-07:00","time_until":{"hours":4,"minutes":15,"seconds":17}},
                {"boss_name":"Muraka","next_spawn":"2026-06-11T14:00:00-07:00","time_until":{"hours":4,"minutes":15,"seconds":17}}
              ]
            },"timestamp":"2026-06-11T16:44:42.260348+00:00"}
        """.trimIndent()

        private val RESET_TIMERS = """
            {"type":"reset_timers","region":"na","data":{
              "region":"na","current_time":"2026-06-11T16:44:42.261358+00:00",
              "daily_reset":{"next_reset":"2026-06-12T00:00:00+00:00","time_until":{"hours":7,"minutes":15,"seconds":17,"total_seconds":26117},"countdown":"7h 15m"},
              "weekly_reset":{"next_reset":"2026-06-18T00:00:00+00:00","time_until":{"hours":151,"minutes":15,"seconds":17,"total_seconds":544517},"countdown":"6d 7h 15m"},
              "blackshrine_reset":{"next_reset":"2026-06-14T00:00:00+00:00","time_until":{"hours":55,"minutes":15,"seconds":17,"total_seconds":198917},"countdown":"2d 7h 15m"},
              "imperial_delivery":{"next_reset":"2026-06-11T18:00:00+00:00","time_until":{"hours":1,"minutes":15,"seconds":17,"total_seconds":4517},"countdown":"1h 15m"},
              "trade_reset":{"next_reset":"2026-06-11T20:00:00+00:00","time_until":{"hours":3,"minutes":15,"seconds":17,"total_seconds":11717},"countdown":"3h 15m"},
              "barter_reset":{"next_reset":"2026-06-11T20:00:00+00:00","time_until":{"hours":3,"minutes":15,"seconds":17,"total_seconds":11717},"countdown":"3h 15m"},
              "day_night_cycle":{"current_cycle":"Day","next_cycle":"Night","next_change":"2026-06-11T19:39:00+00:00","time_until":{"hours":2,"minutes":54,"seconds":17,"total_seconds":10457},"countdown":"2h 54m"}
            },"timestamp":"2026-06-11T16:44:42.261358+00:00"}
        """.trimIndent()

        private val CAVE_STATUS_OPEN = """
            {"type":"cave_status","region":"na","data":{"region":"na","status":"OPEN","last_checked":"2026-06-11T16:08:11+00:00"},"timestamp":"2026-06-11T16:44:23.265097+00:00"}
        """.trimIndent()

        private val CAVE_STATUS_CLOSED = """
            {"type":"cave_status","region":"na","data":{"region":"na","status":"CLOSED","last_checked":"2026-06-11T17:00:00+00:00"},"timestamp":"2026-06-11T17:00:01+00:00"}
        """.trimIndent()

        private val HEARTBEAT = """
            {"type":"heartbeat","data":{"server_time":"2026-06-11T16:44:15.142744+00:00"},"timestamp":"2026-06-11T16:44:15.142744+00:00"}
        """.trimIndent()
    }
}
