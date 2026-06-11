package com.gpowell.bdoboss.live

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class BossAlertsSocket(private val scope: CoroutineScope) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private var wantConnected = false

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    // ── Public API ─────────────────────────────────────────────────────────────

    fun connect() {
        wantConnected = true
        openSocket()
    }

    fun disconnect() {
        wantConnected = false
        pingJob?.cancel()
        socket?.close(1000, null)
        socket = null
        _state.value = _state.value.copy(connected = false)
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun openSocket() {
        pingJob?.cancel()
        socket?.close(1000, null)
        val request = Request.Builder()
            .url("wss://api.bdoalerts.net/ws?region=na")
            .build()
        socket = client.newWebSocket(request, listener)
        pingJob = scope.launch {
            while (isActive) {
                delay(25_000)
                socket?.send("""{"type":"ping"}""")
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _state.value = _state.value.copy(connected = true)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { handle(json.parseToJsonElement(text)) }
                .onFailure { Log.w(TAG, "bad ws message", it) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _state.value = _state.value.copy(connected = false)
            if (wantConnected) {
                scope.launch {
                    delay(5_000)
                    if (wantConnected) openSocket()
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _state.value = _state.value.copy(connected = false)
        }
    }

    // ── Message dispatcher ─────────────────────────────────────────────────────

    internal fun handle(el: JsonElement) {
        val obj = el.jsonObject
        when (obj["type"]?.jsonPrimitive?.content) {
            "connection_ack" -> handleConnectionAck(obj)
            "boss_timers"    -> handleBossTimers(obj["data"]?.jsonObject ?: return)
            "reset_timers"   -> handleResetTimers(obj["data"]?.jsonObject ?: return)
            "cave_status"    -> handleCaveStatus(obj["data"]?.jsonObject ?: return)
            "heartbeat"      -> Unit // nothing to do
            else             -> Unit // unknown type — ignore quietly
        }
    }

    private fun handleConnectionAck(obj: JsonObject) {
        val cached = obj["cached_data"]?.jsonObject ?: return
        cached["boss_timers"]?.jsonObject?.let { handleBossTimers(it) }
        cached["reset_timers"]?.jsonObject?.let { handleResetTimers(it) }
        cached["cave_status"]?.jsonObject?.let { handleCaveStatus(it) }
    }

    private fun handleBossTimers(data: JsonObject) {
        val bossesEl = data["bosses"]?.jsonArray ?: return
        val bosses = runCatching {
            json.decodeFromJsonElement(ListSerializer(WsBoss.serializer()), bossesEl)
        }.getOrElse { Log.w(TAG, "boss_timers parse error", it); return }
        _state.value = _state.value.copy(bosses = bosses)
    }

    private fun handleResetTimers(data: JsonObject) {
        val rt = runCatching {
            json.decodeFromJsonElement(WsResetTimers.serializer(), data)
        }.getOrElse { Log.w(TAG, "reset_timers parse error", it); return }
        _state.value = _state.value.copy(
            dailyResetCountdown = rt.dailyReset?.countdown,
            weeklyResetCountdown = rt.weeklyReset?.countdown,
        )
    }

    private fun handleCaveStatus(data: JsonObject) {
        val cs = runCatching {
            json.decodeFromJsonElement(WsCaveStatus.serializer(), data)
        }.getOrElse { Log.w(TAG, "cave_status parse error", it); return }
        _state.value = _state.value.copy(
            caveOpen = when (cs.status?.uppercase()) {
                "OPEN"   -> true
                "CLOSED" -> false
                else     -> null
            },
        )
    }

    private companion object {
        const val TAG = "BossAlertsSocket"
    }
}
