package com.lalilu.lmusic.prototype

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.resolveMediaData
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.LPlayer
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.mp.KoinPlatform
import java.io.File

/** Debug-only integration harness. Production UI/session registration remains untouched. */
class PlaybackPrototypeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var core: PlaybackCore? = null
    private lateinit var status: TextView
    private lateinit var actions: LinearLayout
    private val media = mutableMapOf<TrackKey, LAudio>()
    private val fixtures = linkedMapOf<TrackKey, String>()
    private val temporaryMedia = mutableListOf<File>()
    private var content: PrototypeContentAccess? = null
    private val cachedUris = mutableMapOf<TrackKey, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
        }
        status = TextView(this).apply { textSize = 17f; text = "播放核心原型（仅调试）\n正在准备…" }
        layout.addView(status)
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(actions)
        setContentView(ScrollView(this).apply { addView(layout) })
        scope.launch {
            try {
                // Explicit exclusive ownership: do not let the legacy service keep playing.
                LPlayer.instance.pause()
                withContext(Dispatchers.IO) {
                    listOf("A", "B", "C").forEachIndexed { index, name ->
                        fixtures[TrackKey("prototype", name)] = PrototypeAudio.write(File(cacheDir,"playback-core-prototype"),name,220.0+index*110).toURI().toString()
                    }
                }
                val access = withContext(Dispatchers.IO) {
                    PrototypeContentAccess(this@PlaybackPrototypeActivity,
                        KoinPlatform.getKoin().get<PlatformMediaSource>(), KoinPlatform.getKoin().get<AudioRepository>(), "plan-b-debug")
                }
                content = access
                val runtime = Media3PrototypeRuntime(this@PlaybackPrototypeActivity, access.dataSourceFactory)
                val instance = PlaybackCore(scope, ContentResolver { key ->
                    val uri = fixtures[key] ?: cachedUris[key] ?: run {
                        val audio = media[key] ?: error("歌曲已不可用")
                        access.uri(audio)?.let { return@ContentResolver PlayableResource(it) }
                        val data = KoinPlatform.getKoin().get<PlatformMediaSource>().resolveMediaData(audio)
                        when (data) {
                            is MediaData.Url -> data.url
                            is MediaData.Bytes -> withContext(Dispatchers.IO) {
                                File.createTempFile("prototype-bytes-", ".audio", cacheDir).apply {
                                    writeBytes(data.bytes)
                                    synchronized(temporaryMedia) { temporaryMedia.add(this) }
                                }.toURI().toString()
                            }
                        }
                    }
                    PlayableResource(uri)
                },runtime)
                core = instance
                createControls()
                instance.dispatch(PlaybackCommand.ReplaceQueue(fixtures.keys.toList()))
                instance.state.collect { s ->
                    status.text = "播放核心原型（仅调试）\n" +
                        "当前：${s.selected?.let { media[it]?.title ?: it.id } ?: "无"}  ${s.phase}\n" +
                        "意图：${s.intent}  模式：${s.mode}\n" +
                        "进度：${s.positionMs} / ${s.durationMs} ms\n" +
                        "队列：${s.queue.joinToString { it.id.take(12) }}\n" +
                        "下一首意图：${s.explicitNext?.id ?: "无"}\n" +
                        (s.error?.let { "错误：$it\n" } ?: "") +
                        "独立前台验证入口；未替换正式播放器。"
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                status.text = "原型初始化失败：${failure.message}"
            }
        }
    }

    private fun createControls() {
        button("播放") { dispatch(PlaybackCommand.Play) }
        button("暂停") { dispatch(PlaybackCommand.Pause) }
        button("上一首") { dispatch(PlaybackCommand.Previous) }
        button("下一首") { dispatch(PlaybackCommand.Next) }
        fixtures.keys.forEach { key -> button("选择 ${key.id}（保留播放意图）") { dispatch(PlaybackCommand.Select(key)) } }
        button("快速 A → B → C") {
            scope.launch { fixtures.keys.forEach { core?.dispatch(PlaybackCommand.Select(it)) } }
        }
        button("定位到中间") { dispatch(PlaybackCommand.Seek((core?.state?.value?.durationMs ?: 0) / 2)) }
        button("切换播放模式") {
            val old = core?.state?.value?.mode ?: PlayMode.Sequential
            dispatch(PlaybackCommand.SetMode(PlayMode.entries[(old.ordinal + 1) % PlayMode.entries.size]))
        }
        button("将 C 设为下一首") { dispatch(PlaybackCommand.PlayNext(TrackKey("prototype","C"))) }
        button("载入媒体库前 20 首（保持暂停）") {
            scope.launch {
                try {
                    core?.dispatch(PlaybackCommand.Pause)
                    val audios = KoinPlatform.getKoin().get<AudioRepository>().getAudios().first().filter { it.available }.take(20)
                    content?.remember(audios)
                    cachedUris.clear()
                    audios.forEach { media[TrackKey(it.mediaSourceName,it.id)] = it }
                    core?.dispatch(PlaybackCommand.ReplaceQueue(audios.map { TrackKey(it.mediaSourceName,it.id) }))
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    status.append("\n媒体库读取失败：${failure.message}")
                }
            }
        }
        button("读取缓存目录（不等待数据源，保持暂停）") {
            scope.launch {
                try {
                    core?.dispatch(PlaybackCommand.Pause)
                    val saved = content?.cachedSongs().orEmpty()
                    cachedUris.clear()
                    saved.forEach { (audio, uri) ->
                        val key = TrackKey(audio.mediaSourceName,audio.id)
                        media[key] = audio; cachedUris[key] = uri
                    }
                    core?.dispatch(PlaybackCommand.ReplaceQueue(saved.map { TrackKey(it.first.mediaSourceName,it.first.id) }))
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    status.append("\n缓存目录读取失败：${failure.message}")
                }
            }
        }
        button("返回现有播放器") { finish() }
    }
    private fun dispatch(command: PlaybackCommand) {
        scope.launch {
            try { core?.dispatch(command) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                status.append("\n操作失败：${failure.message}")
            }
        }
    }
    private fun button(label: String, action: () -> Unit) {
        actions.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }
    override fun onStop() {
        // This prototype intentionally does not claim a background media service.
        dispatch(PlaybackCommand.Pause)
        super.onStop()
    }
    override fun onDestroy() {
        core?.close()
        content?.close()
        scope.cancel()
        synchronized(temporaryMedia) { temporaryMedia.forEach { it.delete() }; temporaryMedia.clear() }
        super.onDestroy()
    }
}
