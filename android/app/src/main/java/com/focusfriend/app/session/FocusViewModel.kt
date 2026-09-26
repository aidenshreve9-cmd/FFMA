package com.focusfriend.app.session

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusfriend.app.audio.SoundChoice
import com.focusfriend.app.audio.SoundEngine
import com.focusfriend.app.dnd.DndController
import com.focusfriend.app.model.AppSettings
import com.focusfriend.app.model.Catalog
import com.focusfriend.app.model.Contact
import com.focusfriend.app.model.MediaKind
import com.focusfriend.app.model.MediaLibrary
import com.focusfriend.app.model.Quote
import com.focusfriend.app.model.SettingsCodec
import com.focusfriend.app.model.SettingsStore
import com.focusfriend.app.model.UserMedia
import com.focusfriend.app.model.digitsOf
import com.focusfriend.app.model.newId
import com.focusfriend.app.search.FieldKind
import com.focusfriend.app.search.LocalSearch
import com.focusfriend.app.search.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

enum class Screen { WELCOME, HOME, SETTINGS, SESSION, DONE }

enum class Flow { IDLE, PERMISSION_REQUIRED, PERMISSION_BARRIER, ACTIVE, CONFIRM_END, COMPLETED }

enum class Sheet { PERMISSION, BARRIER, CONFIRM_END, DONATE }

data class FocusSession(val minutes: Int, val startedAt: Long, val endAt: Long)

data class ToastMessage(val text: String, val id: Long)

/** What to show behind a session: one of the eight painted scenes, or your own picture. */
sealed interface SceneChoice {
    val id: String
    val name: String

    data class Painted(override val id: String, override val name: String) : SceneChoice
    data class Picture(override val id: String, override val name: String, val file: File) : SceneChoice
}

object Collections {
    const val SOUNDS = "sounds"
    const val SCENES = "scenes"
    const val CONTACTS = "trustedContacts"
}

class FocusViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val library = MediaLibrary(app)
    val sound = SoundEngine { showToast(it) }
    val search = LocalSearch().apply {
        define(Collections.SOUNDS, mapOf("title" to FieldKind.TITLE))
        define(Collections.SCENES, mapOf("title" to FieldKind.TITLE))
        define(Collections.CONTACTS, mapOf("name" to FieldKind.NAME, "phone" to FieldKind.PHONE))
    }

    var screen by mutableStateOf(Screen.WELCOME)
        private set
    var flow by mutableStateOf(Flow.IDLE)
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set
    var media by mutableStateOf(library.list())
        private set
    var settings by mutableStateOf(SettingsCodec.validate(store.load(), library.list().map { it.id }.toSet()))
        private set
    var minutes by mutableIntStateOf(Catalog.DEFAULT_MINUTES)
        private set
    var session by mutableStateOf<FocusSession?>(null)
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var silenced by mutableStateOf(false)
        private set
    var sessionSound by mutableStateOf<SoundChoice?>(null)
        private set
    var sessionScene by mutableStateOf<SceneChoice>(SceneChoice.Painted(Catalog.DEFAULT_SCENE, Catalog.SCENES[0].name))
        private set
    var finishedNaturally by mutableStateOf(true)
        private set
    var quote by mutableStateOf<Quote>(Catalog.QUOTES[0])
        private set
    var toast by mutableStateOf<ToastMessage?>(null)
        private set
    var dndGranted by mutableStateOf(DndController.hasAccess(app))
        private set
    var hintVisible by mutableStateOf(true)
        private set

    private var awaitingAccess = false
    private var ticker: Job? = null
    private val context get() = getApplication<Application>()

    init {
        reindex()
        (resolveSound() as? SoundChoice.Noise)?.let { sound.warmUp(it) }
    }

    val keepScreenOn: Boolean get() = flow == Flow.ACTIVE || flow == Flow.CONFIRM_END
    val remainingMs: Long get() = session?.let { max(0L, it.endAt - now) } ?: 0L
    val minutesLeft: Int get() = max(1, ceil(remainingMs / 60000.0).toInt())

    /* ---------------- Screens ---------------- */

    fun start() {
        if (screen == Screen.WELCOME) screen = Screen.HOME
    }

    fun openSettings() {
        if (flow == Flow.IDLE && screen == Screen.HOME) screen = Screen.SETTINGS
    }

    /** Leaving Settings stops any preview at once (a 30 ms fade only to avoid a click). */
    fun closeSettings() {
        sound.release(0.03f)
        screen = Screen.HOME
    }

    fun chooseMinutes(m: Int) {
        minutes = m.coerceIn(Catalog.MIN_MINUTES, Catalog.MAX_MINUTES)
    }

    fun hideHint() {
        hintVisible = false
    }

    /** Android back button. Returns false when the app should just go to the background. */
    fun back(): Boolean {
        when {
            sheet == Sheet.CONFIRM_END -> keepFocusing()
            sheet == Sheet.BARRIER -> barrierBack()
            sheet == Sheet.PERMISSION -> permissionDeny()
            sheet == Sheet.DONATE -> sheet = null
            screen == Screen.SETTINGS -> closeSettings()
            screen == Screen.DONE -> done()
            else -> return false // Home, or a running session: Focus keeps going in the background
        }
        return true
    }

    /* ---------------- Permission ---------------- */

    fun requestStart() {
        if (flow != Flow.IDLE) return
        hintVisible = false
        if (!DndController.hasAccess(context)) {
            flow = Flow.PERMISSION_REQUIRED
            sheet = Sheet.PERMISSION
            return
        }
        startSession()
    }

    fun reviewPermission() {
        sheet = Sheet.PERMISSION
    }

    fun permissionAllow() {
        dndGranted = DndController.hasAccess(context)
        if (dndGranted) {
            sheet = null
            if (flow == Flow.PERMISSION_REQUIRED) startSession() else flow = Flow.IDLE
            return
        }
        awaitingAccess = true
        openDndSettings()
    }

    fun permissionDeny() {
        if (flow == Flow.PERMISSION_REQUIRED) {
            flow = Flow.PERMISSION_BARRIER
            sheet = Sheet.BARRIER
        } else {
            sheet = null
        }
    }

    fun barrierReview() {
        flow = Flow.PERMISSION_REQUIRED
        sheet = Sheet.PERMISSION
    }

    fun barrierBack() {
        flow = Flow.IDLE
        sheet = null
    }

    /** Called whenever the app comes back to the foreground, e.g. from Android's access page. */
    fun onResume() {
        dndGranted = DndController.hasAccess(context)
        tick()
        if (!awaitingAccess) return
        awaitingAccess = false
        if (flow == Flow.PERMISSION_REQUIRED) {
            if (dndGranted) startSession() else { flow = Flow.PERMISSION_BARRIER; sheet = Sheet.BARRIER }
        } else if (dndGranted) {
            sheet = null
        }
    }

    private fun openDndSettings() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(flags))
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags))
            } catch (e2: ActivityNotFoundException) {
                awaitingAccess = false
                showToast("Couldn't open Android's settings.")
            }
        }
    }

    /* ---------------- Session ---------------- */

    private fun startSession() {
        sheet = null
        val startedAt = System.currentTimeMillis()
        val s = FocusSession(minutes, startedAt, startedAt + minutes * 60_000L)
        session = s
        now = startedAt
        flow = Flow.ACTIVE
        silenced = DndController.begin(context, settings.alarmSafety, s.endAt)
        sessionScene = resolveScene()
        sessionSound = resolveSound()
        screen = Screen.SESSION
        sound.startSession(sessionSound)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(1000)
            }
        }
    }

    /** Uses the absolute end time, so it catches up after the app was in the background. */
    fun tick() {
        now = System.currentTimeMillis()
        val s = session ?: return
        if ((flow == Flow.ACTIVE || flow == Flow.CONFIRM_END) && now >= s.endAt) finish(natural = true)
    }

    fun tapTimer() {
        if (flow != Flow.ACTIVE) return
        flow = Flow.CONFIRM_END
        sheet = Sheet.CONFIRM_END
    }

    fun keepFocusing() {
        if (flow != Flow.CONFIRM_END) return
        flow = Flow.ACTIVE
        sheet = null
    }

    fun endEarly() {
        if (flow == Flow.CONFIRM_END) finish(natural = false)
    }

    /** Completion and cleanup run exactly once. */
    private fun finish(natural: Boolean) {
        if (session == null || (flow != Flow.ACTIVE && flow != Flow.CONFIRM_END)) return
        ticker?.cancel()
        ticker = null
        DndController.restore(context)
        sound.release(0.9f)
        sheet = null
        flow = Flow.COMPLETED
        finishedNaturally = natural
        if (natural) sound.chime()
        quote = Catalog.QUOTES.random()
        screen = Screen.DONE
    }

    fun done() {
        session = null
        flow = Flow.IDLE
        minutes = Catalog.DEFAULT_MINUTES
        screen = Screen.HOME
    }

    /** The app is being closed for good: a session can't outlive it, so put the phone back. */
    fun onAppClosing() {
        DndController.restore(context)
    }

    /* ---------------- Choices ---------------- */

    fun soundChoiceFor(id: String): SoundChoice? {
        var pick = id
        if (pick == Catalog.RANDOM) {
            val pool = Catalog.SOUNDS.map { it.id } + media.filter { it.kind == MediaKind.SOUND }.map { it.id }
            pick = pool.random()
        }
        Catalog.sound(pick)?.let { return SoundChoice.Noise(it.id, it.name, it.gain) }
        return media.firstOrNull { it.id == pick && it.kind == MediaKind.SOUND }?.let { SoundChoice.UserFile(it.id, it.name, it.file) }
    }

    /** null means no sound was chosen, so Focus is silent. */
    private fun resolveSound(): SoundChoice? {
        if (settings.audio == Catalog.NO_SOUND) return null
        val white = Catalog.SOUNDS[0]
        return soundChoiceFor(settings.audio) ?: SoundChoice.Noise(white.id, white.name, white.gain)
    }

    fun sceneChoiceFor(id: String): SceneChoice? {
        var pick = id
        if (pick == Catalog.RANDOM) {
            val pool = Catalog.SCENES.map { it.id } + media.filter { it.kind == MediaKind.PICTURE }.map { it.id }
            pick = pool.random()
        }
        Catalog.scene(pick)?.let { return SceneChoice.Painted(it.id, it.name) }
        return media.firstOrNull { it.id == pick && it.kind == MediaKind.PICTURE }?.let { SceneChoice.Picture(it.id, it.name, it.file) }
    }

    private fun resolveScene(): SceneChoice =
        sceneChoiceFor(settings.scene) ?: SceneChoice.Painted(Catalog.DEFAULT_SCENE, Catalog.SCENES[0].name)

    /* ---------------- Settings ---------------- */

    private fun update(s: AppSettings) {
        settings = s
        if (!store.save(s)) showToast("Couldn't save settings on this device.")
    }

    /**
     * Tapping a sound selects it and plays a 5-second preview; tapping the chosen sound again
     * fades it out and deselects it (Focus is then silent). Choosing from search always selects.
     */
    fun tapSound(id: String, fromSearch: Boolean = false) {
        val deselect = settings.audio == id && !fromSearch
        update(settings.copy(audio = if (deselect) Catalog.NO_SOUND else id))
        if (deselect) sound.fadeOutPreview() else soundChoiceFor(id)?.let { sound.preview(it) }
    }

    fun selectScene(id: String) = update(settings.copy(scene = id))

    fun toggleAlarmSafety() = update(settings.copy(alarmSafety = !settings.alarmSafety))

    fun toggleTrusted() = update(settings.copy(trustedOn = !settings.trustedOn))

    /** Returns an error message, or null when the contact was added. */
    fun addContact(rawName: String, rawNumber: String): String? {
        val number = rawNumber.trim()
        val name = rawName.trim().ifEmpty { number }
        if (!PHONE_INPUT.matches(number) || digitsOf(number).length < 3) return "Add a name and a phone number."
        val normalized = digitsOf(number)
        if (settings.contacts.any { TextNormalizer.phonesMatch(it.normalized, normalized) }) return "That number is already a Trusted Contact."
        if (settings.contacts.size >= SettingsCodec.MAX_CONTACTS) return "You can add up to 50 Trusted Contacts."
        update(settings.copy(contacts = settings.contacts + Contact(newId(), name.take(40), number, normalized)))
        reindex()
        showToast("Added ${name.take(40)}")
        return null
    }

    fun removeContact(id: String) {
        val gone = settings.contacts.firstOrNull { it.id == id } ?: return
        update(settings.copy(contacts = settings.contacts - gone))
        reindex()
        showToast("Removed ${gone.name}")
    }

    /** A contact chosen with Android's contact picker (a phone-number row, readable without extra permission). */
    fun addPickedContact(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null, null, null,
                    )?.use { c -> if (c.moveToFirst()) (c.getString(0).orEmpty()) to (c.getString(1).orEmpty()) else null }
                } catch (e: Exception) {
                    null
                }
            }
            if (picked == null || picked.second.isBlank()) {
                showToast("That contact has no phone number.")
                return@launch
            }
            addContact(picked.first, picked.second)?.let { showToast(it) }
        }
    }

    fun addMedia(uri: Uri, kind: MediaKind) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { library.add(uri, kind) }
            result.onSuccess { item ->
                media = library.list()
                reindex()
                update(if (kind == MediaKind.SOUND) settings.copy(audio = item.id) else settings.copy(scene = item.id))
                showToast("Added ${item.name}")
            }.onFailure { e ->
                showToast(e.message ?: "Couldn't save that file on this device.")
            }
        }
    }

    fun removeMedia(item: UserMedia) {
        if (item.kind == MediaKind.SOUND) sound.release(0.03f) // its preview can't outlive the file
        if (!library.remove(item.id)) {
            showToast("Couldn't remove that file.")
            return
        }
        media = library.list()
        update(SettingsCodec.validate(settings, media.map { it.id }.toSet()))
        reindex()
    }

    fun showDonate() {
        sheet = Sheet.DONATE
    }

    fun closeDonate() {
        if (sheet == Sheet.DONATE) sheet = null
    }

    /* ---------------- Messages ---------------- */

    fun showToast(text: String) {
        toast = ToastMessage(text, SystemClock.uptimeMillis())
    }

    fun clearToast(t: ToastMessage) {
        if (toast == t) toast = null
    }

    private fun reindex() {
        search.load(Collections.SOUNDS,
            Catalog.SOUNDS.map { Triple(it.id, it.name, mapOf("title" to it.name)) } +
                Triple(Catalog.RANDOM, "Random", mapOf("title" to "Random")) +
                media.filter { it.kind == MediaKind.SOUND }.map { Triple(it.id, it.name, mapOf("title" to it.name)) })
        search.load(Collections.SCENES,
            Catalog.SCENES.map { Triple(it.id, it.name, mapOf("title" to it.name)) } +
                Triple(Catalog.RANDOM, "Random", mapOf("title" to "Random")) +
                media.filter { it.kind == MediaKind.PICTURE }.map { Triple(it.id, it.name, mapOf("title" to it.name)) })
        search.load(Collections.CONTACTS, settings.contacts.map { Triple(it.id, it.name, mapOf("name" to it.name, "phone" to it.number)) })
    }

    override fun onCleared() {
        ticker?.cancel()
        sound.shutdown()
    }

    private companion object {
        val PHONE_INPUT = Regex("""^[+()\d\s.\-]{3,24}$""")
    }
}
