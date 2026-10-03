package com.numbered.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.components.ScreenPadding

internal data class LockControls(val enabled: Boolean, val busy: Boolean, val message: Int?, val onChange: (Boolean) -> Unit)
internal val LocalLockControls = staticCompositionLocalOf<LockControls> { error("App lock host is missing") }

@Composable
internal fun AppLockSetting() {
    val controls = LocalLockControls.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(controls.enabled, enabled = !controls.busy, role = Role.Switch, onValueChange = controls.onChange)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(R.string.lock_setting), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.lock_setting_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = controls.enabled, onCheckedChange = null, enabled = !controls.busy)
        }
        controls.message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun LockedScreen(busy: Boolean, message: Int?, onUnlock: () -> Unit, onSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lock_locked_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.lock_locked_help), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            Button(onClick = onUnlock, enabled = !busy) { Text(stringResource(R.string.lock_unlock)) }
            if (message == R.string.lock_setup_needed) {
                TextButton(onClick = onSettings, enabled = !busy) { Text(stringResource(R.string.lock_android_settings)) }
            }
        }
    }
}
