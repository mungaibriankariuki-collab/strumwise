package com.strumwise.app.ui.guitar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuitarLayoutTest {
    private val layout = GuitarLayout(2000f, 1000f)

    @Test fun stringsAreEvenlySpacedTopToBottom() {
        layout.stringYs.toList().zipWithNext { a, b -> assertThat(b - a).isWithin(1e-3f).of(layout.stringSpacing) }
        assertThat(layout.stringYs.first()).isLessThan(layout.stringYs.last())
    }

    @Test fun fretsShrinkTowardTheBody() {
        val gaps = layout.fretXs.toList().zipWithNext { a, b -> b - a }
        gaps.zipWithNext { a, b -> assertThat(b).isLessThan(a) }
        assertThat(layout.fretXs.last()).isWithin(1e-3f).of(layout.fretboardEndX)
    }

    @Test fun fretHitTest() {
        assertThat(layout.fretAt(layout.nutX - 1f)).isEqualTo(0)
        for (f in 1..GuitarLayout.VISIBLE_FRETS) {
            assertThat(layout.fretAt(layout.fretCenterX(f))).isEqualTo(f)
        }
        assertThat(layout.fretAt(layout.fretboardEndX + 10f)).isNull()
    }

    @Test fun mirroring() {
        assertThat(layout.mirrorX(100f, leftHanded = false)).isEqualTo(100f)
        assertThat(layout.mirrorX(100f, leftHanded = true)).isEqualTo(1900f)
    }

    @Test fun landmarksAreInOrder() {
        assertThat(layout.nutX).isLessThan(layout.fretboardEndX)
        assertThat(layout.fretboardEndX).isLessThan(layout.hitLineX)
        assertThat(layout.hitLineX).isLessThan(layout.soundHoleX)
        assertThat(layout.soundHoleX).isLessThan(layout.bridgeX)
    }
}
