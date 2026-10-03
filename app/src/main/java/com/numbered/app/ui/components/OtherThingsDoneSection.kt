package com.numbered.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.data.OtherThingDone

/** Completed work outside the week's chosen priorities. */
@Composable
fun OtherThingsDoneSection(
    items: List<OtherThingDone>,
    canAdd: Boolean,
    onAdd: (String) -> Unit,
    onRename: (OtherThingDone, String) -> Unit,
    onRemove: (OtherThingDone) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.other_things_done), style = MaterialTheme.typography.titleSmall)
        items.forEach { item ->
            Surface(
                shape = CardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    OverflowMenu(
                        actions = listOf(
                            MenuAction(stringResource(R.string.action_edit)) { editingId = item.id },
                            MenuAction(stringResource(R.string.action_remove)) { onRemove(item) },
                        ),
                        description = stringResource(R.string.a11y_more_for, item.title),
                    )
                }
            }
        }
        if (canAdd) {
            TextButton(onClick = { adding = true }) { Text(stringResource(R.string.add_other_thing_done)) }
        }
    }
    if (adding) AddOtherThingDoneDialog(onAdd, onDismiss = { adding = false })
    editingId?.let { id ->
        val item = items.firstOrNull { it.id == id }
        if (item == null) editingId = null
        else RenameDialog(item.title, onSave = { onRename(item, it) }, onDismiss = { editingId = null })
    }
}

@Composable
private fun AddOtherThingDoneDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val submit = { onAdd(text); onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_other_thing_done)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.other_thing_done_hint)) },
                singleLine = true,
                trailingIcon = { ExpandTextButton { expanded = true } },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (expanded) ExpandedTextEditor(
        title = stringResource(R.string.add_other_thing_done),
        value = text,
        onValueChange = { text = it },
        onClose = { expanded = false },
        submitLabel = stringResource(R.string.action_add),
        onSubmit = { expanded = false; submit() },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
