package com.appathy.walknapp.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * フォアグラウンドサービスと UI の橋渡し。
 * サービスが計測結果を流し込み、Compose 側は StateFlow を購読するだけにする。
 */
object WalkRuntime {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _tick = MutableStateFlow<Tick?>(null)
    val tick: StateFlow<Tick?> = _tick

    private val _route = MutableStateFlow<List<TrackPoint>>(emptyList())
    val route: StateFlow<List<TrackPoint>> = _route

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    var sessionId: Long? = null
        internal set

    fun setRunning(v: Boolean) {
        _running.value = v
        if (!v) {
            _tick.value = null
            _route.value = emptyList()
        }
    }

    fun publish(t: Tick, points: List<TrackPoint>) {
        _tick.value = t
        _route.value = points
    }

    fun notify(message: String) {
        _notice.value = message
    }

    fun consumeNotice(): String? {
        val v = _notice.value
        _notice.value = null
        return v
    }
}
