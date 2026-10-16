package com.gaythongminh.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lưu vai trò theo từng Firebase UID trên máy.
 *
 * Firestore là dữ liệu chính.
 * SharedPreferences chỉ dùng làm cache để mở app nhanh
 * và vẫn nhớ vai trò khi mở lại ứng dụng.
 */
object Session {

    private lateinit var prefs: SharedPreferences

    private var currentUid: String? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(
            "session",
            Context.MODE_PRIVATE
        )
    }

    fun setCurrentUser(uid: String?) {
        currentUid = uid
    }

    fun getRole(uid: String): Role? {
        return prefs
            .getString("role_$uid", null)
            ?.let {
                runCatching {
                    Role.valueOf(it)
                }.getOrNull()
            }
    }

    fun setRole(uid: String, role: Role) {
        prefs.edit()
            .putString("role_$uid", role.name)
            .apply()
    }

    fun clearRole(uid: String) {
        prefs.edit()
            .remove("role_$uid")
            .apply()
    }

    /**
     * Role của tài khoản hiện tại.
     *
     * Giữ API này để các file khác trong project
     * vẫn có thể dùng Session.role.
     */
    var role: Role?
        get() {
            val uid = currentUid ?: Repo.currentUser?.uid ?: return null
            return getRole(uid)
        }
        set(value) {
            val uid = currentUid ?: Repo.currentUser?.uid ?: return

            if (value == null) {
                clearRole(uid)
            } else {
                setRole(uid, value)
            }
        }

    /**
     * Xóa toàn bộ cache session trên máy.
     */
    fun clear() {
        prefs.edit()
            .clear()
            .apply()

        currentUid = null
    }
}