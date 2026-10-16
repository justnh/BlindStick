package com.gaythongminh.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gaythongminh.app.ble.BleState
import com.gaythongminh.app.ble.CaneBle
import com.gaythongminh.app.data.Repo
import com.gaythongminh.app.util.Notifier
import com.gaythongminh.app.util.Speaker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chạy nền cho NGƯỜI DÙNG GẬY:
 *  - giữ kết nối Bluetooth với ESP32, nhận FALL / CANCEL / SENDING_ALERT ...
 *  - đọc GPS điện thoại, gửi lên Firestore (realtime) và gửi lệnh GPS,lat,lon cho ESP32
 *  - khi ESP32 báo SENDING_ALERT (hết thời gian chờ hủy) -> ghi "BỊ NGÃ" lên Firestore
 *  - đọc to mọi thông báo
 */
@SuppressLint("MissingPermission")
class CaneService : Service() {

    companion object {
        const val NOTIF_ID = 101
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CaneService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CaneService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var ble: CaneBle
    private lateinit var fused: FusedLocationProviderClient
    private var started = false
    private var lastPush = 0L
    private var fallAt = 0L
    private var prevBle = BleState.OFF
    private var prevStatus: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        ble = CaneBle(this, ::onBleMessage, ::onBleState)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.service(this, "Đang tìm gậy thông minh…")
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } catch (e: Exception) {
            // thiếu quyền Bluetooth -> chạy chỉ với vị trí
            try {
                ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } catch (e2: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!started) {
            started = true
            CaneRuntime.running.value = true
            startLocation()
            ble.start()
            watchOwnStatus()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val uid = Repo.currentUser?.uid
        if (uid != null) Repo.updateLocation(uid, null, null, false)
        runCatching { fused.removeLocationUpdates(locCb) }
        ble.stop()
        scope.cancel()
        CaneRuntime.reset()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ vị trí

    private val locCb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val l = r.lastLocation ?: return
            CaneRuntime.location.value = l
            val now = System.currentTimeMillis()
            if (now - lastPush >= 5000) {
                lastPush = now
                pushStatus(l)
                if (ble.isConnected) ble.sendGps(l.latitude, l.longitude)
            }
        }
    }

    private fun startLocation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateIntervalMillis(2000)
            .build()
        try {
            fused.requestLocationUpdates(req, locCb, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
    }

    private fun pushStatus(l: Location? = CaneRuntime.location.value) {
        val uid = Repo.currentUser?.uid ?: return
        Repo.updateLocation(uid, l?.latitude, l?.longitude, ble.isConnected)
    }

    private suspend fun bestLocation(): Location? {
        val cached = CaneRuntime.location.value
        if (cached != null && System.currentTimeMillis() - cached.time < 15_000) return cached
        val fresh = withTimeoutOrNull(4000) {
            runCatching { fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }.getOrNull()
        }
        return fresh ?: cached ?: runCatching { fused.lastLocation.await() }.getOrNull()
    }

    // ------------------------------------------------------------------ Bluetooth

    private fun say(text: String, interrupt: Boolean = false) {
        CaneRuntime.lastEvent.value = text
        Speaker.speak(text, interrupt)
    }

    private fun onBleState(s: BleState) {
        CaneRuntime.bleState.value = s
        if (s == BleState.CONNECTED && prevBle != BleState.CONNECTED) {
            say("Đã kết nối với gậy thông minh.")
            CaneRuntime.location.value?.let { ble.sendGps(it.latitude, it.longitude) }
            pushStatus()
        }
        if (prevBle == BleState.CONNECTED && s != BleState.CONNECTED) {
            say("Mất kết nối với gậy thông minh.")
            CaneRuntime.fallWarning.value = false
            pushStatus()
        }
        prevBle = s
        Notifier.updateService(
            this, NOTIF_ID,
            when (s) {
                BleState.CONNECTED -> "Đã kết nối với gậy"
                BleState.CONNECTING -> "Đang kết nối với gậy…"
                BleState.SCANNING -> "Đang tìm gậy thông minh…"
                BleState.OFF -> "Bluetooth đang tắt hoặc chưa cấp quyền"
            }
        )
    }

    /** Tin nhắn từ ESP32 (xem sendBLE trong firmware). */
    private fun onBleMessage(msg: String) {
        when {
            msg == "FALL" -> {
                fallAt = System.currentTimeMillis()
                CaneRuntime.fallWarning.value = true
                say("Phát hiện bạn có thể bị ngã. Nếu không bị ngã, hãy nhấn nút trên gậy.", true)
                // đưa vị trí mới nhất cho ESP32 trước khi nó gửi email
                CaneRuntime.location.value?.let { ble.sendGps(it.latitude, it.longitude) }
            }
            msg == "CANCEL" -> {
                CaneRuntime.fallWarning.value = false
                say("Đã xác nhận bạn an toàn.", true)
            }
            msg == "SENDING_ALERT" -> {
                CaneRuntime.fallWarning.value = false
                say("Không nhận được xác nhận. Đang gửi thông báo cho người thân.", true)
                reportFall()
            }
            msg == "ALERT_SENT" -> say("Đã gửi thông báo cho người thân.")
            msg == "ALERT_FAILED" -> say("Gậy không gửi được thư điện tử. Thông báo vẫn được gửi qua ứng dụng.")
            // HOLE, HIGH_OBSTACLE, ... : gậy tự đọc bằng DFPlayer nên app không đọc lại
            else -> Unit
        }
    }

    private fun reportFall() {
        val uid = Repo.currentUser?.uid ?: return
        val time = if (fallAt > 0) fallAt else System.currentTimeMillis()
        scope.launch {
            val loc = bestLocation()
            Repo.reportFall(uid, time, loc?.latitude, loc?.longitude)
        }
    }

    // ------------------------------------------------------------------ người thân xác nhận an toàn

    private fun watchOwnStatus() {
        val uid = Repo.currentUser?.uid ?: return
        scope.launch {
            Repo.caneStatus(uid).collect { s ->
                val st = s?.status ?: "SAFE"
                if (prevStatus == "FALLEN" && st == "SAFE") say("Người thân đã xác nhận bạn an toàn.")
                prevStatus = st
            }
        }
    }
}
