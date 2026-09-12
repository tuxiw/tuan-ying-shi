package com.example.tuanyingshi.data.repository

import com.example.tuanyingshi.util.ContentPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内容过滤收口：把 NSFW 开关与「隐藏看过/抛弃」开关，
 * 以及「看过(历史) ∪ 抛弃(状态表)」的 detailUrl 集合，组合为列表可直接消费的流。
 *
 * - [nsfwBlock]：NSFW 屏蔽开关（来自 ContentPrefs）。
 * - [hiddenDetailUrls]：受 [ContentPrefs.hideWatched] 门控的隐藏集合
 *   （hideWatched 关闭时恒为空集，即不隐藏任何条目）。
 */
@Singleton
class ContentFilterRepository @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val userStatusRepository: UserStatusRepository,
) {
    val nsfwBlock: Flow<Boolean> get() = ContentPrefs.nsfwBlock
    val hideWatched: Flow<Boolean> get() = ContentPrefs.hideWatched

    private val watchedUrls: Flow<Set<String>> = historyRepository.observeHistory()
        .map { list -> list.map { it.detailUrl }.toSet() }

    private val abandonedUrls: Flow<Set<String>> = userStatusRepository.observeAllAbandoned()
        .map { list -> list.map { it.detailUrl }.toSet() }

    val hiddenDetailUrls: Flow<Set<String>> = combine(hideWatched, watchedUrls, abandonedUrls) { hide, watched, abandoned ->
        if (!hide) emptySet() else (watched + abandoned)
    }

    /**
     * 合并后的过滤状态（NSFW 开关 + 隐藏集合），供列表流以「两流 combine」接入，
     * 避免依赖三流以上的 combine 重载（部分协程版本不支持）。
     */
    val contentFilterFlow: Flow<Pair<Boolean, Set<String>>> =
        combine(nsfwBlock, hiddenDetailUrls) { block, hidden -> block to hidden }
}
