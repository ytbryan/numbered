package com.numbered.app.security

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.numbered.app.appContainer
import com.numbered.app.ui.theme.NumberedTheme
import com.numbered.app.ui.theme.isDark
import kotlinx.coroutines.launch

/** Every app entry point uses the same gate, including Android sharing and widget deep links. */
abstract class LockedActivity : FragmentActivity() {
    private val session by viewModels<AppLockSession> { viewModelFactory { initializer { AppLockSession(appContainer.appLock) } } }
    private lateinit var authentication: SystemAuthentication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatePrivacy(appContainer.appLock.enabled.value)
        authentication = SystemAuthentication(this, session::result)
        lifecycleScope.launch { appContainer.appLock.enabled.collect { updatePrivacy(it) } }
    }

    override fun onResume() {
        super.onResume()
        if (appContainer.appLock.enabled.value && !session.state.value.verified && !session.attempted) request(LockOperation.Unlock)
    }

    override fun onStop() {
        if (!isChangingConfigurations) session.background()
        super.onStop()
    }

    private fun updatePrivacy(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun request(operation: LockOperation) {
        if (!session.begin(operation)) return
        val unavailable = authentication.availability()
        if (unavailable != null) session.result(unavailable)
        else authentication.authenticate()
    }

    protected fun setLockedContent(content: @Composable () -> Unit) {
        setContent {
            val theme by appContainer.themes.selection.collectAsState()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !theme.isDark
                    isAppearanceLightNavigationBars = !theme.isDark
                }
            }
            NumberedTheme(theme) {
                // The gate must react even while stopped; private content is never behind a lock overlay.
                val enabled by appContainer.appLock.enabled.collectAsState()
                val state by session.state.collectAsState()
                val savedContent = rememberSaveableStateHolder()
                if (enabled && !state.verified) {
                    LockedScreen(
                        busy = state.operation != null,
                        message = state.message,
                        onUnlock = { request(LockOperation.Unlock) },
                        onSettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    )
                } else {
                    CompositionLocalProvider(LocalLockControls provides LockControls(enabled, state.operation != null, state.message) {
                        request(if (it) LockOperation.Enable else LockOperation.Disable)
                    }) {
                        savedContent.SaveableStateProvider("content") { content() }
                    }
                }
            }
        }
    }
}
