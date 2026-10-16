package com.gaythongminh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gaythongminh.app.data.Repo
import com.gaythongminh.app.data.Role
import com.gaythongminh.app.data.Session
import com.gaythongminh.app.service.CaneService
import com.gaythongminh.app.service.RelativeService
import com.gaythongminh.app.ui.CaneHomeScreen
import com.gaythongminh.app.ui.CaneLinksScreen
import com.gaythongminh.app.ui.GtmTheme
import com.gaythongminh.app.ui.LoginScreen
import com.gaythongminh.app.ui.RelativeHomeScreen
import com.gaythongminh.app.ui.RelativeLinksScreen
import com.gaythongminh.app.ui.RoleScreen
import com.gaythongminh.app.util.Speaker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GtmTheme {
                AppNav(this)
            }
        }
    }
}

@Composable
fun AppNav(activity: ComponentActivity) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = đang kiểm tra trạng thái đăng nhập
    var startDestination by remember {
        mutableStateOf<String?>(null)
    }

    /*
     * Kiểm tra khi mở app:
     *
     * 1. Chưa đăng nhập -> login
     * 2. Đã đăng nhập + đã chọn role -> vào thẳng home
     * 3. Đã đăng nhập nhưng chưa chọn role -> role
     */
    LaunchedEffect(Unit) {
        try {
            val user = Repo.currentUser
            if (user != null) {
                Session.setCurrentUser(user.uid)
                if (Session.role == null) {
                    val firestoreRole = Repo.getRole(user.uid)
                    if (firestoreRole != null) {
                        Session.role = firestoreRole
                    }
                }
            }

            startDestination = when {
                user == null -> "login"
                Session.role == Role.CANE -> "cane_home"
                Session.role == Role.RELATIVE -> "rel_home"
                else -> "role"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startDestination = "login" // Nếu xảy ra lỗi kết nối, cho về màn hình login
        }
    }

    // Chưa kiểm tra xong thì chưa tạo NavHost
    val destination = startDestination

    if (destination == null) {
        return
    }

    /*
     * Đăng xuất.
     *
     * Lưu ý:
     * Session.clear() sẽ xóa role trên máy.
     * Vì vậy đăng nhập lại sẽ hỏi role lại.
     *
     * Nếu muốn logout mà vẫn nhớ role thì KHÔNG dùng clear().
     */
    fun logout() {
        CaneService.stop(ctx)
        RelativeService.stop(ctx)
        Speaker.stop()

        // Chỉ đăng xuất Firebase, không xóa role local
        Repo.signOut()
        Session.setCurrentUser(null)

        scope.launch {
            runCatching {
                CredentialManager
                    .create(ctx)
                    .clearCredentialState(
                        ClearCredentialStateRequest()
                    )
            }
        }

        nav.navigate("login") {
            popUpTo(0) {
                inclusive = true
            }
        }
    }

    /*
     * Người dùng chọn vai trò lần đầu.
     */
    fun pickRole(role: Role) {
        val user = Repo.currentUser

        if (user == null) {
            nav.navigate("login") {
                popUpTo(0) {
                    inclusive = true
                }
            }
            return
        }

        val uid = user.uid

        // Lưu role ngay trên máy
        Session.setCurrentUser(uid)
        Session.role = role

        scope.launch {
            try {
                // Lưu role lên Firestore
                Repo.setRole(uid, role)

                /*
                 * CHỈ người dùng gậy mới tạo mã 6 ký tự.
                 */
                if (role == Role.CANE) {
                    Repo.ensureLinkCode(user)

                    RelativeService.stop(ctx)

                    nav.navigate("cane_home") {
                        popUpTo("role") {
                            inclusive = true
                        }
                    }
                } else {
                    /*
                     * Người thân không tạo mã.
                     */
                    CaneService.stop(ctx)

                    nav.navigate("rel_home") {
                        popUpTo("role") {
                            inclusive = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = destination
    ) {

        /*
         * LOGIN
         */
        composable("login") {
            LoginScreen(activity) {

                val user = Repo.currentUser
                    ?: return@LoginScreen

                Session.setCurrentUser(user.uid)

                scope.launch {
                    val role = Session.role ?: Repo.getRole(user.uid)?.also { Session.role = it }

                    when (role) {
                        Role.CANE -> {
                            nav.navigate("cane_home") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }

                        Role.RELATIVE -> {
                            nav.navigate("rel_home") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }

                        null -> {
                            nav.navigate("role") {
                                popUpTo("login") {
                                    inclusive = true
                                }
                            }
                        }
                    }
                }
            }
        }

        /*
         * CHỌN VAI TRÒ
         */
        composable("role") {
            RoleScreen {
                pickRole(it)
            }
        }

        /*
         * NGƯỜI DÙNG GẬY
         */
        composable("cane_home") {
            CaneHomeScreen(
                onLinks = {
                    nav.navigate("cane_links") {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    logout()
                }
            )
        }

        /*
         * DANH SÁCH NGƯỜI THÂN
         */
        composable("cane_links") {
            CaneLinksScreen(
                onHome = {
                    nav.popBackStack(
                        "cane_home",
                        false
                    )
                },
                onLinks = {},
                onLogout = {
                    logout()
                }
            )
        }

        /*
         * NGƯỜI THÂN
         */
        composable("rel_home") {
            RelativeHomeScreen(
                onLinks = {
                    nav.navigate("rel_links") {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    logout()
                }
            )
        }

        /*
         * DANH SÁCH NGƯỜI DÙNG GẬY ĐÃ LIÊN KẾT
         */
        composable("rel_links") {
            RelativeLinksScreen(
                onHome = {
                    nav.popBackStack(
                        "rel_home",
                        false
                    )
                },
                onLinks = {},
                onLogout = {
                    logout()
                }
            )
        }
    }
}