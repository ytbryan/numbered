package com.numbered.app

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.NumberedRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NumberedApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(NumberedDatabase.open(this), Clock.systemDefaultZone())
    }

    @VisibleForTesting
    fun replaceContainer(replacement: AppContainer) {
        container = replacement
    }
}

class AppContainer(val database: NumberedDatabase, val clock: Clock) {
    val repository = NumberedRepository(database, clock)
    val today = Today(clock)
}

/** The current date as a stream, so every screen rolls over together at midnight and on resume. */
class Today(private val clock: Clock) {
    private val date = MutableStateFlow(LocalDate.now(clock))
    val value: StateFlow<LocalDate> = date.asStateFlow()

    fun refresh() {
        date.value = LocalDate.now(clock)
    }

    fun nowMillis(): Long = clock.millis()
}

val Context.appContainer: AppContainer get() = (applicationContext as NumberedApp).container
