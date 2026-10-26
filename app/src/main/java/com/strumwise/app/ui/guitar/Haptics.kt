package com.strumwise.app.ui.guitar

import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One light tick per string hit, timed to the strum spacing. */
fun View.tickForNotes(notes: List<PlayedNote>, scope: CoroutineScope) {
    for (note in notes) {
        if (note.delayMs <= 0f) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            scope.launch {
                delay(note.delayMs.toLong())
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }
}
