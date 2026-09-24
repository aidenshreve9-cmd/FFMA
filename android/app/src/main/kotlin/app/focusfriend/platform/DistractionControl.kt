package app.focusfriend.platform

import android.Manifest
import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import app.focusfriend.MainActivity
import app.focusfriend.core.InterruptionPolicy

/**
 * Platform-neutral contract for reducing distractions. The browser preview has a
 * "cannot do this" implementation; Android has the real one below.
 */
interface DistractionControl {
    /** Derived from the OS every time — never from a stored flag. */
    fun hasAccess(): Boolean
    fun accessSettingsIntent(): Intent
    fun begin(policy: InterruptionPolicy): BeginReport
    fun end()
    /** Restores the phone if a session ended without cleaning up (crash, reboot, force stop). */
    fun recover(nowMs: Long, sessionStillRunning: Boolean)
}

data class BeginReport(
    /** True only when Do Not Disturb is really active. The UI shows the status line only then. */
    val silencing: Boolean,
    val trustedLetThrough: Int = 0,
    /** Trusted Contacts whose numbers aren't in the phone's address book, so Android can't recognise them. */
    val trustedNotInContacts: Int = 0,
    val contactsPermissionMissing: Boolean = false,
)

/**
 * Android implementation built on an app-owned AutomaticZenRule (API 29+).
 *
 * Why a rule instead of NotificationManager.setInterruptionFilter/setNotificationPolicy:
 * the rule never overwrites the person's own Do Not Disturb policy. Turning our rule off
 * returns the phone to exactly its previous state, including any DND the person had on.
 * On Android 15 this is also the supported path for apps.
 */
class AndroidDistractionControl(private val context: Context) : DistractionControl {
    private val nm: NotificationManager = checkNotNull(context.getSystemService(NotificationManager::class.java)) { "NotificationManager unavailable" }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val starrer = TrustedContactStarrer(context)

    override fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    override fun accessSettingsIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun begin(policy: InterruptionPolicy): BeginReport {
        if (!hasAccess()) return BeginReport(silencing = false)
        // Record intent to change things *before* changing them, so recovery can always undo.
        prefs.edit().putBoolean(KEY_RULE_ACTIVE, true).commit()
        val star = if (policy.allowTrustedContacts) starrer.starTrusted(policy.trustedNumbers) else TrustedContactStarrer.Result.NONE
        return try {
            val id = ensureRule(buildZenPolicy(policy, starredCallersAllowed = star.letThrough > 0))
            nm.setAutomaticZenRuleState(id, Condition(CONDITION_ID, "Focus", Condition.STATE_TRUE))
            BeginReport(true, star.letThrough, star.notInContacts, star.permissionMissing)
        } catch (e: SecurityException) {
            // Access was revoked between the check and the call: undo and report honestly.
            end()
            BeginReport(silencing = false)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not start Focus rule: ${e.javaClass.simpleName}")
            end()
            BeginReport(silencing = false)
        }
    }

    override fun end() {
        try {
            prefs.getString(KEY_RULE_ID, null)?.let { id ->
                if (hasAccess()) nm.setAutomaticZenRuleState(id, Condition(CONDITION_ID, "Focus", Condition.STATE_FALSE))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not end Focus rule: ${e.javaClass.simpleName}")
        } finally {
            starrer.restore()
            prefs.edit().putBoolean(KEY_RULE_ACTIVE, false).commit()
        }
    }

    override fun recover(nowMs: Long, sessionStillRunning: Boolean) {
        if (sessionStillRunning) return
        if (prefs.getBoolean(KEY_RULE_ACTIVE, false) || starrer.hasPendingRestore()) end()
    }

    private fun buildZenPolicy(policy: InterruptionPolicy, starredCallersAllowed: Boolean): ZenPolicy {
        val people = if (starredCallersAllowed) ZenPolicy.PEOPLE_TYPE_STARRED else ZenPolicy.PEOPLE_TYPE_NONE
        return ZenPolicy.Builder()
            .hideAllVisualEffects()                          // no pop-ups, peeking, badges or lights
            .allowCalls(people)                              // everyone else's calls are silenced
            .allowMessages(people)
            .allowRepeatCallers(policy.allowRepeatCallers)   // the same person's 2nd call within 15 min rings
            .allowAlarms(policy.allowAlarms)                 // Alarm Safety
            .allowMedia(true)                                // Focus Friend's own sound must keep playing
            .allowSystem(false)
            .allowReminders(false)
            .allowEvents(false)
            .build()
        // Emergency alerts are delivered by the system regardless of Do Not Disturb.
    }

    private fun ensureRule(zen: ZenPolicy): String {
        val rule = AutomaticZenRule(
            "Focus Friend",
            null,
            ComponentName(context, MainActivity::class.java),
            CONDITION_ID,
            zen,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        val existing = prefs.getString(KEY_RULE_ID, null)
        if (existing != null && nm.getAutomaticZenRule(existing) != null && nm.updateAutomaticZenRule(existing, rule)) return existing
        val id = nm.addAutomaticZenRule(rule)
        prefs.edit().putString(KEY_RULE_ID, id).commit()
        return id
    }

    companion object {
        private const val TAG = "FocusFriend"
        private const val PREFS = "focus_rules"
        private const val KEY_RULE_ID = "rule_id"
        private const val KEY_RULE_ACTIVE = "rule_active"
        val CONDITION_ID: Uri = Uri.parse("condition://app.focusfriend/focus")
    }
}

/**
 * Android's Do Not Disturb can let through "starred" contacts, not an app-specific list.
 * To honour Trusted Contacts, Focus stars the chosen people who aren't starred yet, and
 * un-stars exactly those people afterwards. Each change is recorded before it is made.
 * People already starred in Contacts also get through (disclosed in Settings).
 */
class TrustedContactStarrer(private val context: Context) {
    data class Result(val letThrough: Int, val notInContacts: Int, val permissionMissing: Boolean) {
        companion object { val NONE = Result(0, 0, false) }
    }

    private val prefs = context.getSharedPreferences("focus_starred", Context.MODE_PRIVATE)

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasPendingRestore() = !prefs.getStringSet(KEY, null).isNullOrEmpty()

    fun starTrusted(numbers: List<String>): Result {
        if (numbers.isEmpty()) return Result.NONE
        if (!hasPermission()) return Result(0, numbers.size, permissionMissing = true)
        val resolver = context.contentResolver
        var letThrough = 0
        var missing = 0
        for (number in numbers) {
            val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            try {
                resolver.query(lookup, arrayOf(BaseColumns._ID, COLUMN_STARRED), null, null, null)?.use { c ->
                    if (!c.moveToFirst()) { missing++; return@use }
                    letThrough++
                    do {
                        val contactId = c.getLong(0)
                        if (c.getInt(1) == 0) {
                            remember(contactId)                       // record first, then change
                            setStarred(contactId, true)
                        }
                    } while (c.moveToNext())
                } ?: missing++
            } catch (e: RuntimeException) {
                missing++
            }
        }
        return Result(letThrough, missing, permissionMissing = false)
    }

    /** Un-stars only the people Focus starred. Safe to call any number of times. */
    fun restore() {
        val ids = prefs.getStringSet(KEY, null)?.toSet().orEmpty()
        if (ids.isEmpty()) return
        if (hasPermission()) ids.forEach { it.toLongOrNull()?.let { id -> runCatching { setStarred(id, false) } } }
        prefs.edit().remove(KEY).commit()
    }

    private fun remember(contactId: Long) {
        val set = prefs.getStringSet(KEY, null)?.toMutableSet() ?: mutableSetOf()
        set += contactId.toString()
        prefs.edit().putStringSet(KEY, set).commit()
    }

    private fun setStarred(contactId: Long, starred: Boolean) {
        val values = ContentValues().apply { put(COLUMN_STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), values, null, null)
    }

    private companion object {
        const val KEY = "starred_by_focus"
        /** ContactsContract.ContactsColumns.STARRED (declared on a protected interface, so named here). */
        const val COLUMN_STARRED = "starred"
    }
}

/** Safety net: runs at the session's end time, after a reboot, and after an app update. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = app.focusfriend.session.SessionStore(context)
        val running = store.load()?.let { !it.isOver(System.currentTimeMillis()) } ?: false
        if (!running) store.clear()
        AndroidDistractionControl(context).recover(System.currentTimeMillis(), running)
    }

    companion object {
        private const val ACTION = "app.focusfriend.action.RESTORE"

        fun schedule(context: Context, atMs: Long) {
            val am: AlarmManager = checkNotNull(context.getSystemService(AlarmManager::class.java)) { "AlarmManager unavailable" }
            // Inexact is fine: this is a backstop behind the foreground service's own timer.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
        }

        private fun pending(context: Context) = PendingIntent.getBroadcast(
            context, 7,
            Intent(context, RestoreReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
