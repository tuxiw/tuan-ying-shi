package com.example.tuanyingshi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.data.remote.api.cycani.CycaniAuthManager
import com.example.tuanyingshi.data.remote.api.cycani.CycaniLoginState
import com.example.tuanyingshi.util.ResourceSource
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.source_rule.KazumiWebViewVerifier
import com.example.tuanyingshi.util.source_rule.ResourceVerifyChecker
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import com.example.tuanyingshi.util.source_rule.SourceSubscription
import com.example.tuanyingshi.util.source_rule.SourceSubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val sources: List<ResourceSource> = emptyList(),
    val currentSourceId: String = SourceHolder.DEFAULT_SOURCE_ID,
    val cycaniLoginState: CycaniLoginState = CycaniLoginState.LoggedOut,
    val showLoginDialog: Boolean = false,
    val loginError: String? = null,
    val isLoggingIn: Boolean = false,
    /** 资源源编辑弹窗是否打开；[editingSource] 为 null 表示「新增」，否则为「编辑」。 */
    val editorOpen: Boolean = false,
    val editingSource: ResourceSource? = null,
    /** 数据源订阅列表，供「数据源订阅」区域展示。 */
    val subscriptions: List<SourceSubscription> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    /** 登录弹窗内部状态（loading / 错误信息）。 */
    private val loginDialogState = MutableStateFlow(LoginDialogState())

    /** 用户点击 cycani 类源后暂存的目标源 id，用于登录成功后自动切换。 */
    private val pendingSourceId = MutableStateFlow<String?>(null)

    /** 前 5 个流（源列表/当前源/登录态/待切换/登录弹窗）组合成的中间结果。
     * 说明：kotlinx.coroutines 的 [combine] 只提供 2~5 个流的重载，
     * 多于 5 个需分批组合，否则会误匹配到 vararg[Array<T>] 重载导致类型推断失败。 */
    private data class SourceLoginCombo(
        val sources: List<ResourceSource>,
        val currentId: String,
        val loginState: CycaniLoginState,
        val pendingId: String?,
        val dialogState: LoginDialogState,
    )

    private val sourceLoginCombo: Flow<SourceLoginCombo> = combine(
        SourceHolder.allSourcesFlow,
        SourceHolder.currentSourceIdFlow,
        CycaniAuthManager.loginState,
        pendingSourceId,
        loginDialogState,
    ) { sources, currentId, loginState, pendingId, dialogState ->
        SourceLoginCombo(
            sources = sources,
            currentId = currentId,
            loginState = loginState,
            pendingId = pendingId,
            dialogState = dialogState,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        sourceLoginCombo,
        SourceSubscriptionRepository.subscriptions,
        SourceRuleRepository.rules,
    ) { combo, subs, _ ->
        val needLogin = combo.pendingId != null &&
            combo.sources.firstOrNull { it.id == combo.pendingId }?.type == SourceMode.Cycanime &&
            combo.loginState is CycaniLoginState.LoggedOut
        SettingsUiState(
            sources = combo.sources,
            currentSourceId = combo.currentId,
            cycaniLoginState = combo.loginState,
            showLoginDialog = needLogin,
            loginError = if (needLogin) combo.dialogState.error else null,
            isLoggingIn = if (needLogin) combo.dialogState.isLoading else false,
            subscriptions = subs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            sources = SourceHolder.getResourceSources(),
            currentSourceId = SourceHolder.currentSourceId,
            cycaniLoginState = CycaniAuthManager.loginState.value,
        ),
    )

    /**
     * 选中某个资源源作为默认。
     * - 非 cycani 类源：直接切换；
     * - cycani 类源：若已登录则切换，未登录则弹出登录框（不切换）。
     */
    fun selectSource(id: String) {
        val rs = SourceHolder.getResourceSource(id) ?: return
        if (id == SourceHolder.currentSourceId) {
            pendingSourceId.value = null
            return
        }

        if (rs.type == SourceMode.Cycanime && !CycaniAuthManager.isLoggedIn) {
            pendingSourceId.value = id
            loginDialogState.value = LoginDialogState()
            return
        }

        clearPendingAndLoginDialog()
        viewModelScope.launch { SourceHolder.switchSource(id) }
    }

    // ───────── 资源源增删改 ─────────

    /** 打开「新增资源源」弹窗。 */
    fun requestAdd() {
        pendingSourceId.value = null
        _editorSource.value = null
        _editorOpen.value = true
    }

    /** 打开「编辑资源源」弹窗（内置源也可编辑，仅不可删除）。 */
    fun requestEdit(rs: ResourceSource) {
        _editorSource.value = rs
        _editorOpen.value = true
    }

    fun dismissEditor() {
        _editorOpen.value = false
        _editorSource.value = null
    }

    /**
     * 保存编辑结果：新增或更新（依据 [editingSource] 是否为 null）。
     * 内置源编辑时保留其 [ResourceSource.isBuiltIn] = true。
     */
    fun confirmSave(name: String, type: SourceMode, baseUrl: String) {
        val editing = _editorSource.value
        if (editing == null) {
            SourceHolder.addSource(name, type, baseUrl)
        } else {
            SourceHolder.updateSource(editing.copy(name = name, type = type, baseUrl = baseUrl))
        }
        dismissEditor()
    }

    /** 删除资源源；内置源由 UI 禁用删除，这里也做兜底拦截。 */
    fun requestDelete(rs: ResourceSource): Boolean {
        if (rs.isBuiltIn) return false
        return SourceHolder.deleteSource(rs.id)
    }

    /** 删除规则源：先删规则，再同步资源源列表（该规则源会从切换列表中消失，必要时回退默认源）。 */
    fun deleteRuleSource(ruleId: String) {
        SourceRuleRepository.delete(ruleId)
        SourceHolder.syncRules()
    }

    // ───────── 数据源订阅 ─────────

    /** 新增订阅（按 URL 命名，可传自定义名称与格式）。 */
    fun addSubscription(url: String, name: String = "", format: String = "dandanplay") {
        SourceSubscriptionRepository.add(url.trim(), name, format)
    }

    /** 删除订阅（同时清掉其名下全部规则源）。 */
    fun removeSubscription(id: String) {
        SourceSubscriptionRepository.remove(id)
        SourceHolder.syncRules()
    }

    /** 手动刷新单条订阅。 */
    fun refreshSubscription(id: String) {
        val sub = SourceSubscriptionRepository.getById(id) ?: return
        viewModelScope.launch { SourceSubscriptionRepository.refresh(sub) }
    }

    /** 手动刷新全部订阅。 */
    fun refreshAllSubscriptions() {
        viewModelScope.launch { SourceSubscriptionRepository.refreshAll() }
    }

    /** 选中订阅下的某个数据源作为默认源（其 ResourceSource id 为 `rule_<ruleId>`）。 */
    fun selectSubscriptionSource(ruleId: String) {
        viewModelScope.launch { SourceHolder.switchSource("rule_$ruleId") }
    }

    // ───────── 一键验证：检查 + 用户选择验证 ─────────

    /**
     * 一键检查：并发探测所有启用了反爬（antiCrawler）的源规则，
     * 收集当前弹出验证页的源作为候选，供用户在弹窗里勾选要验证哪些。
     */
    fun checkVerification() {
        if (_isChecking.value) return
        viewModelScope.launch {
            _isChecking.value = true
            _verifyMessage.value = null
            _verifyCandidates.value = emptyList()
            val rules = SourceRuleRepository.rules.value.filter { it.antiCrawler.enabled }
            val needs = mutableListOf<SourceRule>()
            rules.map { rule ->
                async(Dispatchers.IO) { rule to ResourceVerifyChecker.needsVerification(rule) }
            }.map { it.await() }.forEach { (rule, need) ->
                if (need) needs.add(rule)
            }
            _verifyCandidates.value = needs
            _isChecking.value = false
            if (needs.isEmpty()) {
                _verifyMessage.value = "检查完成：所有资源当前都无需验证 ✓"
            }
        }
    }

    /**
     * 验证用户勾选的源：逐个用 WebView 过验证（类型1 弹验证码输入、类型2 自动点击、类型3 执行脚本），
     * 验证通过后 Cookie 自动存入 [com.example.tuanyingshi.util.source_rule.SourceCookieManager]。
     */
    fun verifySelected(ids: List<String>) {
        val rules = _verifyCandidates.value.filter { ids.contains(it.id) }
        if (rules.isEmpty()) {
            _verifyCandidates.value = emptyList()
            return
        }
        viewModelScope.launch {
            var ok = 0
            for (rule in rules) {
                _verifyingIds.value = _verifyingIds.value + rule.id
                val res = KazumiWebViewVerifier.verifySearch(rule, ResourceVerifyChecker.PROBE_KEYWORD)
                _verifyingIds.value = _verifyingIds.value - rule.id
                if (res != null) ok++
            }
            _verifyCandidates.value = emptyList()
            _verifyMessage.value = "验证完成：$ok/${rules.size} 个资源已通过验证"
        }
    }

    /** 关闭验证选择弹窗（取消检查结果的展示）。 */
    fun dismissVerifyDialog() {
        _verifyCandidates.value = emptyList()
    }

    /** 清除结果提示文案。 */
    fun clearVerifyMessage() {
        _verifyMessage.value = null
    }

    // 编辑弹窗状态（独立于 uiState 组合，便于读写当前正在编辑的源）
    private val _editorOpen = MutableStateFlow(false)
    private val _editorSource = MutableStateFlow<ResourceSource?>(null)
    val editorOpen: StateFlow<Boolean> = _editorOpen.asStateFlow()
    val editingSource: StateFlow<ResourceSource?> = _editorSource.asStateFlow()

    // ───────── 一键验证检查 ─────────
    /** 是否正在检查各资源是否需要验证。 */
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    /**
     * 检查后判定「需要验证」的候选源列表（待用户勾选要验证哪些）。
     * 为空表示检查已完成且无需验证，或尚未检查。
     */
    private val _verifyCandidates = MutableStateFlow<List<SourceRule>>(emptyList())
    val verifyCandidates: StateFlow<List<SourceRule>> = _verifyCandidates.asStateFlow()

    /** 正在（逐个）验证中的源 id 集合，用于进度展示。 */
    private val _verifyingIds = MutableStateFlow<Set<String>>(emptySet())
    val verifyingIds: StateFlow<Set<String>> = _verifyingIds.asStateFlow()

    /** 验证完成后的结果提示文案（null 表示无提示）。 */
    private val _verifyMessage = MutableStateFlow<String?>(null)
    val verifyMessage: StateFlow<String?> = _verifyMessage.asStateFlow()

    /**
     * 执行源站登录；成功后自动切换到目标 cycani 类资源源。
     */
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            loginDialogState.value = LoginDialogState(error = "请输入账号和密码")
            return
        }

        viewModelScope.launch {
            loginDialogState.value = LoginDialogState(isLoading = true)
            val error = CycaniAuthManager.login(username, password)
            loginDialogState.value = LoginDialogState(error = error)

            if (error == null) {
                val targetId = pendingSourceId.value
                if (targetId != null &&
                    SourceHolder.getResourceSource(targetId)?.type == SourceMode.Cycanime
                ) {
                    pendingSourceId.value = null
                    SourceHolder.switchSource(targetId)
                }
                clearPendingAndLoginDialog()
            }
        }
    }

    /**
     * 退出次元城源站登录。
     * 若当前正在使用 cycani 类资源源，退出后自动切回默认源（避免后续 API 返回 401）。
     */
    fun logout() {
        CycaniAuthManager.clearSession()
        clearPendingAndLoginDialog()
        if (SourceHolder.currentSourceMode == SourceMode.Cycanime) {
            viewModelScope.launch {
                SourceHolder.switchSource(SourceHolder.DEFAULT_SOURCE_ID)
            }
        }
    }

    /** 关闭登录弹窗。 */
    fun dismissLoginDialog() {
        clearPendingAndLoginDialog()
    }

    private fun clearPendingAndLoginDialog() {
        pendingSourceId.value = null
        loginDialogState.value = LoginDialogState()
    }

    private data class LoginDialogState(
        val isLoading: Boolean = false,
        val error: String? = null,
    )
}
