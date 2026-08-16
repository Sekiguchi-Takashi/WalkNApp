package com.appathy.walknapp.session

object SpeedFormat {

    fun kmh(mps: Double): String = String.format("%.1f km/h", mps * 3.6)

    fun pace(mps: Double): String {
        if (mps < 0.15) return "--:-- /km"
        val secPerKm = 1000.0 / mps
        if (secPerKm > 3600) return "--:-- /km"
        val m = (secPerKm / 60).toInt()
        val s = (secPerKm % 60).toInt()
        return String.format("%d:%02d /km", m, s)
    }

    fun label(mps: Double): String {
        val kmh = mps * 3.6
        return when {
            kmh < 0.5 -> "停止中"
            kmh < 3.0 -> "ゆっくり"
            kmh < 5.0 -> "ふつう"
            kmh < 6.5 -> "早歩き"
            kmh < 8.5 -> "かなり速い"
            else -> "走行中"
        }
    }
}
