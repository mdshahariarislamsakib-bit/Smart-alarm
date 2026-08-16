package com.smartalarm.app

data class AlarmData(
    val id: Long,
    var hour: Int,
    var minute: Int,
    var enabled: Boolean = true,
    var sound: String = "Extreme Siren",
    var customUri: String? = null,
    var math: Boolean = true,
    var camera: Boolean = true,
    var simon: Boolean = true
)
