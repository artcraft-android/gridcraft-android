package io.github.artcraftandroid.gridcraft

import android.app.Application
import android.system.Os
import io.github.artcraftandroid.gridcraft.core.MobileSession
import io.github.artcraftandroid.gridcraft.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class GridcraftApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The engine, loaded once per process on a background thread and shared by the screens. */
    val engine: Deferred<Result<Engine>> by lazy {
        scope.async {
            runCatching {
                // Rust's home and temporary folder on Android are not the app's own: point them at
                // it before the library first reads them (it can't set them itself)
                Os.setenv("HOME", filesDir.absolutePath, true)
                Os.setenv("TMPDIR", cacheDir.absolutePath, true)
                Engine(MobileSession())
            }
        }
    }
}
