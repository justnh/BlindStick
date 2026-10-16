package com.gaythongminh.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.Locale
import java.util.UUID

enum class BleState { OFF, SCANNING, CONNECTING, CONNECTED }

/**
 * Kết nối BLE tới ESP32 "GayThongMinh" (Nordic UART):
 *   RX (app ghi lệnh vào gậy) : 6e400002-...   ví dụ  GPS,21.028511,105.804817
 *   TX (gậy báo cho app)      : 6e400003-...   ví dụ  FALL / CANCEL / SENDING_ALERT / ALERT_SENT ...
 * Tự quét, tự nối lại khi mất kết nối.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class CaneBle(
    private val ctx: Context,
    private val onMessage: (String) -> Unit,
    private val onState: (BleState) -> Unit
) {
    companion object {
        val SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEVICE_NAME = "GayThongMinh"
    }

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var started = false
    private var scanning = false
    private var state = BleState.OFF
    private var payload = 20
    private var attempt = 0
    private val writeQueue = ArrayDeque<String>()
    private var writing = false

    val isConnected: Boolean get() = state == BleState.CONNECTED

    private fun setState(s: BleState) {
        main.post {
            if (state != s) {
                state = s
                onState(s)
            }
        }
    }

    private fun adapter(): BluetoothAdapter? =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter

    private fun hasPerm(): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ điều khiển

    fun start() {
        started = true
        main.post { scan() }
    }

    fun stop() {
        started = false
        main.removeCallbacksAndMessages(null)
        stopScan()
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        rx = null
        writeQueue.clear()
        writing = false
        state = BleState.OFF
    }

    /** Gửi vị trí điện thoại cho ESP32 (gậy dùng để ghi vào email cảnh báo). */
    fun sendGps(lat: Double, lon: Double) {
        val d = if (payload >= 40) 6 else 4   // MTU nhỏ thì rút gọn số thập phân cho vừa gói
        send(String.format(Locale.US, "GPS,%.${d}f,%.${d}f", lat, lon))
    }

    fun send(cmd: String) {
        main.post {
            if (!isConnected) return@post
            writeQueue.addLast(cmd)
            pump()
        }
    }

    // ------------------------------------------------------------------ quét + nối

    private fun scan() {
        if (!started || scanning || gatt != null) return
        val ad = adapter()
        if (ad == null || !ad.isEnabled || !hasPerm()) {
            setState(BleState.OFF)
            main.postDelayed({ scan() }, 3000)
            return
        }
        val scanner = ad.bluetoothLeScanner
        if (scanner == null) {
            main.postDelayed({ scan() }, 3000)
            return
        }
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build(),
            ScanFilter.Builder().setDeviceName(DEVICE_NAME).build()
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            scanner.startScan(filters, settings, scanCb)
            scanning = true
            setState(BleState.SCANNING)
        } catch (e: Exception) {
            main.postDelayed({ scan() }, 3000)
        }
    }

    private fun stopScan() {
        if (scanning) {
            runCatching { adapter()?.bluetoothLeScanner?.stopScan(scanCb) }
            scanning = false
        }
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            stopScan()
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            main.postDelayed({ scan() }, 3000)
        }
    }

    private fun connect(dev: BluetoothDevice) {
        setState(BleState.CONNECTING)
        payload = 20
        val my = ++attempt
        gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        main.postDelayed({ if (my == attempt && state == BleState.CONNECTING) dropAndRescan() }, 15000)
    }

    private fun dropAndRescan() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        rx = null
        writeQueue.clear()
        writing = false
        setState(BleState.SCANNING)
        main.postDelayed({ scan() }, 1500)
    }

    // ------------------------------------------------------------------ GATT

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                main.post { if (!g.requestMtu(185)) g.discoverServices() }
            } else {
                main.post {
                    if (gatt === g) dropAndRescan() else runCatching { g.close() }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            payload = if (status == BluetoothGatt.GATT_SUCCESS) mtu - 3 else 20
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SERVICE)
            val tx = svc?.getCharacteristic(TX)
            val r = svc?.getCharacteristic(RX)
            if (status != BluetoothGatt.GATT_SUCCESS || tx == null || r == null) {
                main.post { dropAndRescan() }
                return
            }
            rx = r
            g.setCharacteristicNotification(tx, true)
            val d = tx.getDescriptor(CCCD)
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) setState(BleState.CONNECTED)
            else main.post { dropAndRescan() }
        }

        // Android 12 trở xuống
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            deliver(c.value)
        }

        // Android 13 trở lên
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            deliver(value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                writing = false
                pump()
            }
        }
    }

    private fun deliver(bytes: ByteArray?) {
        if (bytes == null) return
        val s = String(bytes, Charsets.UTF_8).trim()
        if (s.isNotEmpty()) main.post { onMessage(s) }
    }

    private fun pump() {
        if (writing) return
        val g = gatt ?: return
        val r = rx ?: return
        val cmd = writeQueue.removeFirstOrNull() ?: return
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        writing = true
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(r, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            r.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            r.value = bytes
            g.writeCharacteristic(r)
        }
        if (!ok) writing = false
    }
}
