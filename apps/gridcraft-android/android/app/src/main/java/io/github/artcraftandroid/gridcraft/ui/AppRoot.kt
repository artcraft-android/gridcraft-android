package io.github.artcraftandroid.gridcraft.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.artcraftandroid.gridcraft.ui.editor.EditorScreen

/**
 * The app's screens, and its one-time messages. Going on to a screen slides it in from the side
 * over the whole width; going back slides the other way.
 */
@Composable
fun AppRoot(vm: AppViewModel, onOpen: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }
    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            slideInHorizontally(tween(Motion.BASIC, easing = Motion.EaseOutSharp)) { it * dir } togetherWith
                slideOutHorizontally(tween(Motion.BASIC, easing = Motion.EaseOutSharp)) { -it * dir }
        },
        label = "screens",
    ) { screen ->
        when (screen) {
            Screen.Start -> StartScreen(vm, onOpen)
            Screen.Editor -> EditorScreen(vm)
            Screen.Settings -> SettingsScreen(vm, runCatching { io.github.artcraftandroid.gridcraft.core.engineVersion() }.getOrDefault(""))
        }
    }
}
