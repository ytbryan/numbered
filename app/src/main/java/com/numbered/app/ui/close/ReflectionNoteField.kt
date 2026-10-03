package com.numbered.app.ui.close

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.numbered.app.R
import com.numbered.app.ui.components.ExpandedTextEditor
import com.numbered.app.ui.components.ExpandTextButton
import java.time.LocalDate

private val ReflectionQuestions = listOf(
    R.string.reflection_prompt_good,
    R.string.reflection_prompt_learned,
    R.string.reflection_prompt_change,
)

/** Inspiration stays separate from the person's note, and is shown only when requested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReflectionNoteField(
    weekStart: LocalDate,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String? = null,
) {
    var selected by rememberSaveable(weekStart) { mutableStateOf<Int?>(null) }
    var choosing by rememberSaveable(weekStart) { mutableStateOf(false) }
    var expanded by rememberSaveable(weekStart) { mutableStateOf(false) }
    val prompt = selected?.let { stringResource(ReflectionQuestions[it]) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        label = label?.let { text -> { Text(text) } },
        supportingText = if (prompt == null) null else { { Text(prompt) } },
        trailingIcon = {
            Row {
                IconButton(onClick = { choosing = true }) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = stringResource(R.string.reflection_prompt_action))
                }
                ExpandTextButton { expanded = true }
            }
        },
        maxLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    if (expanded) ExpandedTextEditor(
        title = label ?: stringResource(R.string.close_note_label),
        value = value,
        onValueChange = onValueChange,
        onClose = { expanded = false },
    )
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.reflection_prompt_title)) },
        text = {
            Column {
                ReflectionQuestions.forEachIndexed { index, question ->
                    TextButton(
                        onClick = { selected = index; choosing = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(question), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        dismissButton = {
            if (selected != null) TextButton(onClick = { selected = null; choosing = false }) {
                Text(stringResource(R.string.reflection_prompt_hide))
            }
        },
    )
}
