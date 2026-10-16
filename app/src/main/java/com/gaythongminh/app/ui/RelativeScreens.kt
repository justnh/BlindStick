package com.gaythongminh.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaythongminh.app.data.CaneStatus
import com.gaythongminh.app.data.FallEvent
import com.gaythongminh.app.data.LinkInfo
import com.gaythongminh.app.data.LinkResult
import com.gaythongminh.app.data.Repo
import com.gaythongminh.app.data.coordText
import com.gaythongminh.app.data.formatTime
import com.gaythongminh.app.data.mapsLink
import com.gaythongminh.app.service.RelativeService
import com.gaythongminh.app.util.Perms
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val STALE_MS = 30_000L   // quá 30 giây không có vị trí mới = mất kết nối

/** Màn hình chính của NGƯỜI THÂN: Theo dõi trạng thái + Theo dõi vị trí trên Google Map. */
@Composable
fun RelativeHomeScreen(onLinks: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val me = Repo.currentUser
    val uid = me?.uid ?: ""
    val scope = rememberCoroutineScope()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!Perms.hasNotification(ctx)) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        RelativeService.start(ctx)
    }

    val linksFlow = remember(uid) { Repo.linksForRelative(uid) }
    val links by linksFlow.collectAsStateWithLifecycle(initialValue = null as List<LinkInfo>?)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val cane = links?.let { l -> l.firstOrNull { it.caneUid == selected } ?: l.firstOrNull() }
    val caneUid = cane?.caneUid
    val statusFlow = remember<Flow<CaneStatus?>>(caneUid) {
        if (caneUid != null) Repo.caneStatus(caneUid) else flowOf(null)
    }
    val status by statusFlow.collectAsStateWithLifecycle(initialValue = null as CaneStatus?)
    val fallsFlow = remember<Flow<List<FallEvent>>>(caneUid) {
        if (caneUid != null) Repo.falls(caneUid) else flowOf(emptyList())
    }
    val falls by fallsFlow.collectAsStateWithLifecycle(initialValue = emptyList<FallEvent>())

    AppScaffold(
        title = "Gậy Thông Minh", email = me?.email ?: "", speak = false,
        onHome = {}, onLinks = onLinks, onLogout = onLogout
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val list = links
            if (list == null) {
                Text("Đang tải…", Modifier.padding(24.dp), fontSize = 18.sp)
            } else if (list.isEmpty()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Bạn chưa liên kết với người dùng gậy nào", fontSize = 19.sp, modifier = Modifier.padding(22.dp)) }
                    Button(onClick = onLinks, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Default.Add, null)
                        Text(" Thêm người liên kết", fontSize = 17.sp)
                    }
                }
            } else {
                if (list.size > 1) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        list.forEach { l ->
                            FilterChip(
                                selected = l.caneUid == cane?.caneUid,
                                onClick = { selected = l.caneUid },
                                label = { Text(l.caneEmail) }
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (tab == 0) {
                        StatusTab(cane?.caneEmail ?: "", status, falls) {
                            scope.launch { cane?.let { runCatching { Repo.confirmSafe(it.caneUid, uid) } } }
                        }
                    } else {
                        LocationTab(status)
                    }
                }
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Favorite, null) },
                        label = { Text("Trạng thái người thân") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Place, null) },
                        label = { Text("Vị trí Google Map") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTab(email: String, status: CaneStatus?, falls: List<FallEvent>, onConfirmSafe: () -> Unit) {
    val fallen = status?.isFallen == true
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Theo dõi trạng thái người thân", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (fallen) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (fallen) Icons.Default.Warning else Icons.Default.CheckCircle, null,
                    tint = if (fallen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    if (fallen) "BỊ NGÃ" else "AN TOÀN", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (fallen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                Text(email, fontSize = 15.sp)
                if (fallen && status != null) Text("Ngã lúc ${formatTime(status.lastFallAt)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Text("Nhật ký té ngã", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (falls.isEmpty()) {
            Text("Chưa có lần ngã nào được ghi nhận.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val uri = LocalUriHandler.current
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(falls, key = { it.id }) { f ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Ngã lúc ${formatTime(f.time)}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (f.lat != null && f.lon != null) {
                            Text(
                                "Xem vị trí: ${coordText(f.lat, f.lon)}",
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 2.dp).clickableLink { uri.openUri(mapsLink(f.lat, f.lon)) }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onConfirmSafe,
            enabled = fallen,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) { Text("Tôi xác nhận người thân an toàn", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun LocationTab(status: CaneStatus?) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(5000)
            value = System.currentTimeMillis()
        }
    }
    val uri = LocalUriHandler.current
    val hasPos = status?.lat != null && status.lon != null
    val stale = status != null && (now - status.locAt > STALE_MS)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Theo dõi vị trí trên Google Map", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hasPos && stale) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Mất kết nối với người dùng gậy. Đây là vị trí cuối cùng, lúc ${formatTime(status!!.locAt)}.",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp)
                )
            }
        } else if (hasPos && status?.bleConnected == false) {
            Text("Gậy chưa kết nối Bluetooth với điện thoại của người dùng.", fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary)
        }

        MapCard(
            lat = status?.lat, lon = status?.lon,
            label = if (stale) "Vị trí cuối cùng" else "Vị trí hiện tại",
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        if (hasPos) {
            val lat = status!!.lat!!
            val lon = status.lon!!
            Text(
                (if (stale) "Vị trí cuối: " else "Vị trí hiện tại: ") + coordText(lat, lon) +
                    "\nCập nhật lúc: ${formatTime(status.locAt)}",
                fontSize = 15.sp
            )
            Text(
                mapsLink(lat, lon),
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontSize = 14.sp,
                modifier = Modifier.clickableLink { uri.openUri(mapsLink(lat, lon)) }
            )
        } else {
            Text("Chưa có dữ liệu vị trí.", fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Menu "Tài khoản đã liên kết" của NGƯỜI THÂN. */
@Composable
fun RelativeLinksScreen(onHome: () -> Unit, onLinks: () -> Unit, onLogout: () -> Unit) {
    val me = Repo.currentUser
    val uid = me?.uid ?: ""
    val email = me?.email ?: ""
    val scope = rememberCoroutineScope()
    var toUnlink by remember { mutableStateOf<LinkInfo?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val flow = remember(uid) { Repo.linksForRelative(uid) }
    val links by flow.collectAsStateWithLifecycle(initialValue = emptyList<LinkInfo>())

    AppScaffold(
        title = "Tài khoản đã liên kết", email = email, speak = false,
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
                Column(Modifier.padding(18.dp)) {
                    Text("Tài khoản email nhận thông báo", fontSize = 14.sp)
                    Text(email, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Text("Người dùng gậy đã liên kết", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            LinkListSection(
                links = links,
                emptyText = "Bạn chưa liên kết với người dùng gậy nào",
                emailOf = { it.caneEmail },
                speak = false,
                onUnlink = { toUnlink = it }
            )

            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Add, null)
                Text(" Thêm người liên kết", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    toUnlink?.let { l ->
        UnlinkDialog(
            email = l.caneEmail, speak = false,
            onConfirm = {
                toUnlink = null
                scope.launch { runCatching { Repo.unlink(l.id) } }
            },
            onDismiss = { toUnlink = null }
        )
    }
    if (showAdd) {
        AddLinkDialog(
            onDismiss = { showAdd = false },
            onSubmit = { mail, code -> me?.let { Repo.linkCane(it, mail, code) } ?: LinkResult.Mismatch },
            onSuccess = { showAdd = false }
        )
    }
}

@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (String, String) -> LinkResult,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mail by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color.White,
        title = { Text("Thêm người liên kết", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = mail, onValueChange = { mail = it }, singleLine = true,
                    label = { Text("Email của người dùng gậy") }
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().take(6) }, singleLine = true,
                    label = { Text("Mã liên kết (6 ký tự)") }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        when (val r = onSubmit(mail, code)) {
                            LinkResult.Ok -> onSuccess()
                            LinkResult.Mismatch -> error = "Bạn nhập sai thông tin, hãy kiểm tra lại email và mã liên kết."
                            LinkResult.Self -> error = "Không thể tự liên kết với chính mình."
                            is LinkResult.Error -> error = "Lỗi: ${r.msg}"
                        }
                        busy = false
                    }
                }
            ) { Text("Liên kết") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Quay lại") } }
    )
}
