package app.focusfriend.core

import app.focusfriend.core.search.SearchNormalizer

/** One Trusted Contact: local id, name, the number as the person typed it, and its normalized digits. */
data class TrustedContact(
    val id: String,
    val name: String,
    val number: String,
    val normalized: String,
    /** Android ContactsContract lookup key when picked from (or matched to) the address book. */
    val lookupKey: String? = null,
)

/** Local list of up to 50 Trusted Contacts with duplicate detection across number formats. */
class TrustedContactBook(initial: List<TrustedContact> = emptyList()) {
    sealed interface AddResult {
        data class Added(val contact: TrustedContact) : AddResult
        data object InvalidNumber : AddResult
        data object Duplicate : AddResult
        data object Full : AddResult
    }

    private val items = ArrayList<TrustedContact>()
    val contacts: List<TrustedContact> get() = items.toList()
    val size get() = items.size

    init { initial.forEach { c -> sanitize(c)?.let { if (!isDuplicate(it.normalized) && items.size < MAX) items += it } } }

    fun add(name: String?, number: String?, newId: () -> String, lookupKey: String? = null): AddResult {
        val num = number?.trim().orEmpty()
        val digits = SearchNormalizer.normalizePhone(num)
        if (!VALID_NUMBER.matches(num) || digits.length < 3) return AddResult.InvalidNumber
        if (isDuplicate(digits)) return AddResult.Duplicate
        if (items.size >= MAX) return AddResult.Full
        val display = name?.trim().orEmpty().ifEmpty { num }.take(MAX_NAME)
        val contact = TrustedContact(newId(), display, num, digits, lookupKey)
        items += contact
        return AddResult.Added(contact)
    }

    fun remove(id: String): TrustedContact? {
        val i = items.indexOfFirst { it.id == id }
        return if (i >= 0) items.removeAt(i) else null
    }

    private fun isDuplicate(digits: String) = items.any { SearchNormalizer.phonesMatch(it.normalized, digits) }

    companion object {
        const val MAX = 50
        const val MAX_NAME = 40
        private val VALID_NUMBER = Regex("""^[+()\d\s.\-]{3,24}$""")

        /** Repairs older or corrupted saved entries; returns null when unusable. */
        fun sanitize(c: TrustedContact?): TrustedContact? {
            if (c == null || c.id.isBlank()) return null
            val digits = SearchNormalizer.normalizePhone(c.number)
            if (digits.length < 3) return null
            return c.copy(name = c.name.ifBlank { c.number }.take(MAX_NAME), normalized = digits)
        }
    }
}

/**
 * Everything Focus Friend remembers. The timer duration is deliberately absent:
 * it is never persisted, and every launch starts at 15 minutes.
 */
data class FocusSettings(
    val sound: String = Catalog.DEFAULT_SOUND,
    val scene: String = Catalog.DEFAULT_SCENE,
    val alarmSafety: Boolean = true,
    val trustedContactsOn: Boolean = false,
    val contacts: List<TrustedContact> = emptyList(),
    /** Whether the person has been shown the permission sheet at least once. */
    val permissionSheetSeen: Boolean = false,
) {
    /** Falls back to defaults for anything that no longer exists (deleted media, unknown ids). */
    fun validated(userSoundIds: Set<String>, userPictureIds: Set<String>): FocusSettings {
        val soundOk = sound == Catalog.RANDOM || Catalog.SOUNDS.any { it.id == sound } || sound in userSoundIds
        val sceneOk = scene == Catalog.RANDOM || Catalog.SCENES.any { it.id == scene } || scene in userPictureIds
        return copy(
            sound = if (soundOk) sound else Catalog.DEFAULT_SOUND,
            scene = if (sceneOk) scene else Catalog.DEFAULT_SCENE,
            contacts = TrustedContactBook(contacts).contacts,
        )
    }
}

/**
 * What a Focus session asks the phone to do. The Android layer maps this onto a
 * Do Not Disturb rule; the browser preview can only describe it.
 */
data class InterruptionPolicy(
    val blockNotificationSounds: Boolean = true,
    val blockNotificationPopups: Boolean = true,
    val blockNotificationVibration: Boolean = true,
    val blockOtherCalls: Boolean = true,
    /** Emergency alerts are never blocked (the platform guarantees this for Do Not Disturb). */
    val allowEmergencyAlerts: Boolean = true,
    /** The second call from the same number within [repeatCallerWindowMinutes] rings. */
    val allowRepeatCallers: Boolean = true,
    val repeatCallerWindowMinutes: Int = 15,
    val allowAlarms: Boolean,
    /** Normalized numbers that may call and message; empty when Trusted Contacts is off. */
    val trustedNumbers: List<String>,
) {
    val allowTrustedContacts get() = trustedNumbers.isNotEmpty()

    companion object {
        fun from(settings: FocusSettings) = InterruptionPolicy(
            allowAlarms = settings.alarmSafety,
            trustedNumbers = if (settings.trustedContactsOn) settings.contacts.map { it.normalized } else emptyList(),
        )
    }
}
