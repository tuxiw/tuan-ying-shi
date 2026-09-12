package com.example.tuanyingshi.ui.settings.source_editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tuanyingshi.util.source_rule.SourceAnalysisEngine
import com.example.tuanyingshi.util.source_rule.SourceRule
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * CSS 数据源「测试分析工具」的 ViewModel（仅调试模式入口调用）。
 *
 * 复用 [SourceEditorViewModel.runTest] 的并发安全做法：每次分析用单调递增的 [testToken]
 * 标记，新分析会取消上一轮尚未完成的 WebView 抓取协程，只让最新一轮回写 UI，
 * 避免主线程上多个 WebView 抓取叠加互相干扰。
 */
@HiltViewModel
class SourceAnalysisViewModel @Inject constructor() : ViewModel() {

    private val _rule = MutableStateFlow(SourceRule())
    val rule: StateFlow<SourceRule> = _rule.asStateFlow()

    private val _keyword = MutableStateFlow("樱 Trick")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private val _report = MutableStateFlow<SourceAnalysisEngine.SourceAnalysisReport?>(null)
    val report: StateFlow<SourceAnalysisEngine.SourceAnalysisReport?> = _report.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val testToken = AtomicInteger(0)
    private var analysisJob: Job? = null

    fun loadRule(id: String?) {
        if (id == null) {
            _rule.value = SourceRule()
            return
        }
        SourceRuleRepository.getById(id)?.let { _rule.value = it }
    }

    fun updateKeyword(value: String) {
        _keyword.value = value
    }

    fun runAnalysis() {
        analysisJob?.cancel()
        val token = testToken.incrementAndGet()
        analysisJob = viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            try {
                val result = SourceAnalysisEngine.analyze(_rule.value, _keyword.value)
                if (token == testToken.get()) _report.value = result
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Exception) {
                if (token == testToken.get()) _error.value = e.message ?: "分析失败"
            } finally {
                if (token == testToken.get()) _isAnalyzing.value = false
            }
        }
    }
}
