package com.gaythongminh.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gaythongminh.app.data.CaneStatus
import com.gaythongminh.app.data.LinkInfo
import com.gaythongminh.app.data.Repo
import com.gaythongminh.app.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Chạy nền cho NGƯỜI THÂN: lắng nghe Firestore (realtime) của mọi người dùng gậy đã liên kết,
 * hễ trạng thái chuyển sang "BỊ NGÃ" thì bắn thông báo có chuông + rung ngay cả khi đã tắt màn hình.
 */
class RelativeService : Service() {

    companion object {
        const val NOTIF_ID = 102
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RelativeService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, RelativeService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.service(this, "Đang theo dõi người thân…")
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        val me = Repo.currentUser
        if (me == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                Repo.linksForRelative(me.uid)
                    .flatMapLatest { links -> statusesOf(links) }
                    .collect { pairs -> pairs.forEach { (l, s) -> handleStatus(l, s) } }
            }
        }
        return START_STICKY
    }

    private fun statusesOf(links: List<LinkInfo>): Flow<List<Pair<LinkInfo, CaneStatus?>>> {
        if (links.isEmpty()) return flowOf(emptyList())
        val flows: List<Flow<Pair<LinkInfo, CaneStatus?>>> =
            links.map { l -> Repo.caneStatus(l.caneUid).map { s -> l to s } }
        return combine(flows) { arr -> arr.toList() }
    }

    private fun handleStatus(link: LinkInfo, st: CaneStatus?) {
        if (st == null || !st.isFallen) return
        val prefs = getSharedPreferences("alerts", MODE_PRIVATE)
        val key = "alerted_${link.caneUid}"
        if (st.lastFallAt <= prefs.getLong(key, 0L)) return   // đã báo lần ngã này rồi
        prefs.edit().putLong(key, st.lastFallAt).apply()
        Notifier.showFall(
            this, 1000 + (link.caneUid.hashCode() and 0xFFFF),
            link.caneEmail, st.lastFallAt, st.fallLat ?: st.lat, st.fallLon ?: st.lon
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
