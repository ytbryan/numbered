package com.numbered.app

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.biometrics.BiometricManager
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.security.AppLock
import com.numbered.app.security.AppLockSession
import com.numbered.app.security.AuthenticationResult
import com.numbered.app.security.LockOperation
import com.numbered.app.ui.closeWeekDeepLink
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBiometricPrompt
import org.robolectric.shadows.ShadowBiometricManager
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w366dp-h813dp-xxhdpi", fontScale = 1.45f)
class AppLockTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = RuntimeEnvironment.getApplication() as NumberedApp
    private lateinit var database: NumberedDatabase
    private val week = LocalDate.of(2026, 9, 28)

    @Before fun prepare() {
        database = NumberedDatabase.inMemory(app)
        app.replaceContainer(AppContainer(app, database, Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)))
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        Shadow.extract<ShadowBiometricManager>(app.getSystemService(BiometricManager::class.java)).setCanAuthenticate(true)
        runBlocking {
            app.container.repository.saveProfile(LocalDate.of(1989, 12, 2), 80, false, DayOfWeek.MONDAY)
            app.container.repository.addCommitment(week, "Private commitment")
        }
    }
    @After fun close() = database.close()
    private fun await(text: String) {
        try {
            compose.waitUntil(5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError("Waiting for $text:\n${compose.onRoot().printToString()}", failure)
        }
    }
    private fun awaitPrompt() { compose.waitUntil(5_000) { ShadowBiometricPrompt.getCurrentPrompt() != null } }
    private fun hasPrivateContent() = compose.onAllNodesWithText("Private commitment").fetchSemanticsNodes().isNotEmpty()
    private fun capture(activity: android.app.Activity, name: String) {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/screens/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun coldDeepLinksCancelFailureRotationAndBackgroundAllRespectTheLock() {
        app.container.appLock.setEnabled(true)
        val intent = Intent(Intent.ACTION_VIEW, closeWeekDeepLink(week), app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            await("Numbered is locked")
            awaitPrompt()
            assertFalse(hasPrivateContent())
            scenario.onActivity {
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                ShadowBiometricPrompt.cancelCurrentSession()
            }
            compose.waitForIdle()
            assertFalse(hasPrivateContent())
            compose.onNodeWithText("Unlock").performClick()
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionWithError(1, "Unavailable") }
            await("Authentication is unavailable. Try again.")
            assertFalse(hasPrivateContent())
            scenario.onActivity { capture(it, "app-locked") }
            compose.onNodeWithText("Unlock").performClick()
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.failCurrentSessionOnce() }
            assertFalse(hasPrivateContent())
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully() }
            compose.waitForIdle()
            await("Private commitment")
            await("What mattered this week?")
            scenario.recreate()
            await("What mattered this week?")
            assertTrue(hasPrivateContent())
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await("Numbered is locked")
            assertFalse(hasPrivateContent())
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully() }
            compose.waitForIdle()
            await("What mattered this week?")
        }
    }

    @Test fun enablingAndDisablingRequireSuccessfulAuthenticationAndPersistOnlyThePreference() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("Private commitment")
            compose.onNodeWithText("Life").performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            await("App lock")
            compose.onNodeWithText("App lock").performScrollTo()
            compose.onNodeWithText("App lock").performClick()
            awaitPrompt()
            assertFalse(app.container.appLock.enabled.value)
            scenario.onActivity { ShadowBiometricPrompt.cancelCurrentSession() }
            compose.waitForIdle()
            assertFalse(app.container.appLock.enabled.value)
            compose.onNodeWithText("App lock").performClick()
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully() }
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onRoot().fetchSemanticsNode()
                app.container.appLock.enabled.value
            }
            assertTrue(AppLock(app).enabled.value)
            assertFalse(AppLockSession(AppLock(app)).state.value.verified)
            scenario.onActivity { capture(it, "app-lock-settings") }
            compose.onNodeWithText("App lock").performClick()
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.cancelCurrentSession() }
            compose.waitForIdle()
            assertTrue(app.container.appLock.enabled.value)
            compose.onNodeWithText("App lock").performClick()
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully() }
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onRoot().fetchSemanticsNode()
                !app.container.appLock.enabled.value
            }
            scenario.onActivity { assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
            assertFalse(AppLock(app).enabled.value)
        }
    }

    @Test fun sharedIdeasCannotBeReadOrSavedUntilUnlockedAndDraftsSurviveRelocking() {
        app.container.appLock.setEnabled(true)
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Private shared idea")
            .setClass(app, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<CaptureActivity>(intent).use { scenario ->
            await("Numbered is locked")
            assertTrue(compose.onAllNodesWithText("Private shared idea").fetchSemanticsNodes().isEmpty())
            assertTrue(runBlocking { database.someday().all() }.isEmpty())
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully() }
            compose.waitForIdle()
            await("Private shared idea")
            compose.onNode(hasSetTextAction()).performTextReplacement("Edited private idea")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await("Numbered is locked")
            assertTrue(compose.onAllNodesWithText("Edited private idea").fetchSemanticsNodes().isEmpty())
            awaitPrompt()
            scenario.onActivity { ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully() }
            compose.waitForIdle()
            await("Edited private idea")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) { runBlocking { database.someday().all() }.size == 1 }
            assertEquals("Edited private idea", runBlocking { database.someday().all() }.single().title)
        }
    }

    @Test fun missingDeviceLockNeverEnablesTheSettingOrOpensAnExistingLock() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
        val session = AppLockSession(app.container.appLock)
        session.begin(LockOperation.Enable)
        session.result(AuthenticationResult.NoDeviceLock)
        assertFalse(app.container.appLock.enabled.value)
        app.container.appLock.setEnabled(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("Set a screen lock in Android Settings, then try again.")
            assertFalse(hasPrivateContent())
            compose.onNodeWithText("Android Settings").performClick()
            scenario.onActivity { assertEquals(android.provider.Settings.ACTION_SECURITY_SETTINGS, shadowOf(it).nextStartedActivity.action) }
        }
    }
}
