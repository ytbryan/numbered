package com.numbered.app

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.numbered.app.data.PlanResult
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.theme.NumberedTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A separate screen returns to the sharing app after saving, including before initial setup. */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = sharedIdea(intent)
        setContent {
            NumberedTheme {
                CaptureScreen(
                    initialText = shared,
                    onCancel = { finish() },
                    onSaved = {
                        Toast.makeText(this, R.string.capture_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }
}

internal fun sharedIdea(intent: Intent): String {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return ""
    return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
}

internal data class CaptureState(val saving: Boolean = false, val saved: Boolean = false, val failed: Boolean = false)

internal class CaptureViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureState())
    val state = mutableState.asStateFlow()

    fun save(text: String) {
        if (text.isBlank() || state.value.saving || state.value.saved) return
        mutableState.value = CaptureState(saving = true)
        viewModelScope.launch {
            try {
                val result = container.repository.addSomeday(text)
                mutableState.value = CaptureState(saved = result == PlanResult.Ok)
            } catch (_: SQLiteException) {
                mutableState.value = CaptureState(failed = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureScreen(initialText: String, onCancel: () -> Unit, onSaved: () -> Unit) {
    val viewModel = containerViewModel { CaptureViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(initialText) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    BackHandler(enabled = state.saving) { /* Wait for the save before returning to the sharing app. */ }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.capture_idea)) },
                minLines = 4,
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.failed) Text(stringResource(R.string.capture_error), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { viewModel.save(text) },
                enabled = text.isNotBlank() && !state.saving && !state.saved,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
