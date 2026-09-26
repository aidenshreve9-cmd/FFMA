package com.focusfriend.app.dnd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Ends Do Not Disturb when a session's time is up, even if the app isn't open, and after a restart. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SESSION_END -> DndController.restore(context)
            // A restart loses the scheduled alarm; the session can't still be running anyway.
            Intent.ACTION_BOOT_COMPLETED -> DndController.restore(context)
        }
    }

    companion object {
        const val ACTION_SESSION_END = "com.focusfriend.app.SESSION_END"
    }
}
