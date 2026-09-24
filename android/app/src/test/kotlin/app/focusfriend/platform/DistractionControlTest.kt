package app.focusfriend.platform

import android.app.Application
import android.app.NotificationManager
import android.service.notification.ZenPolicy
import app.focusfriend.core.FocusSession
import app.focusfriend.core.FocusSettings
import app.focusfriend.core.InterruptionPolicy
import app.focusfriend.core.TrustedContact
import app.focusfriend.session.SessionStore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Chapter 15: real Do Not Disturb behaviour, verified against Robolectric's Android 15 framework. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DistractionControlTest {
    private lateinit var app: Application
    private lateinit var nm: NotificationManager
    private val policy = InterruptionPolicy.from(FocusSettings())

    @Before fun setUp() {
        app = RuntimeEnvironment.getApplication()
        nm = app.getSystemService(NotificationManager::class.java)!!
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
    }

    private fun onlyRule() = nm.automaticZenRules.values.single()

    @Test fun withoutAccessNothingIsSilencedAndNothingIsCreated() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        val c = AndroidDistractionControl(app)
        assertFalse(c.hasAccess())
        assertFalse(c.begin(policy).silencing, "never claim silencing without access")
        assertTrue(nm.automaticZenRules.isEmpty())
    }

    @Test fun beginUsesItsOwnRuleWithTheSpecPolicy() {
        val report = AndroidDistractionControl(app).begin(policy)
        assertTrue(report.silencing)
        val rule = onlyRule()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, rule.interruptionFilter)
        assertEquals(AndroidDistractionControl.CONDITION_ID, rule.conditionId)
        val z = assertNotNull(rule.zenPolicy)
        assertEquals(ZenPolicy.PEOPLE_TYPE_NONE, z.priorityCallSenders, "calls from everyone else are silenced")
        assertEquals(ZenPolicy.PEOPLE_TYPE_NONE, z.priorityMessageSenders)
        assertEquals(ZenPolicy.STATE_ALLOW, z.priorityCategoryRepeatCallers, "repeat callers ring")
        assertEquals(ZenPolicy.STATE_ALLOW, z.priorityCategoryAlarms, "Alarm Safety defaults ON")
        assertEquals(ZenPolicy.STATE_ALLOW, z.priorityCategoryMedia, "Focus Friend's own sound keeps playing")
        assertEquals(ZenPolicy.STATE_DISALLOW, z.priorityCategoryReminders)
        assertEquals(ZenPolicy.STATE_DISALLOW, z.priorityCategoryEvents)
        assertEquals(ZenPolicy.STATE_DISALLOW, z.visualEffectPeek, "no pop-ups")
        assertEquals(ZenPolicy.STATE_DISALLOW, z.visualEffectFullScreenIntent)
        assertEquals(ZenPolicy.STATE_DISALLOW, z.visualEffectNotificationList)
    }

    @Test fun alarmSafetyOffBlocksAlarms() {
        AndroidDistractionControl(app).begin(InterruptionPolicy.from(FocusSettings(alarmSafety = false)))
        assertEquals(ZenPolicy.STATE_DISALLOW, onlyRule().zenPolicy!!.priorityCategoryAlarms)
    }

    @Test fun trustedContactsWithoutContactsAccessAreReportedHonestly() {
        val settings = FocusSettings(trustedContactsOn = true, contacts = listOf(TrustedContact("a", "Grandma", "780 555 1234", "7805551234")))
        val report = AndroidDistractionControl(app).begin(InterruptionPolicy.from(settings))
        assertTrue(report.silencing)
        assertTrue(report.contactsPermissionMissing)
        assertEquals(ZenPolicy.PEOPLE_TYPE_NONE, onlyRule().zenPolicy!!.priorityCallSenders, "nobody is let through without access")
    }

    @Test fun thePersonsOwnDoNotDisturbSettingsAreNeverTouched() {
        val before = nm.notificationPolicy
        val filterBefore = nm.currentInterruptionFilter
        val c = AndroidDistractionControl(app)
        c.begin(policy); c.end()
        assertEquals(before, nm.notificationPolicy)
        assertEquals(filterBefore, nm.currentInterruptionFilter)
    }

    @Test fun theRuleIsReusedNotDuplicated() {
        val c = AndroidDistractionControl(app)
        repeat(3) { c.begin(policy); c.end() }
        assertEquals(1, nm.automaticZenRules.size)
    }

    @Test fun recoveryAfterAnUnexpectedEnd() {
        AndroidDistractionControl(app).begin(policy)
        val prefs = app.getSharedPreferences("focus_rules", 0)
        assertTrue(prefs.getBoolean("rule_active", false), "intent recorded before changing anything")
        // Process died mid-session; a fresh instance sees a running session and leaves it alone…
        AndroidDistractionControl(app).recover(System.currentTimeMillis(), sessionStillRunning = true)
        assertTrue(prefs.getBoolean("rule_active", false))
        // …and once the session is over, it restores the phone. Idempotent.
        AndroidDistractionControl(app).recover(System.currentTimeMillis(), sessionStillRunning = false)
        assertFalse(prefs.getBoolean("rule_active", false))
        AndroidDistractionControl(app).recover(System.currentTimeMillis(), sessionStillRunning = false)
        assertFalse(prefs.getBoolean("rule_active", false))
    }

    @Test fun sessionStoreKeepsOnlyTheFixedEndTime() {
        val store = SessionStore(app)
        assertNull(store.load())
        val s = FocusSession(30, 1_000_000L)
        store.save(s, "pink", null)
        val loaded = assertNotNull(store.load())
        assertEquals(s.endAtMs, loaded.endAtMs)
        assertEquals("pink", store.soundId())
        app.getSharedPreferences("focus_session", 0).edit().putInt("minutes", 20).commit()
        assertNull(store.load(), "corrupted duration is rejected, not trusted")
        store.clear(); assertNull(store.load())
    }
}
