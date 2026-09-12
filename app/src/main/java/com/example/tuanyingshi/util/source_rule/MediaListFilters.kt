package com.example.tuanyingshi.util.source_rule

/**
 * 条目 / 剧集过滤器与字符串匹配率。
 *
 * 移植自 animeko `me.him188.ani.app.domain.mediasource` 下的
 * `MediaListFilter` / `MediaListFilterContext` / `MediaListFilters` / `StringMatcher`。
 *
 * 与 animeko 的差异（为可读性做的简化）：
 * - animeko 用 context receiver（`fun Ctx.applyOn(candidate)`），这里改为普通二元函数
 *   `fun apply(context, candidate)`，语义完全一致；
 * - [MediaListFilters.ContainsSubjectName] 的剧集维度（animeko `ContainsEpisodeSort` 等）用
 *   [MediaListFilter.Candidate.episodeNumber]（`Int?`）表达，因为本项目剧集序号就是 `Int?`。
 */
object MediaListFilters {

    // ───────────────────────── 过滤器 ─────────────────────────

    /**
     * 要求包含条目名称。支持模糊匹配。
     *
     * 判定（与 animeko 一致）：去特殊字符后 **包含**，或 [StringMatcher.calculateMatchRate] ≥ [FUZZY_MATCH_RATE]。
     */
    val ContainsSubjectName = MediaListFilter { context, candidate ->
        context.subjectNamesWithoutSpecial.any { subjectName ->
            val title = removeSpecials(candidate.subjectName, removeWhitespace = true, replaceNumbers = true)
            title.contains(subjectName, ignoreCase = true) ||
                StringMatcher.calculateMatchRate(title, subjectName) >= FUZZY_MATCH_RATE
        }
    }

    /** 剧集序号一致（对应 animeko `ContainsEpisodeSort`）。 */
    val ContainsEpisodeNumber = MediaListFilter { context, candidate ->
        val expected = context.episodeNumber ?: return@MediaListFilter false
        candidate.episodeNumber == expected
    }

    /** 季度内集数一致（对应 animeko `ContainsEpisodeEp`）。 */
    val ContainsEpisodeEp = MediaListFilter { context, candidate ->
        val expected = context.episodeEp ?: return@MediaListFilter false
        candidate.episodeNumber == expected
    }

    /** 特殊剧集（序号解析不出来时）按名称匹配（对应 animeko `ContainsEpisodeName`）。 */
    val ContainsEpisodeName = MediaListFilter { context, candidate ->
        val expected = context.episodeNameForCompare ?: return@MediaListFilter false
        if (expected.isBlank()) return@MediaListFilter false
        removeSpecials(candidate.episodeName ?: candidate.originalTitle, removeWhitespace = true, replaceNumbers = true)
            .contains(expected, ignoreCase = true)
    }

    /**
     * 对应 animeko `ContainsAnyEpisodeInfo`：系列集数 → 特殊剧集按名 → 季度内集数。
     *
     * animeko 的剧集过滤只启用 [ContainsEpisodeNumber]/[ContainsEpisodeName]/[ContainsEpisodeEp]，
     * 不启用 [ContainsSubjectName]——web 源的剧集名通常是「第x集」，本身不含条目名。
     */
    val ContainsAnyEpisodeInfo = listOf(ContainsEpisodeNumber, ContainsEpisodeName, ContainsEpisodeEp)

    /** 模糊匹配阈值（animeko `MediaListFilters.ContainsSubjectName` 中硬编码的 80）。 */
    const val FUZZY_MATCH_RATE = 80

    /** 逐条应用过滤器，全部通过才保留。 */
    fun applyAll(
        filters: Iterable<MediaListFilter>,
        context: SubjectFilterContext,
        candidate: MediaListFilter.Candidate,
    ): Boolean = filters.all { it.apply(context, candidate) }

    // ───────────────────────── 字符串归一化 ─────────────────────────

    private const val MINIMUM_LENGTH = 2

    /** "Re："（全角冒号）。用转义写，避免源码里出现全角标点。 */
    private const val KEEP_WORD = "Re\uFF1A"
    private val keepWords = listOf(KeepWords(KEEP_WORD, "\uE0010\uE002"))

    /** 无条件删除的字符（animeko `charsToDelete`，当前为空）。 */
    private val charsToDelete: Set<Int> = emptySet()

    /** 条件替换为空格的标点符号（animeko `charsToReplaceWithWhitespace`）。 */
    private val charsToReplaceWithWhitespace: Set<Int> =
        "。、，·・[]～“”~—-!@#\$%^&*()_+{}|\\;':\",.<>/?【】：「」！".map { it.code }.toSet()

    private val whitespaceChars: Set<Int> = setOf(' '.code, '\t'.code, 0x3000)

    /** 中文数字 / 罗马数字 → 阿拉伯数字（animeko `numberMappings`）。 */
    private val numberMappings = mapOf(
        "X" to "10", "IX" to "9", "VIII" to "8", "VII" to "7", "VI" to "6",
        "V" to "5", "IV" to "4", "III" to "3", "II" to "2", "I" to "1",
        "十" to "10", "九" to "9", "八" to "8", "七" to "7", "六" to "6",
        "五" to "5", "四" to "4", "三" to "3", "二" to "2", "一" to "1",
    )

    private val allNumbersRegex = numberMappings.keys.joinToString("|").toRegex()

    /**
     * 处理特殊字符（animeko `MediaListFilters.removeSpecials`）。
     *
     * 1. [keepWords] 里的词替换为掩码，保证不被后续规则破坏；
     * 2. [removeMarkers] 时无条件删除「电影 / 剧场版 / OVA / OAD / 总集篇」等标记；
     * 3. 逐字符扫描：开头的标点直接处理；累积到 [MINIMUM_LENGTH] 个非特殊字符之后，
     *    才对后续标点应用删除 / 替换为空格（否则会把 "A B" 之类短串里的符号误删）；
     * 4. [replaceNumbers] 时把中文数字 / 罗马数字还原为阿拉伯数字（含「五等份」「中二病」等例外）；
     * 5. [removeWhitespace] 时删除全部空白；
     * 6. 还原 [keepWords] 掩码。
     */
    fun removeSpecials(
        string: String,
        removeWhitespace: Boolean,
        replaceNumbers: Boolean,
        removeMarkers: Boolean = false,
    ): String {
        // 1
        var result = keepWords.fold(string) { acc, keepWord -> acc.replace(keepWord.originalWord, keepWord.mask) }

        // 2
        result = StringBuilder(result).apply {
            if (removeMarkers) {
                deletePrefix("电影")
                deleteInfix("电影")
                deletePrefix("剧场版")
                deleteInfix("剧场版")
                deletePrefix("OVA")
                deleteInfix("OVA")
                deleteInfix("OAD")
                deleteInfix("总集篇")
            }
        }.toString()

        // 3
        result = applyConditionalRules(result)

        // 4
        if (replaceNumbers) {
            result = replaceNumbers(result)
        }

        // 5
        var out = if (removeWhitespace) result.filter { it.code !in whitespaceChars } else result
        out = out.trim(*whitespaceChars.map { it.toChar() }.toCharArray())

        // 6
        return keepWords.fold(out) { acc, keepWord -> acc.replace(keepWord.mask, keepWord.originalWord) }
    }

    /** animeko `specialEquals`。 */
    fun specialEquals(first: String, second: String): Boolean =
        removeSpecials(first, removeWhitespace = true, replaceNumbers = true).equals(
            removeSpecials(second, removeWhitespace = true, replaceNumbers = true),
            ignoreCase = true,
        )

    /** animeko `specialContains`：去特殊字符后的包含判断。 */
    fun specialContains(string: String, sub: String): Boolean =
        removeSpecials(string, removeWhitespace = true, replaceNumbers = true).contains(
            removeSpecials(sub, removeWhitespace = true, replaceNumbers = true),
            ignoreCase = true,
        )

    /**
     * 条目名与搜索词的匹配率（0..100）。
     *
     * 等价于 animeko `ContainsSubjectName` 里「去特殊字符后算 [StringMatcher.calculateMatchRate]」。
     */
    fun matchRate(title: String, subjectName: String): Int = StringMatcher.calculateMatchRate(
        removeSpecials(title, removeWhitespace = true, replaceNumbers = true),
        removeSpecials(subjectName, removeWhitespace = true, replaceNumbers = true),
    )

    // ───────────────────────── 内部实现 ─────────────────────────

    private data class KeepWords(val originalWord: String, val mask: String)

    /**
     * 逐字符扫描，按 [MINIMUM_LENGTH] 规则处理 [charsToDelete] / [charsToReplaceWithWhitespace]。
     * 直接对应 animeko `applyConditionalRules`。
     */
    private fun applyConditionalRules(original: String): String {
        val sb = StringBuilder()
        var nonSpecialCount = 0
        var canProcess = false

        for (c in original) {
            if (!c.isSpecialChar()) {
                sb.append(c)
                nonSpecialCount++
                if (!canProcess && nonSpecialCount >= MINIMUM_LENGTH) canProcess = true
                continue
            }

            val code = c.code
            val shouldProcess = when {
                nonSpecialCount == 0 -> true // 开头的特殊字符无条件处理
                canProcess -> true
                else -> false
            }
            if (!shouldProcess) {
                sb.append(c) // 非特殊字符还不够，保留原字符
            } else if (charsToDelete.contains(code)) {
                // 删除
            } else if (charsToReplaceWithWhitespace.contains(code)) {
                sb.append(' ')
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun Char.isSpecialChar(): Boolean {
        val code = code
        return charsToDelete.contains(code) || charsToReplaceWithWhitespace.contains(code)
    }

    private fun replaceNumbers(original: String): String {
        return allNumbersRegex.replace(original) { match -> replaceNumberConditional(original, match) }
    }

    /**
     * animeko `replaceNumberConditional`。
     *
     * 注意「五等份的花嫁」「中二病也要谈恋爱」里的数字不该被替换，
     * 而「第三季」应该变成「第3季」；`OVA` 中间的 `V` 也不该变成 5。
     */
    private fun replaceNumberConditional(original: String, match: MatchResult): String {
        val value = match.value
        if (original.isLetterOrDigitAt(match.range.first - 1) || original.isLetterOrDigitAt(match.range.last + 1)) {
            if (value.isChineseNumber()) {
                if (match.range.first == 0 && original.isChineseCharacterAt(match.range.last + 1)) return value
                if (match.range.first > 0 && match.range.last < original.lastIndex) {
                    if (original[match.range.first - 1] != '第') return value
                }
            } else if (!original.isJapaneseCharacterAt(match.range.first - 1)) {
                // 英文作品名的系列数字一定有空格，中间出现的不替换
                return value
            }
        }

        if (value == "V") {
            val prev = original.reverseFirstLetterOrDigit(match.range.first - 1)
            val next = original.traverseFirstLetterOrDigit(match.range.last + 1)
            if ((prev == 'O' || prev == 'o') && (next == 'A' || next == 'a')) return value
        }

        return numberMappings[value] ?: value
    }

    private fun String.isLetterOrDigitAt(index: Int): Boolean = getOrNull(index)?.isLetterOrDigit() == true

    private fun String.isChineseCharacterAt(index: Int): Boolean = (getOrNull(index)?.code ?: -1) in 0x4E00..0x9FFF

    private fun String.isJapaneseCharacterAt(index: Int): Boolean {
        val code = getOrNull(index)?.code ?: return false
        return code in 0x3040..0x309F || code in 0x30A0..0x30FF
    }

    /** 向前找到第一个字母或数字。 */
    private fun String.reverseFirstLetterOrDigit(fromIndex: Int): Char? {
        for (i in fromIndex downTo 0) {
            val c = getOrNull(i) ?: return null
            if (c.isLetterOrDigit()) return c
        }
        return null
    }

    /** 向后找到第一个字母或数字。 */
    private fun String.traverseFirstLetterOrDigit(fromIndex: Int): Char? {
        for (i in fromIndex until length) {
            val c = get(i)
            if (c.isLetterOrDigit()) return c
        }
        return null
    }

    private fun StringBuilder.deletePrefix(word: String) {
        if (word.isNotEmpty() && indexOf(word) == 0) delete(0, word.length)
    }

    private fun StringBuilder.deleteInfix(word: String) {
        if (word.isEmpty()) return
        while (true) {
            val index = indexOf(word)
            if (index < 0) break
            delete(index, index + word.length)
        }
    }

    private fun String.isChineseNumber(): Boolean = this in setOf(
        "十", "九", "八", "七", "六", "五", "四", "三", "二", "一",
    )
}

/**
 * 一个通用的过滤器（对应 animeko `MediaListFilter`）。
 *
 * @see MediaListFilters
 */
fun interface MediaListFilter {
    /** 待过滤对象。通常由业务对象转换而来。 */
    interface Candidate {
        val originalTitle: String
        val subjectName: String get() = originalTitle
        /** 剧集序号；`null` 表示未解析出（特殊剧集）。 */
        val episodeNumber: Int?
        val episodeName: String? get() = null
    }

    /** 返回 `true` 表示 [candidate] 通过过滤。 */
    fun apply(context: SubjectFilterContext, candidate: Candidate): Boolean
}

/**
 * 过滤所需的查询上下文（对应 animeko `MediaListFilterContext`）。
 *
 * @param subjectNames 条目的所有名称（主名 / 原名 / 别名）
 */
class SubjectFilterContext(
    val subjectNames: Set<String>,
    val episodeNumber: Int? = null,
    val episodeEp: Int? = null,
    val episodeName: String? = null,
) {
    val subjectNamesWithoutSpecial: Set<String> by lazy {
        subjectNames.mapTo(HashSet(subjectNames.size)) {
            MediaListFilters.removeSpecials(it, removeWhitespace = true, replaceNumbers = true)
        }
    }

    val episodeNameForCompare: String? by lazy {
        episodeName?.let { MediaListFilters.removeSpecials(it, removeWhitespace = true, replaceNumbers = true) }
    }
}

/** animeko `StringMatcher`：基于 Levenshtein 距离的相似度百分比（0..100）。 */
object StringMatcher {
    fun calculateMatchRate(a: String, b: String): Int {
        if (a.isEmpty() && b.isEmpty()) return 100
        val distance = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 100
        val similarity = 1 - (distance.toDouble() / maxLen)
        return (similarity * 100).toInt().coerceIn(0, 100)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
