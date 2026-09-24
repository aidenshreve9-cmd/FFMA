package app.focusfriend.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.focusfriend.MainActivity
import app.focusfriend.audio.Chime
import app.focusfriend.audio.NoiseEngine
import app.focusfriend.audio.SoundOutput
import app.focusfriend.audio.UserSoundPlayer
import app.focusfriend.core.FocusSession
import app.focusfriend.core.InterruptionPolicy
import app.focusfriend.core.Quote
import app.focusfriend.core.Quotes
import app.focusfriend.platform.AndroidDistractionControl
import app.focusfriend.platform.BeginReport
import app.focusfriend.platform.RestoreReceiver

/** Persists only what recovery needs: the fixed end time and what is playing. No history. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_session", Context.MODE_PRIVATE)

    fun save(s: FocusSession, soundId: String, soundPath: String?) {
        prefs.edit().putInt("minutes", s.minutes).putLong("startedAt", s.startedAtMs)
            .putString("sound", soundId).putString("soundPath", soundPath).commit()
    }
    fun load(): FocusSession? {
        val minutes = prefs.getInt("minutes", 0)
        val started = prefs.getLong("startedAt", 0L)
        return try { if (minutes > 0 && started > 0) FocusSession(minutes, started) else null } catch (e: IllegalArgumentException) { null }
    }
    fun soundId(): String? = prefs.getString("sound", null)
    fun soundPath(): String? = prefs.getString("soundPath", null)
    fun clear() { prefs.edit().clear().commit() }
}

/** What's playing and showing during a session. */
data class ActiveSession(
    val session: FocusSession,
    val soundId: String,
    val soundName: String,
    val soundPath: String?,
    val soundGain: Float,
    val sceneId: String,
    val sceneName: String,
    val report: BeginReport,
)

data class Completion(val natural: Boolean, val quote: Quote)

/**
 * In-process owner of the running session. Completion and cleanup run exactly once,
 * whichever of natural finish, early end, service restart or recovery gets there first.
 */
object FocusRuntime {
    interface Listener {
        fun onSessionStarted(active: ActiveSession) {}
        fun onSessionFinished(completion: Completion) {}
    }

    var active: ActiveSession? = null
        private set
    var lastCompletion: Completion? = null
        private set
    private val listeners = LinkedHashSet<Listener>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: Listener) { listeners += l }
    fun removeListener(l: Listener) { listeners -= l }

    fun start(context: Context, active: ActiveSession, policy: InterruptionPolicy): ActiveSession {
        val app = context.applicationContext
        val report = AndroidDistractionControl(app).begin(policy)
        val started = active.copy(report = report)
        this.active = started
        lastCompletion = null
        SessionStore(app).save(started.session, started.soundId, started.soundPath)
        RestoreReceiver.schedule(app, started.session.endAtMs + 60_000L)
        FocusService.start(app, started)
        listeners.toList().forEach { it.onSessionStarted(started) }
        return started
    }

    fun endEarly(context: Context) = finish(context, natural = false)

    fun completeNaturally(context: Context) = finish(context, natural = true)

    /** Process start (any reason): if a session ended while the app wasn't running, restore the phone. */
    fun recoverIfEnded(context: Context) {
        val app = context.applicationContext
        val store = SessionStore(app)
        val s = store.load()
        val now = System.currentTimeMillis()
        if (s == null || s.isOver(now)) {
            store.clear()
            AndroidDistractionControl(app).recover(now, sessionStillRunning = false)
        }
    }

    /**
     * App in the foreground with no session in memory: resume one that is still running
     * (the service re-adopts it from the stored end time), or clean up one that ended.
     */
    fun restore(context: Context) {
        if (active != null) return
        val app = context.applicationContext
        val s = SessionStore(app).load()
        if (s == null || s.isOver(System.currentTimeMillis())) recoverIfEnded(app) else FocusService.resume(app)
    }

    internal fun adopt(restored: ActiveSession) {
        if (active == null) { active = restored; listeners.toList().forEach { it.onSessionStarted(restored) } }
    }

    private fun finish(context: Context, natural: Boolean) {
        val current = active ?: return
        if (!current.session.finishOnce()) return
        val app = context.applicationContext
        FocusService.stop(app)
        AndroidDistractionControl(app).end()
        RestoreReceiver.cancel(app)
        SessionStore(app).clear()
        active = null
        if (natural) Chime.play()
        val completion = Completion(natural, Quotes.random())
        lastCompletion = completion
        main.post { listeners.toList().forEach { it.onSessionFinished(completion) } }
    }

    fun consumeCompletion() { lastCompletion = null }
}

/**
 * Foreground service: keeps sound playing with the screen locked and ends the session on
 * time even when the app is in the background. Sticky, so the system restarts it after
 * a kill; on restart it resumes from the stored end time or cleans up.
 */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var output: SoundOutput? = null
    private val timeUp = Runnable { FocusRuntime.completeNaturally(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_START -> {
                val active = FocusRuntime.active ?: run { stopSelf(); return START_NOT_STICKY }
                begin(active.session, active.soundId, active.soundPath, active.soundGain)
            }
            else -> {
                // Restarted by the system (or resumed by the app) with no session in memory.
                val store = SessionStore(this)
                val s = store.load()
                if (s == null || s.isOver(System.currentTimeMillis())) {
                    store.clear()
                    AndroidDistractionControl(this).recover(System.currentTimeMillis(), sessionStillRunning = false)
                    stopSelf(); return START_NOT_STICKY
                }
                val soundId = store.soundId() ?: "white"
                val builtIn = app.focusfriend.core.Catalog.SOUNDS.firstOrNull { it.id == soundId }
                FocusRuntime.adopt(ActiveSession(s, soundId, builtIn?.name ?: "Your sound", store.soundPath(), builtIn?.gain ?: 1f,
                    app.focusfriend.core.Catalog.DEFAULT_SCENE, "Quantum Nebula", BeginReport(silencing = AndroidDistractionControl(this).hasAccess())))
                begin(s, soundId, store.soundPath(), builtIn?.gain ?: 1f)
            }
        }
        return START_STICKY
    }

    private fun begin(s: FocusSession, soundId: String, soundPath: String?, gain: Float) {
        startForeground(NOTIFICATION_ID, notification("Focusing", "Ends at the time you chose. Tap to return."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        output?.stop(immediate = true)
        output = if (soundPath != null) UserSoundPlayer(soundPath) else NoiseEngine(soundId, gain)
        output?.start()
        handler.removeCallbacks(timeUp)
        handler.postDelayed(timeUp, s.remainingMs(System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun shutdown() {
        handler.removeCallbacks(timeUp)
        output?.stop(immediate = false)
        output = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeUp)
        output?.stop(immediate = true)
        output = null
        super.onDestroy()
    }

    private fun notification(title: String, text: String): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(NotificationChannel(CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a Focus session is running."
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(Icon.createWithBitmap(smallIcon()))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    /** Monochrome orb glyph drawn at runtime, so the app needs no generated resources. */
    private fun smallIcon(): Bitmap {
        val size = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(size / 2f, size / 2f, size * 0.30f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.06f
        c.drawCircle(size / 2f, size / 2f, size * 0.44f, p)
        return bmp
    }

    companion object {
        private const val CHANNEL = "focus_session"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "app.focusfriend.action.START"
        private const val ACTION_STOP = "app.focusfriend.action.STOP"

        fun start(context: Context, @Suppress("UNUSED_PARAMETER") active: ActiveSession) {
            context.startForegroundService(Intent(context, FocusService::class.java).setAction(ACTION_START))
        }
        fun resume(context: Context) {
            context.startForegroundService(Intent(context, FocusService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, FocusService::class.java).setAction(ACTION_STOP))
        }
    }
}
