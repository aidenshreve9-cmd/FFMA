package com.focusfriend.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.focusfriend.app.session.FocusViewModel
import com.focusfriend.app.session.Screen
import com.focusfriend.app.ui.background.Nebula
import com.focusfriend.app.ui.background.NebulaLevel
import com.focusfriend.app.ui.components.ToastHost
import com.focusfriend.app.ui.home.HomeScreen
import com.focusfriend.app.ui.session.DoneScreen
import com.focusfriend.app.ui.session.SessionScreen
import com.focusfriend.app.ui.settings.SettingsScreen
import com.focusfriend.app.ui.sheets.SheetLayer
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.ui.welcome.WelcomeScreen
import com.focusfriend.app.util.rememberFrameTime
import com.focusfriend.app.util.rememberReducedMotion

/** Every launch opens on Welcome; then Home ⇄ Settings, and Home → Session → Done → Home. */
@Composable
fun FocusApp(vm: FocusViewModel, onLeave: () -> Unit) {
    val still = rememberReducedMotion()
    val time = rememberFrameTime(!still)

    // The screen stays on during Focus.
    val view = LocalView.current
    val keepOn = vm.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // Back closes a sheet or goes back a screen; on Home or during Focus the app goes to the background.
    BackHandler { if (!vm.back()) onLeave() }

    val level = animateFloatAsState(
        when (vm.screen) {
            Screen.WELCOME -> NebulaLevel.START
            Screen.HOME -> NebulaLevel.HOME
            Screen.SETTINGS -> NebulaLevel.SETTINGS
            Screen.SESSION -> NebulaLevel.SESSION
            Screen.DONE -> NebulaLevel.DONE
        },
        tween(if (still) 0 else 1200),
        label = "nebula",
    )

    Box(Modifier.fillMaxSize().background(Palette.Pitch)) {
        if (vm.screen == Screen.HOME || vm.screen == Screen.DONE) Nebula(time, level, Modifier.fillMaxSize())

        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                when {
                    still -> EnterTransition.None togetherWith ExitTransition.None
                    targetState == Screen.SETTINGS ->
                        (slideInHorizontally(tween(620, 80)) { (it * 0.14f).toInt() } + fadeIn(tween(620, 80))) togetherWith
                            (slideOutHorizontally(tween(480)) { -(it * 0.10f).toInt() } + fadeOut(tween(480)))
                    initialState == Screen.SETTINGS ->
                        (slideInHorizontally(tween(620, 80)) { -(it * 0.10f).toInt() } + fadeIn(tween(620, 80))) togetherWith
                            (slideOutHorizontally(tween(480)) { (it * 0.14f).toInt() } + fadeOut(tween(480)))
                    else ->
                        (fadeIn(tween(620, 80)) + scaleIn(tween(620, 80), initialScale = 0.94f)) togetherWith
                            (fadeOut(tween(480)) + scaleOut(tween(480), targetScale = 1.06f))
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.WELCOME -> WelcomeScreen(onStart = vm::start)
                Screen.HOME -> HomeScreen(vm, time)
                Screen.SETTINGS -> SettingsScreen(vm, time)
                Screen.SESSION -> SessionScreen(vm, time)
                Screen.DONE -> DoneScreen(vm, time)
            }
        }

        SheetLayer(vm)

        val toast = vm.toast
        ToastHost(
            text = toast?.text,
            key = toast?.id,
            onDone = { toast?.let(vm::clearToast) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}
