package com.numbered.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import java.io.File
import java.time.Clock
import com.numbered.app.data.NumberedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w366dp-h813dp-xxhdpi", fontScale = 1.45f)
class CaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = RuntimeEnvironment.getApplication() as NumberedApp
    private lateinit var database: NumberedDatabase

    @Before fun prepare() {
        database = NumberedDatabase.inMemory(app)
        app.replaceContainer(AppContainer(app, database, Clock.systemUTC()))
    }

    @After fun close() = database.close()

    private fun share(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .setClass(app, CaptureActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun awaitScreen() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Save to Someday").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ideas() = runBlocking { database.someday().all() }

    @Test fun androidOffersNumberedForTextAndLinks() {
        val incoming = Intent(Intent.ACTION_SEND).setType("text/plain").addCategory(Intent.CATEGORY_DEFAULT)
        val names = app.packageManager.queryIntentActivities(incoming, 0).map { it.activityInfo.name }
        assertTrue(CaptureActivity::class.java.name in names)
        val images = app.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("image/png"), 0)
        assertTrue(images.none { it.activityInfo.name == CaptureActivity::class.java.name })
    }

    @Test fun editedLinkSurvivesRecreationAndSavesOnceBeforeSetup() {
        ActivityScenario.launch<CaptureActivity>(share("https://example.com/ideas?q=one&lang=en")).use { scenario ->
            awaitScreen()
            val edited = "Read café ideas https://example.com/ideas?q=one&lang=en"
            compose.onNode(hasSetTextAction()).performTextReplacement(edited)
            scenario.recreate()
            awaitScreen()
            compose.onNodeWithText(edited).assertExists()
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val view = activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/screens/share-capture.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            assertTrue(ideas().isEmpty())
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) { ideas().size == 1 }
            assertEquals(edited, ideas().single().title)
            assertEquals(null, runBlocking { app.container.repository.currentProfile() })
        }
    }

    @Test fun cancellingAndBlankInputSaveNothing() {
        ActivityScenario.launch<CaptureActivity>(share("Maybe later")).use {
            awaitScreen()
            compose.onNode(hasSetTextAction()).performTextReplacement("  \n ")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Cancel").performClick()
        }
        assertTrue(ideas().isEmpty())
    }

    @Test fun textWinsOverSubjectAndSubjectIsAFallback() {
        assertEquals("Link", sharedIdea(share("Link").putExtra(Intent.EXTRA_SUBJECT, "Title")))
        assertEquals("Title", sharedIdea(share(" ").putExtra(Intent.EXTRA_SUBJECT, "Title")))
        assertEquals("", sharedIdea(share("Ignore").setType("image/png")))
        assertEquals("", sharedIdea(share("Ignore").setAction(Intent.ACTION_VIEW)))
    }
}
