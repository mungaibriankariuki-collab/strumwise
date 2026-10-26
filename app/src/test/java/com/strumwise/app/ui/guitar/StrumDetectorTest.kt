package com.strumwise.app.ui.guitar

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class StrumDetectorTest {
    private val detector = StrumDetector()

    @Before fun setUp() {
        detector.stringYs = floatArrayOf(100f, 150f, 200f, 250f, 300f, 350f)
        detector.density = 1f
    }

    @Test fun fastDownStrumInOneFrameHitsAllStringsLowToHigh() {
        detector.down(1, 50f, 0)
        val hits = detector.move(1, 400f, 16)
        assertThat(hits.map { it.string }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
        assertThat(hits.all { it.direction == StrumDirection.DOWN }).isTrue()
    }

    @Test fun upStrumReversesOrder() {
        detector.down(1, 400f, 0)
        val hits = detector.move(1, 50f, 16)
        assertThat(hits.map { it.string }).containsExactly(5, 4, 3, 2, 1, 0).inOrder()
        assertThat(hits.all { it.direction == StrumDirection.UP }).isTrue()
    }

    @Test fun strumSpacingIsBetween6And12ms() {
        detector.down(1, 50f, 0)
        val hits = detector.move(1, 400f, 16)
        assertThat(hits.first().delayMs).isEqualTo(0f)
        hits.zipWithNext { a, b ->
            val gap = b.delayMs - a.delayMs
            assertThat(gap).isAtLeast(6f)
            assertThat(gap).isAtMost(12f)
        }
    }

    @Test fun fasterSwipeIsLouderAndTighter() {
        detector.down(1, 50f, 0)
        val fast = detector.move(1, 400f, 10)
        detector.up(1)
        detector.down(2, 50f, 0)
        val slow = detector.move(2, 400f, 600)
        assertThat(fast.first().velocity).isGreaterThan(slow.first().velocity)
        assertThat(fast[1].delayMs).isLessThan(slow[1].delayMs)
    }

    @Test fun slowStrumAcrossManyEventsHitsEachStringOnce() {
        detector.down(1, 60f, 0)
        val all = mutableListOf<StringHit>()
        var y = 60f
        var t = 0L
        while (y < 380f) {
            y += 10f
            t += 16
            all += detector.move(1, y, t)
        }
        assertThat(all.map { it.string }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
        assertThat(all.all { it.delayMs == 0f }).isTrue()
    }

    @Test fun skippedFramesAreInterpolated() {
        // A swipe from above string 1 to below string 4 in a single event still hits 1..4.
        detector.down(1, 120f, 0)
        val hits = detector.move(1, 320f, 16)
        assertThat(hits.map { it.string }).containsExactly(1, 2, 3, 4).inOrder()
    }

    @Test fun tapOnStringPlucksIt() {
        val hits = detector.down(1, 203f, 0)
        assertThat(hits).hasSize(1)
        assertThat(hits[0].string).isEqualTo(2)
        assertThat(hits[0].direction).isNull()
    }

    @Test fun tapBetweenStringsDoesNothing() {
        assertThat(detector.down(1, 125f, 0)).isEmpty()
    }

    @Test fun jitterOnAStringDoesNotRetrigger() {
        val pluck = detector.down(1, 195f, 0)
        assertThat(pluck.map { it.string }).containsExactly(2)
        // Wobbling across the string just plucked, even slowly, stays silent.
        assertThat(detector.move(1, 205f, 100)).isEmpty()
        assertThat(detector.move(1, 195f, 300)).isEmpty()
        assertThat(detector.move(1, 205f, 500)).isEmpty()
    }

    @Test fun movingClearAndComingBackRetriggers() {
        detector.down(1, 180f, 0)
        assertThat(detector.move(1, 220f, 16).map { it.string }).containsExactly(2)
        assertThat(detector.move(1, 180f, 32).map { it.string }).containsExactly(2)
    }

    @Test fun multiTouchPointersAreIndependent() {
        detector.down(1, 50f, 0)
        detector.down(2, 400f, 0)
        val a = detector.move(1, 175f, 16)
        val b = detector.move(2, 225f, 16)
        assertThat(a.map { it.string }).containsExactly(0, 1).inOrder()
        assertThat(b.map { it.string }).containsExactly(5, 4, 3).inOrder()
    }

    @Test fun moveWithoutDownIsIgnored() {
        assertThat(detector.move(9, 400f, 16)).isEmpty()
    }

    @Test fun velocityCurve() {
        assertThat(StrumDetector.velocityFor(0f)).isEqualTo(StrumDetector.MIN_VELOCITY)
        assertThat(StrumDetector.velocityFor(100f)).isEqualTo(1f)
        assertThat(StrumDetector.velocityFor(1f)).isGreaterThan(StrumDetector.velocityFor(0.5f))
    }
}
