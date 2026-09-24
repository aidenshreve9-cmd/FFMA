package com.focusfriend.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Turns Do Not Disturb on for a Focus session and puts the phone back exactly as it was afterwards.
 *
 * During Focus:
 *  - notifications and calls are silenced (no sound, vibration, pop-ups or lights);
 *  - repeat callers get through (Android's 15-minute window);
 *  - media stays on, so the session's sound keeps playing;
 *  - alarms ring only when Alarm Safety is on;
 *  - emergency alerts are never silenced by Do Not Disturb.
 *
 * The phone's previous Do Not Disturb state is saved before the first change, so it can be restored
 * after the session, after the app is closed (a scheduled alarm), or after a restart.
 */
object DndController {
    private const val PREFS = "ff.dnd"
    private const val ACTIVE = "active"
    private const val END_AT = "endAt"
    private const val PREV_FILTER = "prevFilter"
    private const val PREV_CATEGORIES = "prevCategories"
    private const val PREV_CALLS = "prevCalls"
    private const val PREV_MESSAGES = "prevMessages"
    private const val PREV_EFFECTS = "prevEffects"
    private const val PREV_CONVERSATIONS = "prevConversations"
    private const val RESTORE_REQUEST = 1

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAccess(ctx: Context): Boolean = nm(ctx).isNotificationPolicyAccessGranted

    fun isActive(ctx: Context): Boolean = prefs(ctx).getBoolean(ACTIVE, false)

    fun endAt(ctx: Context): Long = prefs(ctx).getLong(END_AT, 0L)

    /** Returns true only when Do Not Disturb is really on. */
    @Synchronized
    fun begin(ctx: Context, alarms: Boolean, endAtMillis: Long): Boolean {
        val nm = nm(ctx)
        if (!nm.isNotificationPolicyAccessGranted) return false
        val p = prefs(ctx)
        return try {
            if (!p.getBoolean(ACTIVE, false)) saveCurrent(ctx, nm)

            var categories = Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or Policy.PRIORITY_CATEGORY_MEDIA
            if (alarms) categories = categories or Policy.PRIORITY_CATEGORY_ALARMS
            // Blocks sound and vibration (Do Not Disturb itself) and the visual interruptions;
            // notifications stay in the shade so nothing is lost.
            val effects = Policy.SUPPRESSED_EFFECT_PEEK or
                Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
                Policy.SUPPRESSED_EFFECT_LIGHTS or
                Policy.SUPPRESSED_EFFECT_AMBIENT
            nm.notificationPolicy = makePolicy(
                categories, Policy.PRIORITY_SENDERS_ANY, Policy.PRIORITY_SENDERS_ANY, effects,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Policy.CONVERSATION_SENDERS_NONE else 0,
            )
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

            p.edit().putBoolean(ACTIVE, true).putLong(END_AT, endAtMillis).commit()
            if (endAtMillis > 0) scheduleRestore(ctx, endAtMillis)
            true
        } catch (e: SecurityException) {
            restore(ctx)
            false
        }
    }

    /** Puts Do Not Disturb back the way it was. Safe to call more than once. */
    @Synchronized
    fun restore(ctx: Context) {
        val p = prefs(ctx)
        cancelRestore(ctx)
        if (!p.getBoolean(ACTIVE, false)) return
        val nm = nm(ctx)
        if (nm.isNotificationPolicyAccessGranted) {
            try {
                nm.notificationPolicy = makePolicy(
                    p.getInt(PREV_CATEGORIES, 0),
                    p.getInt(PREV_CALLS, Policy.PRIORITY_SENDERS_STARRED),
                    p.getInt(PREV_MESSAGES, Policy.PRIORITY_SENDERS_STARRED),
                    p.getInt(PREV_EFFECTS, 0),
                    p.getInt(PREV_CONVERSATIONS, 0),
                )
                nm.setInterruptionFilter(p.getInt(PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL))
            } catch (e: SecurityException) {
                // Access was revoked mid-session: Android turns off the app's Do Not Disturb itself.
            }
        }
        p.edit().putBoolean(ACTIVE, false).remove(END_AT).commit()
    }

    private fun saveCurrent(ctx: Context, nm: NotificationManager) {
        val cur = nm.notificationPolicy
        val filter = nm.currentInterruptionFilter.let {
            if (it == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else it
        }
        prefs(ctx).edit()
            .putInt(PREV_FILTER, filter)
            .putInt(PREV_CATEGORIES, cur.priorityCategories)
            .putInt(PREV_CALLS, cur.priorityCallSenders)
            .putInt(PREV_MESSAGES, cur.priorityMessageSenders)
            .putInt(PREV_EFFECTS, cur.suppressedVisualEffects.coerceAtLeast(0))
            .putInt(
                PREV_CONVERSATIONS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cur.priorityConversationSenders else 0,
            )
            .commit()
    }

    private fun makePolicy(categories: Int, calls: Int, messages: Int, effects: Int, conversations: Int): Policy =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Policy(categories, calls, messages, effects, conversations)
        } else {
            Policy(categories, calls, messages, effects)
        }

    private fun restoreIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, RESTORE_REQUEST,
            Intent(ctx, RestoreReceiver::class.java).setAction(RestoreReceiver.ACTION_SESSION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun scheduleRestore(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = restoreIntent(ctx)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // may run a few minutes late
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelRestore(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(restoreIntent(ctx))
    }
}
