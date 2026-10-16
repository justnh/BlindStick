package com.gaythongminh.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

sealed class LinkResult {
    object Ok : LinkResult()
    object Mismatch : LinkResult()
    object Self : LinkResult()
    data class Error(val msg: String) : LinkResult()
}

/**
 * Toàn bộ thao tác cơ sở dữ liệu (Firebase Auth + Cloud Firestore).
 *
 *  users/{uid}                 : email, name, role, linkCode
 *  codes/{code}                : uid, email           (tra mã liên kết)
 *  links/{caneUid_relativeUid} : liên kết người dùng gậy <-> người thân
 *  cane_status/{caneUid}       : trạng thái + vị trí realtime
 *  cane_status/{caneUid}/falls : nhật ký các lần ngã
 */
object Repo {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    // ---------------------------------------------------------------- tài khoản

    suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(cred).await().user ?: error("Không lấy được tài khoản")
    }

    fun signOut() = auth.signOut()

    suspend fun userExists(uid: String): Boolean {
        return try {
            db.collection("users").document(uid).get().await().exists()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createUser(u: FirebaseUser) {
        try {
            val ref = db.collection("users").document(u.uid)
            ref.set(
                mapOf(
                    "email" to (u.email ?: "").lowercase(),
                    "name" to (u.displayName ?: ""),
                    "createdAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- vai trò người dùng (Role)

    /**
     * Lưu vai trò (role) của tài khoản lên Firestore.
     */
    suspend fun setRole(uid: String, role: Role) {
        try {
            db.collection("users")
                .document(uid)
                .set(
                    mapOf("role" to role.name),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Đọc vai trò (role) của tài khoản từ Firestore.
     */
    suspend fun getRole(uid: String): Role? {
        return try {
            val value = db.collection("users")
                .document(uid)
                .get()
                .await()
                .getString("role")

            value?.let {
                runCatching {
                    Role.valueOf(it)
                }.getOrNull()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ---------------------------------------------------------------- mã liên kết

    private fun randomCode(): String {
        val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val digits = "23456789"
        val all = letters + digits
        val rnd = SecureRandom()
        while (true) {
            val s = (1..6).map { all[rnd.nextInt(all.length)] }.joinToString("")
            if (s.any { it in letters } && s.any { it in digits }) return s   // gồm cả chữ và số
        }
    }

    /** Mã 6 ký tự (chữ + số) của người dùng gậy; tự sinh lần đầu rồi giữ nguyên. */
    suspend fun ensureLinkCode(u: FirebaseUser): String {
        val userRef = db.collection("users").document(u.uid)
        val existing = try {
            userRef.get().await().getString("linkCode")
        } catch (_: Exception) { null }

        if (!existing.isNullOrBlank()) return existing
        val email = (u.email ?: "").lowercase()
        repeat(10) {
            val code = randomCode()
            try {
                db.collection("codes").document(code)
                    .set(mapOf("uid" to u.uid, "email" to email)).await()   // trùng mã -> bị rules từ chối -> thử mã khác
                userRef.set(mapOf("linkCode" to code), SetOptions.merge()).await()
                return code
            } catch (_: Exception) { /* thử lại */ }
        }
        error("Không tạo được mã liên kết")
    }

    /** Người thân nhập email + mã của người dùng gậy. Rules ở phía server đối chiếu lại lần nữa. */
    suspend fun linkCane(rel: FirebaseUser, caneEmail: String, codeInput: String): LinkResult {
        val email = caneEmail.trim().lowercase()
        val code = codeInput.trim().uppercase()
        if (email.isEmpty() || code.length != 6) return LinkResult.Mismatch
        return try {
            val snap = db.collection("codes").document(code).get().await()
            if (!snap.exists() || snap.getString("email") != email) return LinkResult.Mismatch
            val caneUid = snap.getString("uid") ?: return LinkResult.Mismatch
            if (caneUid == rel.uid) return LinkResult.Self
            db.collection("links").document("${caneUid}_${rel.uid}").set(
                mapOf(
                    "caneUid" to caneUid,
                    "caneEmail" to email,
                    "relativeUid" to rel.uid,
                    "relativeEmail" to (rel.email ?: "").lowercase(),
                    "code" to code,
                    "linkedAt" to System.currentTimeMillis()
                )
            ).await()
            LinkResult.Ok
        } catch (e: Exception) {
            LinkResult.Error(e.localizedMessage ?: "Lỗi kết nối")
        }
    }

    suspend fun unlink(linkId: String) {
        try {
            db.collection("links").document(linkId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- realtime

    private fun DocumentSnapshot.toLink() = LinkInfo(
        id = id,
        caneUid = getString("caneUid") ?: "",
        caneEmail = getString("caneEmail") ?: "",
        relativeUid = getString("relativeUid") ?: "",
        relativeEmail = getString("relativeEmail") ?: "",
        linkedAt = getLong("linkedAt") ?: 0L
    )

    private fun linksFlow(field: String, uid: String): Flow<List<LinkInfo>> = callbackFlow {
        val reg = db.collection("links").whereEqualTo(field, uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap.documents.map { it.toLink() }.sortedBy { it.linkedAt })
            }
        awaitClose { reg.remove() }
    }

    fun linksForCane(caneUid: String) = linksFlow("caneUid", caneUid)
    fun linksForRelative(relUid: String) = linksFlow("relativeUid", relUid)

    fun caneStatus(caneUid: String): Flow<CaneStatus?> = callbackFlow {
        val reg = db.collection("cane_status").document(caneUid)
            .addSnapshotListener { d, err ->
                if (err != null || d == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (!d.exists()) {
                    trySend(CaneStatus())
                    return@addSnapshotListener
                }
                trySend(
                    CaneStatus(
                        status = d.getString("status") ?: "SAFE",
                        lat = d.getDouble("lat"),
                        lon = d.getDouble("lon"),
                        locAt = d.getLong("locAt") ?: 0L,
                        bleConnected = d.getBoolean("bleConnected") ?: false,
                        lastFallAt = d.getLong("lastFallAt") ?: 0L,
                        fallLat = d.getDouble("fallLat"),
                        fallLon = d.getDouble("fallLon")
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    fun falls(caneUid: String): Flow<List<FallEvent>> = callbackFlow {
        val reg = db.collection("cane_status").document(caneUid).collection("falls")
            .orderBy("time", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap.documents.map {
                    FallEvent(it.id, it.getLong("time") ?: 0L, it.getDouble("lat"), it.getDouble("lon"))
                })
            }
        awaitClose { reg.remove() }
    }

    // ---------------------------------------------------------------- ghi dữ liệu (người dùng gậy)

    /** Ghi vị trí realtime. Không chờ server: mất mạng thì Firestore tự xếp hàng và đẩy lên khi có mạng lại. */
    fun updateLocation(uid: String, lat: Double?, lon: Double?, bleConnected: Boolean) {
        val data = mutableMapOf<String, Any>("bleConnected" to bleConnected)
        if (lat != null && lon != null) {
            data["lat"] = lat
            data["lon"] = lon
            data["locAt"] = System.currentTimeMillis()
        }
        db.collection("cane_status").document(uid).set(data, SetOptions.merge())
    }

    /** Ngã (đã hết thời gian chờ hủy): chuyển sang BỊ NGÃ + ghi nhật ký, cùng một lượt ghi. */
    fun reportFall(uid: String, time: Long, lat: Double?, lon: Double?) {
        val statusRef = db.collection("cane_status").document(uid)
        val fallRef = statusRef.collection("falls").document()
        val status = mutableMapOf<String, Any>("status" to "FALLEN", "lastFallAt" to time)
        val fall = mutableMapOf<String, Any>("time" to time)
        if (lat != null && lon != null) {
            status["fallLat"] = lat; status["fallLon"] = lon
            fall["lat"] = lat; fall["lon"] = lon
        }
        db.batch()
            .set(statusRef, status, SetOptions.merge())
            .set(fallRef, fall)
            .commit()
    }

    // ---------------------------------------------------------------- người thân

    suspend fun confirmSafe(caneUid: String, relUid: String) {
        try {
            db.collection("cane_status").document(caneUid).update(
                mapOf("status" to "SAFE", "ackAt" to System.currentTimeMillis(), "ackBy" to relUid)
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}