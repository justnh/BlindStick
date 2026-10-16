package com.gaythongminh.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.gaythongminh.app.data.Repo
import com.gaythongminh.app.data.Role
import com.gaythongminh.app.data.Session
import com.gaythongminh.app.util.GoogleAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Bước 1: Đăng nhập bằng Google. */
@Composable
fun LoginScreen(activity: ComponentActivity, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun go() {
        busy = true
        error = null
        scope.launch {
            var success = false
            try {
                val token = GoogleAuth.idToken(activity)           // Google xác thực (chọn/nhập tài khoản + mật khẩu)
                val user = Repo.signInWithGoogleIdToken(token)     // Firebase xác minh token phía backend
                Session.setCurrentUser(user.uid)                   // Ghi nhận session trên máy
                Repo.createUser(user)                              // Đảm bảo hồ sơ người dùng trên Firestore
                success = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                error = "Bạn đã hủy đăng nhập."
            } catch (e: Exception) {
                error = "Không đăng nhập được: ${e.localizedMessage ?: "lỗi không xác định"}"
            } finally {
                busy = false
            }

            if (success) {
                onDone()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFFF3FAFF), Color(0xFFFFF1D6))))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(100.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                Icons.Default.Accessibility, null, tint = Color.White,
                modifier = Modifier.padding(28.dp).size(64.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Gậy Thông Minh", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Đồng hành an toàn cùng người khiếm thị", fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { go() },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Đăng nhập bằng Google", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        }
        error?.let {
            Spacer(Modifier.height(18.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, fontSize = 16.sp)
        }
    }
}

/** Bước 2: chọn vai trò. */
@Composable
fun RoleScreen(onPick: (Role) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF3FAFF), Color(0xFFFFF1D6))))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bạn là ai?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(28.dp))
        RoleCard("Bạn là người dùng gậy", Icons.Default.Accessibility, MaterialTheme.colorScheme.primary) { onPick(Role.CANE) }
        Spacer(Modifier.height(18.dp))
        RoleCard("Bạn là người thân", Icons.Default.Favorite, MaterialTheme.colorScheme.tertiary) { onPick(Role.RELATIVE) }
    }
}

@Composable
private fun RoleCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
