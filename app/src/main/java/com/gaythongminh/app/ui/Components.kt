package com.gaythongminh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope 
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaythongminh.app.data.LinkInfo
import com.gaythongminh.app.data.formatTime
import com.gaythongminh.app.util.Speaker
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Khung màn hình có thanh 3 sọc ở góc trên bên TRÁI (menu: Màn hình chính / Tài khoản đã liên kết / Đăng xuất). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    email: String,
    speak: Boolean,                    // true = vai trò người dùng gậy: đọc to các mục menu
    onHome: () -> Unit,
    onLinks: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun item(label: String, action: () -> Unit) {
        if (speak) Speaker.speak(label, true)
        scope.launch { drawer.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(24.dp)
                ) {
                    Text("Gậy Thông Minh", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(email, color = Color.White, fontSize = 14.sp)
                }
                NavigationDrawerItem(
                    label = { Text("Màn hình chính", fontSize = 18.sp) },
                    icon = { Icon(Icons.Default.Home, null) },
                    selected = false,
                    onClick = { item("Màn hình chính", onHome) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Tài khoản đã liên kết", fontSize = 18.sp) },
                    icon = { Icon(Icons.Default.Link, null) },
                    selected = false,
                    onClick = { item("Tài khoản đã liên kết", onLinks) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Đăng xuất", fontSize = 18.sp) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                    selected = false,
                    onClick = { item("Đăng xuất", onLogout) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Mở menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            content = content
        )
    }
}

/** Hộp thoại xác nhận hủy liên kết (luôn hỏi lại 1 lần). */
@Composable
fun UnlinkDialog(email: String, speak: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val msg = "Bạn có chắc muốn hủy liên kết với $email không?"
    LaunchedEffect(Unit) { if (speak) Speaker.speak(msg, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Hủy liên kết", fontWeight = FontWeight.Bold) },
        text = { Text(msg, fontSize = 17.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Hủy liên kết") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Quay lại") } }
    )
}

/** Một dòng trong danh sách liên kết: email + thời gian liên kết + nút Hủy liên kết. */
@Composable
fun LinkRow(email: String, linkedAt: Long, speak: Boolean, onUnlink: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(email, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Liên kết lúc: ${formatTime(linkedAt)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onUnlink,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.LinkOff, null)
                Text(" Hủy liên kết")
            }
        }
    }
}

@Composable
fun LinkListSection(
    links: List<LinkInfo>,
    emptyText: String,
    emailOf: (LinkInfo) -> String,
    speak: Boolean,
    onUnlink: (LinkInfo) -> Unit
) {
    if (links.isEmpty()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(emptyText, fontSize = 18.sp, modifier = Modifier.padding(20.dp))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            links.forEach { l -> LinkRow(emailOf(l), l.linkedAt, speak) { onUnlink(l) } }
        }
    }
}

/** Bản đồ OpenStreetMap (osmdroid) có ghim vị trí, tự di chuyển theo vị trí mới (realtime). */
@Composable
fun MapCard(lat: Double?, lon: Double?, label: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(21.0285, 105.8542))
        }
    }
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onDetach()
        }
    }

    LaunchedEffect(lat, lon, label) {
        if (lat != null && lon != null) {
            val p = GeoPoint(lat, lon)
            marker.position = p
            marker.title = label
            if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
            mapView.controller.animateTo(p)
            mapView.invalidate()
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    }
}

fun Modifier.clickableLink(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
