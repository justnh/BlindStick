package com.gaythongminh.app.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaythongminh.app.ble.BleState
import com.gaythongminh.app.data.LinkInfo
import com.gaythongminh.app.data.Repo
import com.gaythongminh.app.data.coordText
import com.gaythongminh.app.service.CaneRuntime
import com.gaythongminh.app.service.CaneService
import com.gaythongminh.app.util.Perms
import com.gaythongminh.app.util.Speaker
import kotlinx.coroutines.launch

/** Màn hình chính của NGƯỜI DÙNG GẬY: Trạng thái Bluetooth + Định vị Google Map. */
@Composable
fun CaneHomeScreen(onLinks: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val email = Repo.currentUser?.email ?: ""
    val ble by CaneRuntime.bleState.collectAsStateWithLifecycle()
    val loc by CaneRuntime.location.collectAsStateWithLifecycle()
    val warning by CaneRuntime.fallWarning.collectAsStateWithLifecycle()
    val lastEvent by CaneRuntime.lastEvent.collectAsStateWithLifecycle()

    val perms = remember { Perms.caneRequired() }
    var locOk by remember { mutableStateOf(Perms.hasLocation(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locOk = Perms.hasLocation(ctx)
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LaunchedEffect(Unit) {
        Speaker.speak("Màn hình chính của người dùng gậy.", true)
        permLauncher.launch(perms)
    }
    LaunchedEffect(locOk) { if (locOk) CaneService.start(ctx) }

    AppScaffold(
        title = "Gậy Thông Minh", email = email, speak = true,
        onHome = {}, onLinks = onLinks, onLogout = onLogout
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Phần 1: Trạng thái Bluetooth (cập nhật liên tục)
            val (text, icon, container) = when (ble) {
                BleState.CONNECTED -> Triple("Đã kết nối với gậy", Icons.Default.BluetoothConnected, MaterialTheme.colorScheme.tertiaryContainer)
                BleState.CONNECTING -> Triple("Đang kết nối với gậy…", Icons.Default.BluetoothSearching, MaterialTheme.colorScheme.secondaryContainer)
                BleState.SCANNING -> Triple("Đang tìm gậy…", Icons.Default.BluetoothSearching, MaterialTheme.colorScheme.secondaryContainer)
                BleState.OFF -> Triple("Bluetooth đang tắt hoặc chưa được cấp quyền", Icons.Default.BluetoothDisabled, MaterialTheme.colorScheme.errorContainer)
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = container),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Trạng thái Bluetooth", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(36.dp))
                        Text(" $text", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (ble == BleState.OFF) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                                Icon(Icons.Default.Bluetooth, null)
                                Text(" Bật Bluetooth")
                            }
                            Button(onClick = { permLauncher.launch(perms) }) { Text("Cấp quyền") }
                        }
                    }
                }
            }

            if (warning) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(34.dp))
                        Text(
                            "  Phát hiện có thể bị ngã! Nếu an toàn, hãy nhấn nút trên gậy.",
                            color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (lastEvent.isNotEmpty()) {
                Text(lastEvent, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- Phần 2: Định vị Google Map (realtime)
            Text("Định vị Google Map", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MapCard(
                lat = loc?.latitude, lon = loc?.longitude, label = "Vị trí của bạn",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Text(
                loc?.let { "Vị trí hiện tại: ${coordText(it.latitude, it.longitude)}" }
                    ?: if (locOk) "Đang xác định vị trí…" else "Chưa được cấp quyền vị trí",
                fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

/** Menu "Tài khoản đã liên kết" của NGƯỜI DÙNG GẬY. Mọi thông báo đều được đọc to. */
@Composable
fun CaneLinksScreen(onHome: () -> Unit, onLinks: () -> Unit, onLogout: () -> Unit) {
    val user = Repo.currentUser
    val uid = user?.uid ?: ""
    val email = user?.email ?: ""
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var toUnlink by remember { mutableStateOf<LinkInfo?>(null) }
    val flow = remember(uid) { Repo.linksForCane(uid) }
    val links by flow.collectAsStateWithLifecycle(initialValue = emptyList<LinkInfo>())

    LaunchedEffect(uid) {
        code = runCatching { user?.let { Repo.ensureLinkCode(it) } }.getOrNull()
    }
    LaunchedEffect(code, links.size) {
        val c = code ?: return@LaunchedEffect
        val spelled = c.toCharArray().joinToString(" ")
        val tail = if (links.isEmpty()) "Bạn chưa liên kết với người thân nào."
        else "Có ${links.size} người thân đã liên kết."
        Speaker.speak("Mã liên kết của bạn là $spelled. $tail", true)
    }

    AppScaffold(
        title = "Tài khoản đã liên kết", email = email, speak = true,
        onHome = onHome, onLinks = onLinks, onLogout = onLogout
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mã liên kết của bạn là:", fontSize = 18.sp)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        code ?: "…", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp, color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Đưa mã này và email của bạn cho người thân để họ thêm liên kết.",
                        fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                }
            }

            Text("Người thân được xem vị trí của bạn", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            LinkListSection(
                links = links,
                emptyText = "Bạn chưa liên kết với người thân nào",
                emailOf = { it.relativeEmail },
                speak = true,
                onUnlink = { toUnlink = it }
            )
        }
    }

    toUnlink?.let { l ->
        UnlinkDialog(
            email = l.relativeEmail, speak = true,
            onConfirm = {
                toUnlink = null
                scope.launch {
                    val ok = runCatching { Repo.unlink(l.id) }.isSuccess
                    Speaker.speak(if (ok) "Đã hủy liên kết." else "Hủy liên kết thất bại, hãy thử lại.", true)
                }
            },
            onDismiss = { toUnlink = null }
        )
    }
}
