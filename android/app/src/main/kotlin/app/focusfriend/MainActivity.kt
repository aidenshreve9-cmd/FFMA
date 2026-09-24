package app.focusfriend

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.OnBackInvokedDispatcher
import app.focusfriend.core.Catalog
import app.focusfriend.core.FocusSession
import app.focusfriend.core.FocusSettings
import app.focusfriend.core.InterruptionPolicy
import app.focusfriend.core.TrustedContactBook
import app.focusfriend.core.search.FieldKind
import app.focusfriend.core.search.SearchController
import app.focusfriend.core.search.SearchInput
import app.focusfriend.data.MediaItem
import app.focusfriend.data.MediaLibrary
import app.focusfriend.data.SettingsStore
import app.focusfriend.platform.AndroidDistractionControl
import app.focusfriend.platform.BeginReport
import app.focusfriend.platform.TrustedContactStarrer
import app.focusfriend.session.ActiveSession
import app.focusfriend.session.Completion
import app.focusfriend.session.FocusRuntime
import app.focusfriend.ui.DoneScreen
import app.focusfriend.ui.Fonts
import app.focusfriend.ui.GlassDrawable
import app.focusfriend.ui.HomeScreen
import app.focusfriend.ui.Motion
import app.focusfriend.ui.NebulaView
import app.focusfriend.ui.Palette
import app.focusfriend.ui.SessionScreen
import app.focusfriend.ui.SettingsHost
import app.focusfriend.ui.SettingsScreen
import app.focusfriend.ui.SheetHost
import app.focusfriend.ui.Toaster
import app.focusfriend.ui.Transitions
import app.focusfriend.ui.announcePolitely
import app.focusfriend.ui.dpi
import app.focusfriend.ui.label
import app.focusfriend.ui.pillButton
import java.util.UUID
import java.util.concurrent.Executors

/** On process start: if a session ended while the app wasn't running, put the phone back. */
class FocusFriendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FocusRuntime.recoverIfEnded(this)
    }
}

/**
 * The single screen host. State machine (same as the browser preview):
 * IDLE → PERMISSION_REQUIRED → (PERMISSION_BARRIER) → ACTIVE → (CONFIRM_END) → COMPLETED → IDLE.
 */
class MainActivity : Activity(), SettingsHost, FocusRuntime.Listener {
    override var settings = FocusSettings(); private set
    override lateinit var search: SearchController; private set

    private lateinit var root: FrameLayout
    private lateinit var nebula: NebulaView
    private lateinit var home: HomeScreen
    private lateinit var sessionScreen: SessionScreen
    private lateinit var done: DoneScreen
    private lateinit var settingsScreen: SettingsScreen
    private lateinit var sheets: SheetHost
    private lateinit var toaster: Toaster
    private var current: View? = null
    private var pendingStart = false
    private var lastMinute = -1
    private var warnedAccessLost = false

    private val control by lazy { AndroidDistractionControl(this) }
    private val starrer by lazy { TrustedContactStarrer(this) }
    private val store by lazy { SettingsStore(this) }
    private val media by lazy { MediaLibrary(this) }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable { override fun run() { tick(); main.postDelayed(this, 1000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        @Suppress("DEPRECATION") run { window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK }

        settings = store.load().validated(media.sounds().map { it.id }.toSet(), media.pictures().map { it.id }.toSet())
        search = SearchController()
        indexEverything()

        root = FrameLayout(this)
        nebula = NebulaView(this)
        home = HomeScreen(this, onStart = { requestStart() }, onSettings = { openSettings() })
        sessionScreen = SessionScreen(this) { confirmEnd() }.apply { visibility = View.GONE }
        done = DoneScreen(this) { finishDone() }.apply { visibility = View.GONE }
        settingsScreen = SettingsScreen(this, this).apply { visibility = View.GONE }
        listOf(nebula, home, sessionScreen, done, settingsScreen).forEach { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        sheets = SheetHost(this, root)
        toaster = Toaster(this, root)
        setContentView(root)
        current = home

        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
    }

    override fun onResume() {
        super.onResume()
        FocusRuntime.addListener(this)
        setAmbient(true)
        if (current === settingsScreen) settingsScreen.render()
        FocusRuntime.active?.let { enterSession(it, animate = false) } ?: FocusRuntime.restore(this)
        FocusRuntime.lastCompletion?.let { if (current !== done) showDone(it) }
        if (pendingStart) {
            pendingStart = false
            if (control.hasAccess()) { sheets.close(); startFocus() } else showPermissionSheet(fromStart = true)
        }
    }

    override fun onPause() {
        FocusRuntime.removeListener(this)
        setAmbient(false)
        main.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    private fun setAmbient(on: Boolean) {
        nebula.setAnimating(on); home.focus.setAnimating(on && current === home)
        sessionScreen.timer.setAnimating(on && current === sessionScreen); sessionScreen.scene.setAnimating(on && current === sessionScreen)
        done.orb.setAnimating(on && current === done)
    }

    private fun show(to: View, sideways: Int = 0) {
        val from = current
        current = to
        Transitions.swap(from, to, sideways)
        val level = when (to) { home -> .18f; settingsScreen -> 1f; else -> .8f }
        if (Motion.enabled) ValueAnimator.ofFloat(nebula.level, level).apply { duration = 700; addUpdateListener { nebula.level = it.animatedValue as Float }; start() }
        else nebula.level = level
        setAmbient(true)
    }

    // ---------------- Focus flow ----------------

    private fun requestStart() {
        if (FocusRuntime.active != null) return
        if (!control.hasAccess() || !settings.permissionSheetSeen) showPermissionSheet(fromStart = true) else startFocus()
    }

    private fun startFocus() {
        val userSounds = media.sounds(); val userPictures = media.pictures()
        val soundId = Catalog.pickSound(settings.sound, userSounds.map { it.id })
        val sceneId = Catalog.pickScene(settings.scene, userPictures.map { it.id })
        val builtInSound = Catalog.SOUNDS.firstOrNull { it.id == soundId }
        val userSound = userSounds.firstOrNull { it.id == soundId }
        val sceneName = Catalog.SCENES.firstOrNull { it.id == sceneId }?.name ?: userPictures.firstOrNull { it.id == sceneId }?.name ?: "Quantum Nebula"
        val active = ActiveSession(
            FocusSession(home.focus.minutes, System.currentTimeMillis()),
            soundId = if (builtInSound == null && userSound == null) Catalog.DEFAULT_SOUND else soundId,
            soundName = builtInSound?.name ?: userSound?.name ?: "White Noise",
            soundPath = userSound?.file?.path,
            soundGain = builtInSound?.gain ?: if (userSound != null) 1f else Catalog.SOUNDS[0].gain,
            sceneId = sceneId, sceneName = sceneName, report = BeginReport(silencing = false),
        )
        requestNotificationPermissionOnce()
        val started = FocusRuntime.start(this, active, InterruptionPolicy.from(settings))
        val r = started.report
        when {
            settings.trustedContactsOn && r.contactsPermissionMissing ->
                toaster.show("Trusted Contacts need contacts access, so only emergency alerts and repeat callers can get through.")
            settings.trustedContactsOn && r.trustedNotInContacts > 0 ->
                toaster.show("${r.trustedNotInContacts} Trusted ${if (r.trustedNotInContacts == 1) "Contact isn't" else "Contacts aren't"} in your phone's contacts, so Android can't let them through.")
        }
    }

    override fun onSessionStarted(active: ActiveSession) { main.post { enterSession(active, animate = true) } }

    private fun enterSession(active: ActiveSession, animate: Boolean) {
        sessionScreen.setStatus(active.report.silencing)
        sessionScreen.setFooter(active.soundName, active.sceneName)
        val picture = media.pictures().firstOrNull { it.id == active.sceneId }?.let { media.decodePicture(it, MediaLibrary.MAX_PICTURE_PX) }
        sessionScreen.scene.show(active.sceneId, picture)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // screen stays awake during Focus
        warnedAccessLost = false; lastMinute = -1
        if (current !== sessionScreen) {
            if (animate) show(sessionScreen) else { current?.visibility = View.GONE; current = sessionScreen; sessionScreen.visibility = View.VISIBLE; setAmbient(true) }
            sessionScreen.live.announcePolitely("Focus started. ${active.session.minutes} minutes.")
        }
        main.removeCallbacks(ticker); main.post(ticker)
    }

    private fun tick() {
        val a = FocusRuntime.active ?: return
        val now = System.currentTimeMillis()
        if (a.session.isOver(now)) { FocusRuntime.completeNaturally(this); return }
        val m = a.session.minutesLeft(now)
        sessionScreen.timer.show(m, a.session.arcFraction(now).toFloat(), a.session.minutes, animate = lastMinute != -1)
        if (m != lastMinute) { lastMinute = m; sessionScreen.live.announcePolitely("$m ${if (m == 1) "minute" else "minutes"} left") }
        // Access revoked mid-session: say so calmly, never keep claiming silence.
        if (a.report.silencing && !control.hasAccess() && !warnedAccessLost) {
            warnedAccessLost = true
            sessionScreen.setStatus(false)
            toaster.show("Do Not Disturb access was turned off, so notifications are no longer silenced.")
        }
    }

    private fun confirmEnd() {
        val a = FocusRuntime.active ?: return
        val left = a.session.minutesLeft(System.currentTimeMillis())
        sheets.show("End focus early?",
            listOf(label(this, "About $left min left.", 15f, Palette.HALO, Fonts.mono(this))),
            listOf(
                pillButton(this, "Keep focusing", primary = true).apply { setOnClickListener { sheets.close() } },
                pillButton(this, "End session", primary = false).apply { setOnClickListener { sheets.close(); FocusRuntime.endEarly(this@MainActivity) } },
            ), centered = true)
    }

    override fun onSessionFinished(completion: Completion) { showDone(completion) }

    private fun showDone(completion: Completion) {
        main.removeCallbacks(ticker)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // screen may sleep again
        sheets.close(immediate = true)
        done.show(completion.natural, completion.quote)
        show(done)
        done.doneButton.post { done.doneButton.requestFocus() }
        done.announceForAccessibility(if (completion.natural) "Session complete." else "Session ended.")
    }

    private fun finishDone() {
        FocusRuntime.consumeCompletion()
        home.reset()                     // back to 15 minutes
        show(home)
    }

    // ---------------- Permissions ----------------

    override fun showPermissionSheet() = showPermissionSheet(fromStart = false)

    private fun showPermissionSheet(fromStart: Boolean) {
        val allowed = control.hasAccess()
        fun head(t: String) = label(this, t.uppercase(), 11f, Palette.HALO, Fonts.sans(this, 600)).apply { letterSpacing = .18f }
        fun body(t: String) = label(this, t, 14.5f, Palette.MUTED)
        fun optional(name: String, on: Boolean, detail: String) = LinearLayout(this).apply {
            addView(label(this@MainActivity, "$name: ", 14.5f, Palette.TEXT, Fonts.sans(this@MainActivity, 600)))
            addView(label(this@MainActivity, if (on) "ON" else "OFF", 12f, if (on) Palette.COPPER else Palette.FAINT, Fonts.mono(this@MainActivity)))
            addView(label(this@MainActivity, " — $detail", 14.5f, Palette.MUTED))
        }
        val n = settings.contacts.size
        val views = listOf(
            body("Once you allow it, every Focus session sets these rules automatically, and puts your phone back the way it was when the session ends."),
            head("Blocked"), body("• All notifications: sounds, pop-ups and vibration.\n• Calls from everyone else."),
            head("Always gets through"), body("• Emergency alerts.\n• Repeat callers: the first call is silenced. If the same person calls again within 15 minutes, that second call rings."),
            head("Only when turned on in Settings"),
            optional("Trusted Contacts", settings.trustedContactsOn, if (n > 0) "$n ${if (n == 1) "person" else "people"} can call and message you." else "no one added yet."),
            optional("Alarm Safety", settings.alarmSafety, "alarms ring during Focus."),
        )
        val primary = if (allowed) pillButton(this, if (fromStart) "Start Focus" else "Done", primary = true).apply {
            setOnClickListener { update { it.copy(permissionSheetSeen = true) }; sheets.close(); if (fromStart) startFocus() }
        } else pillButton(this, "Allow Do Not Disturb access", primary = true).apply {
            setOnClickListener {
                update { it.copy(permissionSheetSeen = true) }
                pendingStart = fromStart
                try { startActivity(control.accessSettingsIntent()) } catch (e: RuntimeException) { toaster.show("Open Settings › Notifications › Do Not Disturb access to allow Focus Friend.") }
            }
        }
        val notNow = pillButton(this, "Not now", primary = false).apply {
            setOnClickListener { if (fromStart && !allowed) showBarrier() else sheets.close() }
        }
        sheets.show("Focus Friend needs Do Not Disturb access", views, listOf(primary, notNow))
    }

    private fun showBarrier() {
        sheets.show("Focus can't start without access.",
            listOf(label(this, "Focus Friend needs Do Not Disturb access to reduce distractions.", 14.5f, Palette.MUTED)),
            listOf(
                pillButton(this, "Review the rules again", primary = true).apply { setOnClickListener { showPermissionSheet(fromStart = true) } },
                pillButton(this, "Go back", primary = false).apply { setOnClickListener { sheets.close() } },
            ))
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("focus_asked", MODE_PRIVATE)
            if (!prefs.getBoolean("notifications", false)) {
                prefs.edit().putBoolean("notifications", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CONTACTS) settingsScreen.render()
    }

    // ---------------- Settings host ----------------

    private fun openSettings() { if (FocusRuntime.active != null) return; settingsScreen.render(); show(settingsScreen, sideways = 1) }
    override fun close() { show(home, sideways = -1) }

    override fun update(transform: (FocusSettings) -> FocusSettings) { settings = transform(settings); store.save(settings) }
    override fun userSounds() = media.sounds()
    override fun userPictures() = media.pictures()
    override fun pictureThumb(item: MediaItem, px: Int): Bitmap? = media.decodePicture(item, px)
    override fun contactsAccessGranted() = starrer.hasPermission()
    override fun doNotDisturbAllowed() = control.hasAccess()

    override fun pickSound() = openDocument("audio/*", REQ_SOUND)
    override fun pickPicture() = openDocument("image/*", REQ_PICTURE)
    private fun openDocument(type: String, req: Int) {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), req) }
        catch (e: RuntimeException) { toaster.show("No file picker is available on this phone.") }
    }

    /** System contact picker: returns only the chosen contact, so no contacts permission is needed for this. */
    override fun pickContact() {
        try { startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT) }
        catch (e: RuntimeException) { toaster.show("The contact picker isn't available. Type the number instead.") }
    }

    override fun trustedContactsSwitched(on: Boolean) {
        if (on && !starrer.hasPermission()) requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), REQ_CONTACTS)
    }

    override fun addContact(name: String, number: String): String? = addContact(name, number, null)

    private fun addContact(name: String, number: String, lookupKey: String?): String? {
        val book = TrustedContactBook(settings.contacts)
        val msg = when (val r = book.add(name, number, { UUID.randomUUID().toString() }, lookupKey)) {
            is TrustedContactBook.AddResult.Added -> {
                update { it.copy(contacts = book.contacts) }
                search.add("trustedContacts", contactInput(r.contact))
                settingsScreen.render()
                toaster.show("Added ${r.contact.name}")
                return null
            }
            TrustedContactBook.AddResult.InvalidNumber -> "Add a name and a phone number."
            TrustedContactBook.AddResult.Duplicate -> "That number is already a Trusted Contact."
            TrustedContactBook.AddResult.Full -> "You can add up to 50 Trusted Contacts."
        }
        toaster.show(msg)
        return msg
    }

    override fun removeContact(id: String) {
        val book = TrustedContactBook(settings.contacts)
        val gone = book.remove(id) ?: return
        update { it.copy(contacts = book.contacts) }
        search.remove("trustedContacts", id)
        settingsScreen.render()
        toaster.show("Removed ${gone.name}")
    }

    override fun removeMedia(id: String) {
        io.execute {
            val ok = media.remove(id)
            main.post {
                if (!ok) { toaster.show("Couldn't remove that file."); return@post }
                search.remove(if (id.startsWith(Catalog.USER_PICTURE_PREFIX)) "scenes" else "sounds", id)
                update { it.validated(media.sounds().map { s -> s.id }.toSet(), media.pictures().map { p -> p.id }.toSet()) }
                settingsScreen.render()
            }
        }
    }

    override fun showDonationSheet() {
        sheets.show("Support Focus Friend",
            listOf(
                label(this, "Focus Friend is free, and it stays free. If it helps you, you can buy the maker a coffee.", 14.5f, Palette.MUTED),
                label(this, "The Buy Me a Coffee page isn't set up yet, so this button doesn't open anything.", 13.5f, Palette.WARN).apply {
                    background = GlassDrawable(this@MainActivity, 14f, prism = false); setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f))
                },
            ),
            listOf(
                pillButton(this, "Open Buy Me a Coffee", primary = true).apply { isEnabled = false },
                pillButton(this, "Close", primary = false).apply { setOnClickListener { sheets.close() } },
            ))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_SOUND, REQ_PICTURE -> io.execute {
                val result = if (requestCode == REQ_SOUND) media.importSound(uri) else media.importPicture(uri)
                main.post {
                    when (result) {
                        is MediaLibrary.ImportResult.Failed -> toaster.show(result.message)
                        is MediaLibrary.ImportResult.Added -> {
                            val item = result.item
                            search.add(if (item.kind == MediaItem.Kind.PICTURE) "scenes" else "sounds", SearchInput(item.id, "user-media", item.name))
                            update { if (item.kind == MediaItem.Kind.PICTURE) it.copy(scene = item.id) else it.copy(sound = item.id) }
                            settingsScreen.render()
                            toaster.show("Added ${item.name}")
                        }
                    }
                }
            }
            REQ_CONTACT -> try {
                contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, COLUMN_DISPLAY_NAME, COLUMN_LOOKUP), null, null, null)?.use { c ->
                    if (c.moveToFirst()) addContact(c.getString(1) ?: "", c.getString(0) ?: "", c.getString(2))
                }
            } catch (e: RuntimeException) { toaster.show("Couldn't read that contact. Type the number instead.") }
        }
    }

    // ---------------- Back / Escape ----------------

    private fun handleBack() {
        when {
            sheets.isOpen -> sheets.dismiss()
            current === settingsScreen -> close()
            current === done -> finishDone()
            else -> moveTaskToBack(true)   // a running session keeps going in the background
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = handleBack()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true   // focused view first (e.g. search field clears itself)
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP && (sheets.isOpen || current === settingsScreen)) {
            handleBack(); return true
        }
        return false
    }

    // ---------------- Search index ----------------

    private fun indexEverything() {
        search.define("sounds", mapOf("title" to FieldKind.TITLE))
        search.define("scenes", mapOf("title" to FieldKind.TITLE))
        search.define("trustedContacts", mapOf("name" to FieldKind.NAME, "phone" to FieldKind.PHONE))
        search.load("sounds", Catalog.SOUNDS.map { SearchInput(it.id, "sound", it.name) } + SearchInput(Catalog.RANDOM, "sound", "Random") +
            media.sounds().map { SearchInput(it.id, "user-sound", it.name) })
        search.load("scenes", Catalog.SCENES.map { SearchInput(it.id, "scene", it.name) } + SearchInput(Catalog.RANDOM, "scene", "Random") +
            media.pictures().map { SearchInput(it.id, "picture", it.name) })
        search.load("trustedContacts", settings.contacts.map(::contactInput))
    }

    private fun contactInput(c: app.focusfriend.core.TrustedContact) =
        SearchInput(c.id, "trusted-contact", c.name, c.number, mapOf("name" to c.name, "phone" to c.number))

    private companion object {
        const val REQ_SOUND = 11; const val REQ_PICTURE = 12; const val REQ_CONTACT = 13; const val REQ_CONTACTS = 14; const val REQ_NOTIFY = 15
        // Declared on protected ContactsContract interfaces, so named here.
        const val COLUMN_DISPLAY_NAME = "display_name"
        const val COLUMN_LOOKUP = "lookup"
    }
}
