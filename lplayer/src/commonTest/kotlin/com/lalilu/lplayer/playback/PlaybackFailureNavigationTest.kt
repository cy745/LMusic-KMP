package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 导航跳过规则：方向、预算、队列变化与"只记一次原始失败"。 */
class PlaybackFailureNavigationTest {
    private val a = LAudio(id = "a", mediaSourceName = "local")
    private val b = LAudio(id = "b", mediaSourceName = "local")
    private val c = LAudio(id = "c", mediaSourceName = "local")
    private val d = LAudio(id = "d", mediaSourceName = "local")
    private val queue = listOf(a, b, c)

    private fun recordedFailures(ids: Set<String>) = ids

    @Test fun successfulAttemptDoesNotSkipOrRecordAnything() = runTest {
        val attempts = mutableListOf<Int>()
        val recorded = mutableListOf<String>()
        val index = navigate(
            initialIndex = 1,
            playable = recordedFailures(emptySet()),
            recorded = recorded,
            attempt = { target ->
                attempts += target
                if (target != 1) error("should not be attempted")
            },
        )
        assertEquals(1, index)
        assertEquals(listOf(1), attempts)
        assertTrue(recorded.isEmpty())
    }

    @Test fun failedLoadSkipsForwardAndRecordsEveryBrokenSongOnce() = runTest {
        val attempts = mutableListOf<Int>()
        val recorded = mutableListOf<String>()
        val index = navigate(
            initialIndex = 0,
            playable = recordedFailures(emptySet()),
            recorded = recorded,
            attempt = { target ->
                attempts += target
                if (target != 2) error("broken ${queue[target].id}")
            },
        )
        assertEquals(2, index)
        assertEquals(listOf(0, 1, 2), attempts)
        assertEquals(listOf("a", "b"), recorded)
    }

    @Test fun backwardDirectionSkipsToPreviousSlots() = runTest {
        val attempts = mutableListOf<Int>()
        val index = navigate(
            initialIndex = 2,
            direction = PlaybackDirection.Backward,
            playable = recordedFailures(emptySet()),
            recorded = mutableListOf(),
            attempt = { target ->
                attempts += target
                if (target != 0) error("broken")
            },
        )
        assertEquals(0, index)
        assertEquals(listOf(2, 1, 0), attempts)
    }

    @Test fun alreadyFailedSongsAreNotAttemptedAgain() = runTest {
        val attempts = mutableListOf<Int>()
        val index = navigate(
            initialIndex = 0,
            playable = recordedFailures(setOf(b.playbackId)),
            recorded = mutableListOf(),
            attempt = { target ->
                attempts += target
                if (target != 2) error("broken")
            },
        )
        assertEquals(2, index)
        assertEquals(listOf(0, 2), attempts)
    }

    @Test fun exhaustedBudgetStopsAndRethrowsTheFirstFailure() = runTest {
        val attempts = mutableListOf<Int>()
        var stopped = 0
        val failure = assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                stop = { stopped++ },
                attempt = { target ->
                    attempts += target
                    error("broken ${queue[target].id}")
                },
            )
        }
        assertEquals(listOf(0, 1, 2), attempts)
        assertEquals(1, stopped)
        assertEquals("broken a", failure.message)
    }

    @Test fun aFailingStopDoesNotReplaceTheOriginalFailure() = runTest {
        val stopFailure = IllegalStateException("native stop failed")
        val failure = assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                stop = { throw stopFailure },
                attempt = { target -> error("broken ${queue[target].id}") },
            )
        }
        assertEquals("broken a", failure.message)
        assertEquals(listOf<Throwable>(stopFailure), failure.suppressedExceptions)
    }

    @Test fun aFailedAttemptThatWasNotAllowedToSkipIsReportedDirectly() = runTest {
        val attempts = mutableListOf<Int>()
        var stopped = 0
        assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 1,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                skipPolicy = { it.id != "b" },
                stop = { stopped++ },
                attempt = { target ->
                    attempts += target
                    error("play failed after load")
                },
            )
        }
        assertEquals(listOf(1), attempts)
        assertEquals(0, stopped)
    }

    @Test fun queueReplacementDuringNavigationAbortsInsteadOfSkippingOn() = runTest {
        var current = queue
        val attempts = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                candidates = { current },
                attempt = { target ->
                    attempts += target
                    current = listOf(a)
                    error("broken")
                },
            )
        }
        assertEquals(listOf(0), attempts)
    }

    @Test fun cancellationIsNeverRecordedAsAFailure() = runTest {
        val recorded = mutableListOf<String>()
        assertFailsWith<CancellationException> {
            navigate(
                initialIndex = 1,
                playable = recordedFailures(emptySet()),
                recorded = recorded,
                attempt = { throw CancellationException("replaced") },
            )
        }
        assertTrue(recorded.isEmpty())
    }

    @Test fun slotsWithUnreadyOrAlreadyFailedSourcesAreNotAttemptedWhileSkipping() = runTest {
        val attempts = mutableListOf<Int>()
        val index = navigate(
            initialIndex = 0,
            playable = recordedFailures(setOf(b.playbackId)),
            recorded = mutableListOf(),
            attempt = { target ->
                attempts += target
                if (target == 0) error("broken a")
            },
        )
        assertEquals(2, index)
        assertEquals(listOf(0, 2), attempts)
    }

    @Test fun anExhaustedTimeBudgetStopsInsteadOfTryingEverySlot() = runTest {
        val attempts = mutableListOf<Int>()
        var stopped = 0
        val failure = assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                stop = { stopped++ },
                outOfBudget = { attempts.size >= 1 },
                attempt = { target ->
                    attempts += target
                    error("broken ${queue[target].id}")
                },
            )
        }
        // 只尝试了首曲：总时长上限到点后不再继续占着队列编辑锁。
        assertEquals(listOf(0), attempts)
        assertEquals(1, stopped)
        assertEquals("broken a", failure.message)
        assertTrue(failure.suppressedExceptions.any { it.message?.contains("budget exhausted") == true })
    }

    @Test fun aBudgetThatNeverTripsStillUsesTheWholeQueueBudget() = runTest {
        val attempts = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                outOfBudget = { false },
                attempt = { target ->
                    attempts += target
                    error("broken")
                },
            )
        }
        assertEquals(queue.size, attempts.size)
    }

    @Test fun queueReorderedWhileResolvingPlayableSlotsAbortsTheSkip() = runTest {
        // 同长度重排不会越界，只靠顶部下标检查兜不住：旧下标会被套到新列表的别的槽位上。
        var current = queue + d
        val attempts = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 1,
                candidates = { current },
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                playableSlotsDelay = { current = listOf(d, c, b, a) },
                attempt = { target ->
                    attempts += target
                    error("broken ${current[target].id}")
                },
            )
        }
        assertEquals(listOf(1), attempts)
    }

    @Test fun singleItemQueueStopsWithoutRepeatingTheSameSlot() = runTest {
        val only = listOf(a)
        val attempts = mutableListOf<Int>()
        var stopped = 0
        assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                candidates = { only },
                stop = { stopped++ },
                attempt = { target ->
                    attempts += target
                    error("broken")
                },
            )
        }
        assertEquals(listOf(0), attempts)
        assertEquals(1, stopped)
    }

    @Test fun emptyQueueOrOutOfRangeIndexIsReportedWithoutAttempting() = runTest {
        val attempts = mutableListOf<Int>()
        assertFailsWith<IllegalArgumentException> {
            navigate(
                initialIndex = 0,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                candidates = { emptyList() },
                attempt = { target -> attempts += target },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            navigate(
                initialIndex = 9,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                attempt = { target -> attempts += target },
            )
        }
        assertTrue(attempts.isEmpty())
    }

    @Test fun shuffleFallbackStillVisitsEverySlotAtMostOnce() = runTest {
        val attempts = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            navigate(
                initialIndex = 0,
                mode = PlaybackMode.SHUFFLE,
                playable = recordedFailures(emptySet()),
                recorded = mutableListOf(),
                attempt = { target ->
                    attempts += target
                    error("broken")
                },
            )
        }
        assertEquals(queue.size, attempts.size)
        assertEquals(queue.indices.toSet(), attempts.toSet())
    }

    private suspend fun navigate(
        initialIndex: Int,
        direction: PlaybackDirection = PlaybackDirection.Forward,
        mode: PlaybackMode = PlaybackMode.LOOP,
        playable: Set<String>,
        recorded: MutableList<String>,
        candidates: () -> List<LAudio> = { queue },
        playableSlotsDelay: suspend () -> Unit = {},
        skipPolicy: (LAudio) -> Boolean = { true },
        stop: suspend () -> Unit = {},
        outOfBudget: () -> Boolean = { false },
        attempt: suspend (Int) -> Unit,
    ): Int = navigateWithFailureFallback(
        traversal = PlaybackFailureTraversal(),
        initialIndex = initialIndex,
        direction = direction,
        playbackMode = { mode },
        candidates = candidates,
        playableSlots = { list ->
            playableSlotsDelay()
            list.indices.filter { list[it].playbackId !in playable }.toSet()
        },
        skipPolicy = skipPolicy,
        recordFailure = { item, error ->
            assertFalse(error is CancellationException)
            recorded += item.id
        },
        stop = stop,
        attempt = { target, _ -> attempt(target) },
        outOfBudget = outOfBudget,
    )
}
