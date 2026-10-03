package com.numbered.app.security

import android.app.KeyguardManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.numbered.app.R

internal enum class AuthenticationResult { Success, Cancelled, NoDeviceLock, Unavailable }
/** Android owns the fingerprint and screen-lock UI; the app never receives a PIN or fingerprint. */
internal class SystemAuthentication(
    private val activity: FragmentActivity,
    result: (AuthenticationResult) -> Unit,
) {
    private val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(authentication: BiometricPrompt.AuthenticationResult) {
            result(AuthenticationResult.Success)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            result(if (errorCode in CancelledErrors) AuthenticationResult.Cancelled else AuthenticationResult.Unavailable)
        }
        // A failed fingerprint match is not terminal. Android keeps the prompt open for another try.
    })

    fun availability(): AuthenticationResult? {
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) return AuthenticationResult.NoDeviceLock
        return if (BiometricManager.from(activity).canAuthenticate(Authenticators) == BiometricManager.BIOMETRIC_SUCCESS) null
        else AuthenticationResult.Unavailable
    }

    fun authenticate() {
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_auth_title))
            .setAllowedAuthenticators(Authenticators)
            .build())
    }

    internal companion object {
        // WEAK includes stronger biometrics and supports credential fallback on Android 8-10 too.
        const val Authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        private val CancelledErrors = setOf(BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON)
    }
}
