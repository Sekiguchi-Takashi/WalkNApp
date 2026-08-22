package com.appathy.walknapp.session

object SpeedFormat {

    fun kmh(v: Double): String = String.format("%.1f km/h", v)

    fun pace(kmh: Double): String {
        if (kmh < 0.5) return "--:-- /km"
        val secPerKm = 3600.0 / kmh
        if (secPerKm > 3600) return "--:-- /km"
        val m = (secPerKm / 60).toInt()
        val s = (secPerKm % 60).toInt()
        return String.format("%d:%02d /km", m, s)
    }

    fun clock(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%d:%02d", m, s)
    }
}
