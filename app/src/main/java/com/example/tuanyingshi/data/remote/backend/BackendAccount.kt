package com.example.tuanyingshi.data.remote.backend

import android.content.Context
import android.net.Uri
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 后端账号会话管理器。
 *
 * - 登录 / 注册成功后把令牌与用户信息写入 [BackendPrefs]（令牌由 [BackendClient] 的 JWT 拦截器自动附带）；
 * - 通过 [userState] 向 Compose 暴露当前登录用户，登录态变化即自动通知 UI；
 * - 所有网络调用均经 [BackendClient.api]，且要求在后端模式下已配置后端地址。
 */
object BackendAccount {

    data class User(
        val id: Long,
        val username: String,
        val email: String,
        val nickname: String?,
        val avatar: String?,
        val signature: String?,
    )

    private val _user = MutableStateFlow(readUser())
    val userState: StateFlow<User?> = _user.asStateFlow()

    val isLoggedIn: Boolean get() = BackendPrefs.isLoggedIn

    private fun readUser(): User? {
        if (BackendPrefs.token.isBlank()) return null
        val id = BackendPrefs.userId
        if (id == 0L) return null
        return User(
            id = id,
            username = BackendPrefs.username,
            email = BackendPrefs.email,
            nickname = BackendPrefs.nickname.ifBlank { null },
            avatar = BackendPrefs.avatar.ifBlank { null },
            signature = BackendPrefs.signature.ifBlank { null },
        )
    }

    private fun emit() {
        _user.value = readUser()
    }

    /** 账号（用户名或邮箱）+ 密码登录。登录成功后顺带上报设备信息。 */
    suspend fun login(account: String, password: String): Result<Unit> = runCatching {
        val resp = BackendClient.api.login(
            LoginRequestDTO(
                account = account,
                password = password,
                // 带上设备信息：后端登录时若 deviceId 非空会直接写入 t_user_device
                deviceId = DeviceInfo.deviceId(),
                deviceName = DeviceInfo.deviceName(),
                appVersion = DeviceInfo.appVersion(),
            ),
        )
        if (!resp.ok || resp.data == null) error(resp.message ?: "登录失败")
        applySession(resp.data!!)
        // 注册接口不带设备字段，这里统一补一次，保证设备表一定有新记录
        runCatching { reportDevice(force = true) }
    }

    /** 邮箱 + 验证码注册。 */
    suspend fun register(
        email: String,
        code: String,
        username: String,
        password: String,
        nickname: String?,
    ): Result<Unit> = runCatching {
        val resp = BackendClient.api.register(
            RegisterRequestDTO(username = username, email = email, password = password, code = code, nickname = nickname),
        )
        if (!resp.ok || resp.data == null) error(resp.message ?: "注册失败")
        applySession(resp.data!!)
        // 注册接口没有设备字段，注册成功后单独上报
        runCatching { reportDevice(force = true) }
    }

    /** 向指定邮箱发送注册验证码。 */
    suspend fun sendCode(email: String): Result<Unit> = runCatching {
        val resp = BackendClient.api.sendCode(SendCodeRequestDTO(email))
        if (!resp.ok) error(resp.message ?: "验证码发送失败")
    }

    // ───────────────────────── 扫码登录 ─────────────────────────

    /** 出码端：创建二维码票据（未登录）。 */
    suspend fun qrCreate(): Result<QrCreateVO> = runCatching {
        val resp = BackendClient.api.qrCreate()
        if (!resp.ok || resp.data == null) error(resp.message ?: "获取二维码失败")
        resp.data!!
    }

    /**
     * 出码端：轮询状态，返回最新状态（CONFIRMED 时携带令牌，但不在此写入会话）。
     * 由调用方拿到 CONFIRMED 后调用 [applyQrLogin]。
     */
    suspend fun qrPoll(ticket: String): Result<QrStatusVO> = runCatching {
        val resp = BackendClient.api.qrStatus(ticket)
        if (!resp.ok || resp.data == null) error(resp.message ?: "查询失败")
        resp.data!!
    }

    /** 出码端：把扫码登录拿到的令牌写入本地会话（顺带上报设备）。 */
    suspend fun applyQrLogin(vo: LoginResultVO) {
        applySession(vo)
        runCatching { reportDevice(force = true) }
    }

    /** 扫码端：已登录设备扫码，标记 SCANNED。 */
    suspend fun qrScan(ticket: String): Result<Unit> = runCatching {
        val resp = BackendClient.api.qrScan(QrTicketDTO(ticket))
        if (!resp.ok) error(resp.message ?: "扫码失败")
    }

    /** 扫码端：已登录设备确认登录，为当前用户签发令牌。 */
    suspend fun qrConfirm(ticket: String): Result<Unit> = runCatching {
        val resp = BackendClient.api.qrConfirm(QrTicketDTO(ticket))
        if (!resp.ok) error(resp.message ?: "确认失败")
    }

    /** 扫码端：已登录设备取消本次扫码登录。 */
    suspend fun qrCancel(ticket: String): Result<Unit> = runCatching {
        val resp = BackendClient.api.qrCancel(QrTicketDTO(ticket))
        if (!resp.ok) error(resp.message ?: "取消失败")
    }

    /** 退出登录（忽略服务端错误，本地会话始终清除）。 */
    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { BackendClient.api.logout() }
        BackendPrefs.clearSession()
        emit()
    }

    /**
     * 上报当前设备信息（后台「用户分析 - 登录设备」的数据来源）。
     *
     * 设备信息属于辅助数据，失败一律静默忽略，不影响登录 / 启动流程。
     * 仅在「后端模式 + 已登录」时有意义——没有后端地址或没登录，上报无从归属。
     *
     * @param force 忽略 12 小时节流；登录 / 注册成功后调用，确保设备表立即可见
     */
    suspend fun reportDevice(force: Boolean = false): Result<Unit> = runCatching {
        if (!BackendPrefs.isBackendMode() || !BackendPrefs.isLoggedIn) return@runCatching
        if (!force && !DeviceInfo.isReportDue()) return@runCatching
        val resp = BackendClient.api.saveDevice(
            DeviceRequestDTO(
                deviceId = DeviceInfo.deviceId(),
                deviceName = DeviceInfo.deviceName(),
                platform = DeviceInfo.platform(),
                appVersion = DeviceInfo.appVersion(),
                clientTime = System.currentTimeMillis(),
            ),
        )
        if (resp.ok) DeviceInfo.markReported()
    }

    /** 拉取最新个人资料并刷新本地会话。 */
    suspend fun refreshProfile(): Result<Unit> = runCatching {
        val resp = BackendClient.api.profile()
        if (resp.ok && resp.data != null) {
            applyUserVO(resp.data!!)
        }
    }

    /** 修改个人资料（昵称 / 签名；为 null 的字段保持不变）。 */
    suspend fun updateProfile(nickname: String? = null, signature: String? = null): Result<Unit> = runCatching {
        val resp = BackendClient.api.updateProfile(
            UpdateProfileRequestDTO(nickname = nickname, signature = signature),
        )
        if (!resp.ok || resp.data == null) error(resp.message ?: "资料保存失败")
        applyUserVO(resp.data!!)
    }

    /**
     * 上传头像并写回本地会话，使头像即时生效。
     * @param uri 图片 content Uri（来自系统相册选择器）
     * @return 后端保存的头像地址
     */
    suspend fun uploadAvatar(context: Context, uri: Uri): Result<String> = runCatching {
        val resp = BackendClient.api.uploadAvatar(buildImagePart(context, uri))
        if (!resp.ok || resp.data == null) error(resp.message ?: "头像上传失败")
        val url = resp.data!!["url"] as? String
            ?: resp.data!!["absoluteUrl"] as? String
            ?: error("头像地址解析失败")
        // 上传接口只负责存文件并返回地址，必须再 PUT /user/profile 才会写进账号资料，
        // 否则头像只在本地生效，重新登录 / 换设备后会被后端资料里的旧值覆盖。
        val saved = BackendClient.api.updateProfile(UpdateProfileRequestDTO(avatar = url))
        if (!saved.ok) error(saved.message ?: "头像保存失败")
        applyAvatar(url)
        url
    }

    /** 修改密码（需原密码），成功后建议引导用户重新登录。 */
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> = runCatching {
        val resp = BackendClient.api.changePassword(ChangePasswordRequestDTO(oldPassword, newPassword))
        if (!resp.ok) error(resp.message ?: "密码修改失败")
    }

    /** 用后端返回的完整用户信息覆盖本地会话。 */
    private fun applyUserVO(vo: UserVO) {
        BackendPrefs.setSession(
            accessToken = BackendPrefs.token,
            userId = vo.id ?: BackendPrefs.userId,
            username = vo.username ?: BackendPrefs.username,
            email = vo.email ?: BackendPrefs.email,
            nickname = vo.nickname,
            avatar = vo.avatar,
            signature = vo.signature,
        )
        emit()
    }

    /** 仅更新本地头像（上传后即时生效，省去再拉一次 profile）。 */
    private fun applyAvatar(url: String) {
        BackendPrefs.setSession(
            accessToken = BackendPrefs.token,
            userId = BackendPrefs.userId,
            username = BackendPrefs.username,
            email = BackendPrefs.email,
            nickname = BackendPrefs.nickname,
            avatar = url,
            signature = BackendPrefs.signature,
        )
        emit()
    }

    /** 把 content Uri 读成 multipart 的 `file` 字段。 */
    private fun buildImagePart(context: Context, uri: Uri): MultipartBody.Part {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "image/jpeg"
        val ext = mime.substringAfter('/', "jpg").takeIf { it.isNotBlank() } ?: "jpg"
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选图片")
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(
            "file",
            "avatar_${System.currentTimeMillis()}.$ext",
            body,
        )
    }

    private fun applySession(vo: LoginResultVO) {
        val u = vo.user
        BackendPrefs.setSession(
            accessToken = vo.accessToken ?: "",
            userId = u?.id ?: 0L,
            username = u?.username ?: "",
            email = u?.email ?: "",
            nickname = u?.nickname,
            avatar = u?.avatar,
            signature = u?.signature,
        )
        emit()
    }
}
