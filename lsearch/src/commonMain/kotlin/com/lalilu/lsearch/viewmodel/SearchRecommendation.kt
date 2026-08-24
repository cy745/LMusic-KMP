package com.lalilu.lsearch.viewmodel

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/** 推荐词来源类型。替换时保持类型不变，从而始终维持歌手、专辑、歌曲的 3:3:2 比例。 */
@Immutable
enum class SearchRecommendationType {
    Artist,
    Album,
    Audio,
}

/** 一个可展示的搜索推荐词。 */
@Immutable
data class SearchRecommendation(
    val type: SearchRecommendationType,
    val keyword: String,
)

/**
 * 当前聚合搜索页实例所展示的推荐词快照。
 *
 * 该状态由 [SearchVM] 组合持有，生命周期与导航栈中的聚合搜索页实例一致：跳转到其他页面再
 * 返回时继续使用原来的推荐词；只有聚合搜索页真正出栈、对应 ViewModel 被销毁后，下一次进入
 * 页面才会重新随机生成一组推荐词。
 */
internal class SearchRecommendationState {
    private val _recommendations = MutableStateFlow(emptyList<SearchRecommendation>())

    val recommendations: StateFlow<List<SearchRecommendation>> =
        _recommendations.asStateFlow()

    /** 候选池首次可用时生成推荐词；已有快照时保持不变。 */
    fun initialize(candidates: SearchRecommendationCandidates) {
        _recommendations.update { current ->
            current.ifEmpty { candidates.initialRecommendations() }
        }
    }

    /** 替换一个完成倒计时的胶囊，并忽略已经失效的下标。 */
    fun replace(index: Int, recommendation: SearchRecommendation) {
        _recommendations.update { current ->
            if (index !in current.indices) return@update current

            current.toMutableList().also { items ->
                items[index] = recommendation
            }
        }
    }

    /** 提供给定时协程读取的同步快照。 */
    fun current(): List<SearchRecommendation> = _recommendations.value
}

/**
 * 三类推荐词候选池。
 *
 * 候选名称在创建时会去除首尾空白、过滤空名称，并在各自类别内忽略大小写去重。
 */
@Immutable
data class SearchRecommendationCandidates(
    val artistNames: List<String> = emptyList(),
    val albumNames: List<String> = emptyList(),
    val audioNames: List<String> = emptyList(),
) {
    companion object {
        val Empty = SearchRecommendationCandidates()

        fun create(
            artistNames: List<String>,
            albumNames: List<String>,
            audioNames: List<String>,
        ) = SearchRecommendationCandidates(
            artistNames = artistNames.normalizedNames(),
            albumNames = albumNames.normalizedNames(),
            audioNames = audioNames.normalizedNames(),
        )
    }

    /**
     * 随机生成首屏推荐词：歌手 3 个、专辑 3 个、歌曲 2 个，最后统一打乱展示顺序。
     *
     * 优先避免跨类型重名；若某一类型只有与其他类型重名的候选，则允许重名以尽量满足数量。
     * 当某类候选本身不足时，只展示实际可取到的数量。
     */
    fun initialRecommendations(random: Random = Random.Default): List<SearchRecommendation> {
        val usedKeywords = mutableSetOf<String>()

        return buildList {
            addRecommendations(
                type = SearchRecommendationType.Artist,
                candidates = artistNames,
                count = 3,
                usedKeywords = usedKeywords,
                random = random,
            )
            addRecommendations(
                type = SearchRecommendationType.Album,
                candidates = albumNames,
                count = 3,
                usedKeywords = usedKeywords,
                random = random,
            )
            addRecommendations(
                type = SearchRecommendationType.Audio,
                candidates = audioNames,
                count = 2,
                usedKeywords = usedKeywords,
                random = random,
            )
        }.shuffled(random)
    }

    /**
     * 为 [current] 选择同类型的新词。优先排除当前页面已经展示的词，候选不足时才允许
     * 与其他胶囊重名；不会返回与当前胶囊相同的内容，以确保替换动画有实际变化。
     */
    fun replacementFor(
        current: SearchRecommendation,
        displayed: List<SearchRecommendation>,
        random: Random = Random.Default,
    ): SearchRecommendation? {
        val candidates = namesOf(current.type)
            .filterNot { it.equals(current.keyword, ignoreCase = true) }
        if (candidates.isEmpty()) return null

        val displayedKeywords = displayed
            .asSequence()
            .map { it.keyword.normalizedKey() }
            .toSet()
        val preferred = candidates.filterNot { it.normalizedKey() in displayedKeywords }
        val keyword = (preferred.ifEmpty { candidates }).shuffled(random).first()

        return SearchRecommendation(type = current.type, keyword = keyword)
    }

    private fun namesOf(type: SearchRecommendationType): List<String> = when (type) {
        SearchRecommendationType.Artist -> artistNames
        SearchRecommendationType.Album -> albumNames
        SearchRecommendationType.Audio -> audioNames
    }
}

private fun MutableList<SearchRecommendation>.addRecommendations(
    type: SearchRecommendationType,
    candidates: List<String>,
    count: Int,
    usedKeywords: MutableSet<String>,
    random: Random,
) {
    val shuffled = candidates.shuffled(random)
    val preferred = shuffled.filterNot { it.normalizedKey() in usedKeywords }
    val selected = (preferred + shuffled.filterNot(preferred::contains))
        .distinctBy { it.normalizedKey() }
        .take(count)

    selected.forEach { keyword ->
        add(SearchRecommendation(type = type, keyword = keyword))
        usedKeywords += keyword.normalizedKey()
    }
}

private fun List<String>.normalizedNames(): List<String> = asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::normalizedKey)
    .toList()

private fun String.normalizedKey(): String = lowercase()
