package com.lalilu.lplayer.components

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope

/**
 * 封面加载重试节奏的单元验证。
 *
 * 这里只关心两件事：还没拿到图时该不该继续重试，以及重试是否会在加载超时窗口内收敛。
 * 真实请求由 Coil 负责，与重试策略无关。
 */
class CoverRetryTest {

    /** 用测试调度器的虚拟时钟驱动重试：真实等待时长与窗口判断是同一套逻辑。 */
    private suspend fun TestScope.retry(
        deadline: Long,
        attempts: (attempt: Int) -> String,
    ): Pair<String, Int> {
        var count = 0
        val result = retryUntilLoaded(
            deadlineMillis = deadline,
            initialDelayMillis = 100,
            maxDelayMillis = 400,
            nowMillis = { testScheduler.currentTime },
            isLoaded = { it == "ok" },
            attempt = {
                count += 1
                attempts(count)
            },
        )
        return result to count
    }

    @Test
    fun retriesUntilAnImageArrives() = runTest {
        val (result, count) = retry(deadline = 5_000L) { attempt ->
            // 前两次是"暂时还没有"：数据源正在提取元数据/下载边车封面
            if (attempt <= 2) "fail" else "ok"
        }

        assertEquals("ok", result, "重试期间拿到的图必须被采用")
        assertEquals(3, count, "失败两次后第三次成功，应恰好请求三次")
    }

    @Test
    fun givesUpOnceTheDeadlineIsReached() = runTest {
        val (result, count) = retry(deadline = 1_000L) { "fail" }

        assertEquals("fail", result)
        assertEquals(4, count, "100/200/400ms 三次退避后，再等一次就会越过窗口，应停止")
        assertTrue(
            testScheduler.currentTime <= 1_000L,
            "重试不能越过加载窗口，实际用时 ${testScheduler.currentTime}ms",
        )
    }

    @Test
    fun stopsBeforeAGapThatWouldOverrunTheDeadline() = runTest {
        val (_, count) = retry(deadline = 300L) { "fail" }

        // 首延迟 100ms、退避 200ms 已经跨过 300ms 窗口：只允许尝试两次
        assertTrue(count <= 3, "不应在窗口外继续重试，实际 $count 次")
    }
}
