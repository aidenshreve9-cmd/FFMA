package com.focusfriend.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.focusfriend.app.dnd.DndController
import com.focusfriend.app.session.FocusViewModel
import com.focusfriend.app.ui.theme.FocusFriendTheme

/** Hosts the Focus Friend app, built natively with Jetpack Compose. */
class MainActivity : ComponentActivity() {
    private val vm: FocusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A fresh start with no session running means an earlier session's Do Not Disturb must end too.
        if (vm.session == null) DndController.restore(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            FocusFriendTheme {
                FocusApp(vm, onLeave = { moveTaskToBack(true) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Android's settings, or the session ended while the app was in the background.
        vm.onResume()
    }

    override fun onDestroy() {
        if (isFinishing) vm.onAppClosing()
        super.onDestroy()
    }
}
