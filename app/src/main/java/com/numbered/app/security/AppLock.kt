package com.numbered.app.security

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import com.numbered.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local preference. Authentication and unlocked sessions are never persisted. */
class AppLock(context: Context) {
    private val preferences = context.getSharedPreferences("app-lock", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    val enabled = mutableEnabled.asStateFlow()

    // KTX edit discards the commit result; only publish a change that was saved successfully.
    @SuppressLint("UseKtx")
    internal fun setEnabled(value: Boolean): Boolean {
        if (!preferences.edit().putBoolean("enabled", value).commit()) return false
        mutableEnabled.value = value
        return true
    }
}

internal enum class LockOperation { Unlock, Enable, Disable }
internal data class LockSession(
    val verified: Boolean = false,
    val operation: LockOperation? = null,
    val message: Int? = null,
)

/** Retained across rotation, but neither across a background stop nor process death. */
internal class AppLockSession(private val lock: AppLock) : ViewModel() {
    private val mutableState = MutableStateFlow(LockSession())
    val state = mutableState.asStateFlow()
    var attempted = false
        private set

    fun begin(operation: LockOperation): Boolean {
        if (state.value.operation != null) return false
        attempted = true
        mutableState.value = state.value.copy(operation = operation, message = null)
        return true
    }

    fun result(result: AuthenticationResult) {
        val operation = state.value.operation ?: return
        val previous = state.value.copy(operation = null)
        mutableState.value = when (result) {
            AuthenticationResult.Success -> {
                val stored = when (operation) {
                    LockOperation.Unlock -> true
                    LockOperation.Enable -> lock.setEnabled(true)
                    LockOperation.Disable -> lock.setEnabled(false)
                }
                if (stored) previous.copy(verified = true, message = null)
                else previous.copy(message = R.string.lock_save_failed)
            }
            AuthenticationResult.Cancelled -> previous
            AuthenticationResult.NoDeviceLock -> previous.copy(message = R.string.lock_setup_needed)
            AuthenticationResult.Unavailable -> previous.copy(message = R.string.lock_unavailable)
        }
    }

    fun background() {
        if (lock.enabled.value) {
            mutableState.value = state.value.copy(verified = false)
            attempted = false
        }
    }
}
