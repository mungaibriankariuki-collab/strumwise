package com.strumwise.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.strumwise.music.ChordLibrary

/** Lets the player put any chord from the library into a slot of the chord column. */
@Composable
fun ChordPickerDialog(currentChordId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap chord") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            ) {
                items(ChordLibrary.ALL, key = { it.id }) { chord ->
                    FilterChip(
                        selected = chord.id == currentChordId,
                        onClick = { onPick(chord.id) },
                        label = { Text(chord.displayName) },
                        modifier = Modifier.semantics { contentDescription = "Use ${chord.displayName}, shape ${chord.shape()}" },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
