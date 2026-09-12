package com.example.tuanyingshi.data.remote.api

import com.example.tuanyingshi.data.remote.dto.AnimeBean
import com.example.tuanyingshi.data.remote.dto.AnimeDetailBean
import com.example.tuanyingshi.data.remote.dto.HomeBean
import com.example.tuanyingshi.data.remote.dto.VideoBean
import com.example.tuanyingshi.data.remote.parse.AnimeSource
import com.example.tuanyingshi.data.remote.parse.BackendAnimeSource
import com.example.tuanyingshi.data.remote.parse.RuleAggregateAnimeSource
import com.example.tuanyingshi.data.remote.parse.RuleBasedAnimeSource
import com.example.tuanyingshi.util.BackendPrefs
import com.example.tuanyingshi.util.log
import com.example.tuanyingshi.util.SearchChunk
import com.example.tuanyingshi.util.SearchResultCache
import com.example.tuanyingshi.util.SearchStreamState
import com.example.tuanyingshi.util.SourceHolder
import com.example.tuanyingshi.util.SourceMode
import com.example.tuanyingshi.util.dedupeSearch
import com.example.tuanyingshi.util.source_rule.RuleExecutor
import com.example.tuanyingshi.util.source_rule.SourceRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeApiImpl @Inject constructor() : AnimeApi {
    override suspend fun getHomeAllData(): List<HomeBean> {
        val animeSource = SourceHolder.currentSource
        return animeSource.getHomeData()
    }

    override suspend fun getAnimeDetail(detailUrl: String, mode: SourceMode, sourceId: String): AnimeDetailBean {
        // 后端模式：所有详情数据走后端 API，忽略聚合搜索带来的 ruleId。
        if (BackendPrefs.isBackendMode()) return BackendAnimeSource.getAnimeDetail(detailUrl)
        // 聚合搜索结果携带来源 ruleId：直接定位到该 CSS 规则取详情，
        // 不再回退到聚合源逐条试全部 CSS 源（避免"详情页查全部"的卡顿）。
        if (sourceId.isNotBlank()) {
            val rule = SourceRuleRepository.getById(sourceId)
            if (rule != null) {
                "getAnimeDetail: sourceId=$sourceId 命中规则→直接 RuleBasedAnimeSource 取详情".log("AnimeApiImpl", "getAnimeDetail")
                val detail = RuleBasedAnimeSource(rule).getAnimeDetail(detailUrl)
                if (detail.title.isNotBlank()) return detail
                "getAnimeDetail: sourceId=$sourceId 该规则解析为空，回退聚合源".log("AnimeApiImpl", "getAnimeDetail")
            } else {
                "getAnimeDetail: sourceId=$sourceId 未找到对应规则，回退聚合源".log("AnimeApiImpl", "getAnimeDetail")
            }
        }
        val animeSource = SourceHolder.getSource(mode)
        return animeSource.getAnimeDetail(detailUrl)
    }

    override suspend fun getVideoData(episodeUrl: String, mode: SourceMode): VideoBean {
        val animeSource = SourceHolder.getSource(mode)
        return animeSource.getVideoData(episodeUrl)
    }

    override suspend fun getVideoDataByRule(ruleId: String, episodeUrl: String): VideoBean {
        // 后端模式下无规则源切换，原样返回 episode 地址（由播放器直接播放后端给的流）。
        if (BackendPrefs.isBackendMode()) return VideoBean(videoUrl = episodeUrl, headers = emptyMap())
        val rule = SourceRuleRepository.getById(ruleId) ?: return VideoBean(videoUrl = "", headers = emptyMap())
        return RuleBasedAnimeSource(rule).getVideoData(episodeUrl)
    }

    override suspend fun getSearchData(query: String, page: Int, mode: SourceMode): List<AnimeBean> {
        val animeSource = SourceHolder.getSource(mode)
        val sourceId = SourceHolder.currentSourceId
        // 搜索结果 TTL 缓存：命中直接复用，避免重复 WebView/OkHttp 抓取（空结果不缓存，见 SearchResultCache）
        SearchResultCache.get(query, sourceId, page)?.let { return it }
        val result = animeSource.getSearchData(query, page)
        SearchResultCache.put(query, sourceId, page, result)
        return result
    }

    override fun getSearchStream(query: String, mode: SourceMode): Flow<SearchStreamState> {
        val sourceId = SourceHolder.currentSourceId
        // 命中缓存：直接整页返回，不走流式（重复搜索同一词零网络）
        SearchResultCache.get(query, sourceId, 1)?.let { cached ->
            return flowOf(SearchStreamState(items = cached.dedupeSearch(), pending = emptyList()))
        }
        val source = SourceHolder.getSource(mode)
        val displayName = if (BackendPrefs.isBackendMode()) {
            "团影视后端"
        } else {
            SourceHolder.getResourceSource(sourceId)?.name ?: mode.name
        }
        val chunkFlow: Flow<SearchChunk> = if (source is RuleAggregateAnimeSource) {
            source.searchChunks(query)
        } else {
            singleSourceChunkFlow(source, query, displayName)
        }
        val expectedNames: List<String> = if (source is RuleAggregateAnimeSource) {
            SourceRuleRepository.getAll()
                .filter { RuleExecutor.supportsCurrentPlayer(it) }
                .map { it.name.ifBlank { it.search.baseUrl } }
        } else {
            listOf(displayName)
        }
        return chunkFlow
            .scan(
                expectedNames.associateWith { emptyList<AnimeBean>() } to emptySet<String>()
            ) { (map, done), chunk ->
                val newMap = map + (chunk.name to chunk.items)
                val newDone = if (chunk.done) done + chunk.name else done
                newMap to newDone
            }
            .map { (map, done) ->
                SearchStreamState(
                    items = map.values.flatten().dedupeSearch(),
                    pending = map.keys.filter { it !in done },
                )
            }
            .onEach { state ->
                // 全部源到齐且非空时写入缓存（流式最后一条即全集），空结果不缓存
                if (state.pending.isEmpty() && state.items.isNotEmpty()) {
                    SearchResultCache.put(query, sourceId, 1, state.items)
                }
            }
    }

    /** 单数据源（内置/单条 CSS 规则源）的 chunk 流：先发 pending，抓取完成后发 done。 */
    private fun singleSourceChunkFlow(
        source: AnimeSource,
        query: String,
        name: String,
    ): Flow<SearchChunk> = flow {
        emit(SearchChunk(name, emptyList(), done = false))
        val items = runCatching { source.getSearchData(query, 1) }.getOrDefault(emptyList())
        emit(SearchChunk(name, items, done = true))
    }

    override suspend fun getFilterData(
        zoneId: Int,
        tag: String?,
        year: Int?,
        orderBy: String?,
        page: Int,
        mode: SourceMode,
    ): List<AnimeBean> {
        val animeSource = SourceHolder.getSource(mode)
        return animeSource.getFilterData(zoneId, tag, year, orderBy, page)
    }

    override suspend fun getWeekDate(): Map<Int, List<AnimeBean>> {
        val animeSource = SourceHolder.currentSource
        return animeSource.getWeekData()
    }

    override suspend fun getRanking(): Map<String, List<AnimeBean>> {
        val animeSource = SourceHolder.currentSource
        return animeSource.getRanking()
    }
}
