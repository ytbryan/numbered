package com.numbered.app

import androidx.core.content.FileProvider
import org.junit.rules.ExternalResource

/**
 * Clears FileProvider's static cache of resolved roots before each test.
 *
 * FileProvider resolves each authority's paths once per process, but Robolectric gives every test a
 * new data directory while keeping static state, so a later test would share files from an earlier
 * test's directory and fail. On a phone the app's directories never change, so this is test-only.
 */
class FreshFileProviderPaths : ExternalResource() {
    override fun before() {
        @Suppress("UNCHECKED_CAST")
        val cache = FileProvider::class.java.getDeclaredField("sCache")
            .apply { isAccessible = true }
            .get(null) as MutableMap<String, *>
        synchronized(cache) { cache.clear() }
    }
}
