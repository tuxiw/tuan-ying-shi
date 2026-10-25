package com.example.tuanyingshi.ui.cycani

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.remote.api.cycani.CycaniAuthManager
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 次元城源站登录 / 注册全屏页共用 ViewModel。
 *
 * 登录 / 注册成功后的收尾动作：
 * 1. 拉取完整资料（头像 / ID / 用户组）；
 * 2. 若带着 pendingSourceId 进入（数据源页点击次元城源触发），自动切换到该源。
 */
@HiltViewModel
class CycaniAuthViewModel @Inject constructor() : ViewModel() {

    // ───────── 登录 ─────────
    private val _loggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    /** 上次登录账号（失效后保留，用于登录页回填）。 */
    val lastUsername: String? get() = CycaniAuthManager.username

    fun login(username: String, password: String, pendingSourceId: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            _loginError.value = "请输入账号和密码"
            return
        }
        if (_loggingIn.value) return
        viewModelScope.launch {
            _loggingIn.value = true
            _loginError.value = null
            val error = withContext(Dispatchers.IO) {
                CycaniAuthManager.login(username.trim(), password)
            }
            _loggingIn.value = false
            if (error == null) {
                CycaniAuthManager.clearSessionExpiredFlag()
                // 登录接口返回的用户节点可能是精简的，再拉一次完整资料
                CycaniAuthManager.fetchProfile()
                applyPendingSource(pendingSourceId)
                onSuccess()
            } else {
                _loginError.value = error
            }
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    // ───────── 注册（邮箱验证码）─────────
    private val _registering = MutableStateFlow(false)
    val registering: StateFlow<Boolean> = _registering.asStateFlow()

    private val _sendingCode = MutableStateFlow(false)
    val sendingCode: StateFlow<Boolean> = _sendingCode.asStateFlow()

    private val _registerError = MutableStateFlow<String?>(null)
    val registerError: StateFlow<String?> = _registerError.asStateFlow()

    private val _registerInfo = MutableStateFlow<String?>(null)
    val registerInfo: StateFlow<String?> = _registerInfo.asStateFlow()

    /** 验证码冷却剩余秒数（>0 时「获取验证码」按钮禁用）。 */
    private val _codeCountdown = MutableStateFlow(0)
    val codeCountdown: StateFlow<Int> = _codeCountdown.asStateFlow()

    fun sendCode(email: String) {
        if (email.isBlank()) {
            _registerError.value = "请输入邮箱"
            return
        }
        if (_sendingCode.value) return
        viewModelScope.launch {
            _sendingCode.value = true
            _registerError.value = null
            _registerInfo.value = null
            val error = withContext(Dispatchers.IO) { CycaniAuthManager.sendRegisterCode(email) }
            _sendingCode.value = false
            if (error == null) {
                _registerInfo.value = "验证码已发送至 $email，请查收"
                startCodeCountdown(60)
            } else {
                _registerError.value = error
            }
        }
    }

    fun register(
        username: String,
        password: String,
        email: String,
        code: String,
        pendingSourceId: String,
        onSuccess: () -> Unit,
    ) {
        if (username.isBlank() || password.isBlank() || email.isBlank() || code.isBlank()) {
            _registerError.value = "请填写完整的注册信息"
            return
        }
        if (_registering.value) return
        viewModelScope.launch {
            _registering.value = true
            _registerError.value = null
            _registerInfo.value = null
            val error = withContext(Dispatchers.IO) {
                CycaniAuthManager.register(username.trim(), password, email, code)
            }
            _registering.value = false
            if (error == null) {
                CycaniAuthManager.clearSessionExpiredFlag()
                // 注册接口已自动登录，拉一次完整资料（头像 / ID / 用户组）
                CycaniAuthManager.fetchProfile()
                applyPendingSource(pendingSourceId)
                onSuccess()
            } else {
                _registerError.value = error
            }
        }
    }

    fun clearRegisterState() {
        _registerError.value = null
        _registerInfo.value = null
    }

    private fun startCodeCountdown(seconds: Int) {
        viewModelScope.launch {
            for (i in seconds downTo 1) {
                _codeCountdown.value = i
                kotlinx.coroutines.delay(1_000L)
            }
            _codeCountdown.value = 0
        }
    }

    /** 登录 / 注册成功后：若带着待切换的 cycani 源进入，自动切到该源。 */
    private fun applyPendingSource(pendingSourceId: String) {
        val id = pendingSourceId.trim()
        if (id.isBlank()) return
        if (SourceHolder.getResourceSource(id)?.type == SourceMode.Cycanime) {
            viewModelScope.launch { SourceHolder.switchSource(id) }
        }
    }
}
