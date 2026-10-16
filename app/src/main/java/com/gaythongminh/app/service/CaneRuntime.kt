package com.gaythongminh.app.service

import android.location.Location
import com.gaythongminh.app.ble.BleState
import kotlinx.coroutines.flow.MutableStateFlow

/** Trạng thái chạy nền của người dùng gậy, để giao diện quan sát. */
object CaneRuntime {
    val bleState = MutableStateFlow(BleState.OFF)
    val location = MutableStateFlow<Location?>(null)
    val fallWarning = MutableStateFlow(false)      // gậy đang đếm ngược chờ bấm nút hủy
    val lastEvent = MutableStateFlow("")
    val running = MutableStateFlow(false)

    fun reset() {
        bleState.value = BleState.OFF
        location.value = null
        fallWarning.value = false
        lastEvent.value = ""
        running.value = false
    }
}
