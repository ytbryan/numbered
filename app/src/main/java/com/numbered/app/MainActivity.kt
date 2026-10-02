package com.numbered.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.numbered.app.ui.NumberedRoot
import com.numbered.app.ui.theme.NumberedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val today = appContainer.today
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    today.refresh()
                    delay(DATE_CHECK_INTERVAL_MILLIS)
                }
            }
        }
        setContent {
            NumberedTheme {
                NumberedRoot()
            }
        }
    }

    private companion object {
        const val DATE_CHECK_INTERVAL_MILLIS = 60_000L
    }
}
