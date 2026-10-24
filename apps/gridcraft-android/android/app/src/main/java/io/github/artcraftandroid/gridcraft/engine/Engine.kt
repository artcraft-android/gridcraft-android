package io.github.artcraftandroid.gridcraft.engine

import io.github.artcraftandroid.gridcraft.core.MobileException
import io.github.artcraftandroid.gridcraft.core.MobileSession
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The GridCraft engine (Rust: `apps/gridcraft-android`) as the app uses it: one [MobileSession]
 * and the threads its calls run on. Never call the session from the main thread.
 */
class Engine(val session: MobileSession) {
    /**
     * Edits and queries run one at a time, in the order the UI issued them, on a thread with a
     * large stack: formulas nest.
     */
    val commands: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(null, r, "gridcraft-commands", 64L shl 20) }.asCoroutineDispatcher()

    /** The grid's viewports, built from a snapshot of the workbook without holding the session. */
    val views: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(null, r, "gridcraft-views", 32L shl 20) }.asCoroutineDispatcher()

    /** Run [block] on the session after the edits issued before it. */
    suspend fun <T> command(block: MobileSession.() -> T): T = withContext(commands) { session.block() }

    /** Read for the grid on the views thread. */
    suspend fun <T> view(block: MobileSession.() -> T): T = withContext(views) { session.block() }
}

/** A message for the user from anything the engine (or the system) threw. */
val Throwable.userMessage: String
    get() = when (this) {
        is MobileException -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
        // the system's wording names packages and URIs; say what it means instead
        is SecurityException -> "GridCraft isn't allowed to open that file. Open it again from Open or the app it's in."
        is java.io.FileNotFoundException -> "That file couldn't be found. It may have been moved or deleted."
        else -> message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: "Something went wrong."
    }
