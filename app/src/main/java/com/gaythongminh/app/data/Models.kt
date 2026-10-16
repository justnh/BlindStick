package com.gaythongminh.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Role { CANE, RELATIVE }

data class LinkInfo(
    val id: String,
    val caneUid: String,
    val caneEmail: String,
    val relativeUid: String,
    val relativeEmail: String,
    val linkedAt: Long
)

data class CaneStatus(
    val status: String = "SAFE",          // SAFE | FALLEN
    val lat: Double? = null,
    val lon: Double? = null,
    val locAt: Long = 0,                  // thời điểm ghi nhận vị trí (ms)
    val bleConnected: Boolean = false,    // ESP32 có đang nối Bluetooth với điện thoại không
    val lastFallAt: Long = 0,
    val fallLat: Double? = null,
    val fallLon: Double? = null
) {
    val isFallen get() = status == "FALLEN"
}

data class FallEvent(val id: String, val time: Long, val lat: Double?, val lon: Double?)

fun mapsLink(lat: Double, lon: Double): String =
    String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", lat, lon)

fun coordText(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.6f, %.6f", lat, lon)

fun formatTime(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(ms))
