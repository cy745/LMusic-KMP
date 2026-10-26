package com.lalilu.lmusic.prototype

import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises real Media3 preparation/position/seek/end events, not fake engine callbacks. */
class PlaybackCoreDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val a=TrackKey("device-test","A")
    private val b=TrackKey("device-test","B")
    private val c=TrackKey("device-test","C")

    @Test fun debugEntryInitializesWithPausedPreparedAudio() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            android.content.Intent(context, PlaybackPrototypeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        fun texts(view: android.view.View): String = when (view) {
            is android.widget.TextView -> view.text.toString()
            is android.view.ViewGroup -> (0 until view.childCount).joinToString("\n") { texts(view.getChildAt(it)) }
            else -> ""
        }
        try {
            withTimeout(15000) {
                while (true) {
                    var rendered = ""
                    instrumentation.runOnMainSync { rendered = texts(activity.window.decorView) }
                    assertFalse(rendered, rendered.contains("原型初始化失败"))
                    if (rendered.contains("ReadyPaused") && rendered.contains("意图：Pause")) break
                    delay(100)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun scenario(durationMs: Int = 8000, block: suspend (PlaybackCore, String) -> Unit) = runBlocking {
        val directory=File(context.cacheDir,"core-device-${System.nanoTime()}")
        val uri=PrototypeAudio.write(directory,"fixture",durationMs=durationMs).toURI().toString()
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var core: PlaybackCore?=null
        try {
            withContext(Dispatchers.Main) {
                core=PlaybackCore(parent,ContentResolver { key ->
                    if(key==b) delay(150)
                    if(key==c) delay(300)
                    PlayableResource(if(key.id=="missing") File(directory,"missing.wav").toURI().toString() else uri)
                },Media3PrototypeRuntime(context))
            }
            block(core!!,uri)
        } finally {
            withContext(Dispatchers.Main) { core?.close(); parent.cancel() }
            directory.deleteRecursively()
        }
    }
    private suspend fun PlaybackCore.command(command: PlaybackCommand) = withContext(Dispatchers.Main) { dispatch(command) }
    private suspend fun PlaybackCore.await(predicate: (PlaybackSnapshot)->Boolean): PlaybackSnapshot =
        withTimeout(15000) { state.first(predicate) }

    @Test fun preparesSilentlyThenPlaysAndPausesAtItsPosition() = scenario { core,_ ->
        core.command(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.await { it.phase==Phase.ReadyPaused }
        delay(250)
        assertEquals(0L,core.state.value.positionMs)
        core.command(PlaybackCommand.Play)
        core.await { it.phase==Phase.Playing && it.positionMs>100 }
        core.command(PlaybackCommand.Pause)
        delay(250)
        val paused=core.state.value.positionMs
        delay(250)
        assertEquals(PlayIntent.Pause,core.state.value.intent)
        assertEquals(Phase.ReadyPaused,core.state.value.phase)
        assertTrue(kotlin.math.abs(core.state.value.positionMs-paused)<80)
    }

    @Test fun quickSelectionDuringPreparationHonorsPause() = scenario { core,_ ->
        core.command(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.command(PlaybackCommand.Play)
        core.await { it.phase==Phase.Playing }
        core.command(PlaybackCommand.Select(b))
        core.command(PlaybackCommand.Select(c))
        core.command(PlaybackCommand.Pause)
        core.await { it.selected==c && it.phase==Phase.ReadyPaused }
        delay(250)
        assertEquals(c,core.state.value.selected)
        assertEquals(0L,core.state.value.positionMs)
        core.command(PlaybackCommand.Play)
        core.await { it.phase==Phase.Playing && it.positionMs>100 }
        assertEquals(c,core.state.value.selected)
    }

    @Test fun nativeSeekAcknowledgesLatestPositionWhilePaused() = scenario { core,_ ->
        core.command(PlaybackCommand.ReplaceQueue(listOf(a)))
        core.await { it.phase==Phase.ReadyPaused }
        core.command(PlaybackCommand.Seek(2000))
        core.command(PlaybackCommand.Seek(5000))
        val settled=core.await { it.pendingSeekId==null && it.positionMs>=4900 }
        assertEquals(PlayIntent.Pause,settled.intent)
        assertEquals(Phase.ReadyPaused,settled.phase)
        delay(250)
        assertTrue(kotlin.math.abs(core.state.value.positionMs-5000)<100)
    }

    @Test fun naturalSequentialEndStopsAtLastTrackAndManualNextWraps() = scenario(300) { core,_ ->
        core.command(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.command(PlaybackCommand.Play)
        core.await { it.phase==Phase.Ended && it.selected==b }
        assertEquals(PlayIntent.Pause,core.state.value.intent)
        core.command(PlaybackCommand.Next)
        core.await { it.selected==a && it.phase==Phase.ReadyPaused }
    }

    @Test fun nativeLoadFailureUsesSharedFailurePolicy() = scenario { core,_ ->
        val missing=TrackKey("device-test","missing")
        core.command(PlaybackCommand.ReplaceQueue(listOf(missing,a)))
        core.command(PlaybackCommand.Play)
        core.await { it.selected==a && it.phase==Phase.Playing }
        assertNull(core.state.value.error)
        core.command(PlaybackCommand.SetMode(PlayMode.RepeatOne))
        core.command(PlaybackCommand.Select(missing))
        core.await { it.selected==missing && it.phase==Phase.Failed }
        assertEquals(PlayIntent.Pause,core.state.value.intent)
    }
}
