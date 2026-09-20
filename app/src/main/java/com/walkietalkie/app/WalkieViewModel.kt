package com.walkietalkie.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface ConnectionState { data object Disconnected : ConnectionState; data object Connecting : ConnectionState; data object Connected : ConnectionState; data class Error(val message: String) : ConnectionState }

data class WalkieState(val connection: ConnectionState = ConnectionState.Disconnected, val channel: String = "ops", val speaking: Boolean = false, val speakerId: String? = null)

class WalkieViewModel : ViewModel() {
    private val client = OkHttpClient()
    private var socket: WebSocket? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var reconnectAttempt = 0
    private var requestedUserId: String? = null
    private val _state = MutableStateFlow(WalkieState())
    val state: StateFlow<WalkieState> = _state.asStateFlow()

    fun connect(userId: String) {
        if (_state.value.connection is ConnectionState.Connecting || _state.value.connection is ConnectionState.Connected) return
        requestedUserId = userId
        _state.value = _state.value.copy(connection = ConnectionState.Connecting)
        val encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8)
        val request = Request.Builder().url("${BuildConfig.WALKIE_WS_URL}?userId=$encodedUserId").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { reconnectAttempt = 0; socket = webSocket; _state.value = _state.value.copy(connection = ConnectionState.Connected); joinChannel() }
            override fun onMessage(webSocket: WebSocket, text: String) { handleEvent(JSONObject(text)) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { socket = null; _state.value = _state.value.copy(connection = ConnectionState.Error("Connection lost"), speaking = false); scheduleReconnect() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { _state.value = _state.value.copy(connection = ConnectionState.Disconnected, speaking = false) }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || requestedUserId == null) return
        reconnectJob = viewModelScope.launch {
            val waitMs = (1000L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(30_000L)
            reconnectAttempt++
            delay(waitMs)
            requestedUserId?.let { connect(it) }
        }
    }

    fun joinChannel() { send(JSONObject().put("version", 1).put("type", "join_channel").put("requestId", System.currentTimeMillis().toString()).put("channelId", _state.value.channel)) }
    fun startTalking() { if (_state.value.connection is ConnectionState.Connected) send(JSONObject().put("version", 1).put("type", "request_talk").put("requestId", System.currentTimeMillis().toString())) }
    fun stopTalking() { if (_state.value.speaking) { send(JSONObject().put("version", 1).put("type", "release_talk").put("requestId", System.currentTimeMillis().toString())); _state.value = _state.value.copy(speaking = false) } }
    private fun send(message: JSONObject) { socket?.send(message.toString()) }
    private fun handleEvent(event: JSONObject) { when (event.optString("type")) { "talk_granted" -> _state.value = _state.value.copy(speaking = true, speakerId = event.optString("speakerId")); "talk_denied" -> _state.value = _state.value.copy(speaking = false); "speaker_changed" -> _state.value = _state.value.copy(speakerId = event.optString("speakerId").ifBlank { null }) } }
    override fun onCleared() { reconnectJob?.cancel(); requestedUserId = null; socket?.close(1000, "closed"); client.dispatcher.executorService.shutdown() }
}
