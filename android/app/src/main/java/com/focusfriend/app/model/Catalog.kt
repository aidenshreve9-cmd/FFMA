package com.focusfriend.app.model

/** Everything the app offers, and the fixed rules it follows. */
object Catalog {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60
    const val DEFAULT_MINUTES = 15

    /** Android's repeat-caller window: a second call from the same number within this many minutes rings. */
    const val REPEAT_CALL_WINDOW_MIN = 15

    /** Not approved yet: the donation sheet says so instead of opening anything. */
    val DONATION_LINK: String? = null

    const val DEFAULT_SOUND = "white"
    const val DEFAULT_SCENE = "quantum"
    const val RANDOM = "random"
    const val NO_SOUND = "none"

    val SOUNDS = listOf(
        NoiseSound("white", "White Noise", "FLAT", 0.50f),
        NoiseSound("pink", "Pink Noise", "SOFT 1/F", 0.62f),
        NoiseSound("brown", "Brown Noise", "DEEP 1/F²", 0.70f),
        NoiseSound("green", "Green Noise", "~500 HZ", 1.3f),
        NoiseSound("grey", "Grey Noise", "EVEN EAR", 0.55f),
        NoiseSound("blue", "Blue Noise", "BRIGHT", 0.30f),
        NoiseSound("violet", "Violet Noise", "AIRY", 0.20f),
        NoiseSound("black", "Black Noise", "SUB-BASS", 2.2f),
    )

    val SCENES = listOf(
        Scene("quantum", "Quantum Nebula"),
        Scene("galaxy", "Spiral Galaxy"),
        Scene("horizon", "Event Horizon"),
        Scene("aurora", "Aurora Veil"),
        Scene("dust", "Cosmic Dust"),
        Scene("nursery", "Stellar Nursery"),
        Scene("web", "Dark Matter Web"),
        Scene("void", "Ethereal Void"),
    )

    val QUOTES = listOf(
        Quote("Each man lives only this present, this momentary thing.", "Marcus Aurelius"),
        Quote("Do not imagine this, that you will recover it when you choose.", "Epictetus"),
        Quote("To learn, and in due time to practise what you have learned — is that not a pleasure?", "Confucius"),
        Quote("Learning without thought is dark; thought without learning is perilous.", "Confucius"),
        Quote("Stillness should be guarded with unwearying vigour.", "Laozi"),
        Quote("Hold fast to these few things only.", "Marcus Aurelius"),
        Quote("To learn without tiring of it, to teach others without wearying.", "Confucius"),
        Quote("Be quick in what must be done and careful in what you say.", "Confucius"),
        Quote("Is there any part of life excepted, to which attention does not extend?", "Epictetus"),
        Quote("The task at hand.", "Marcus Aurelius"),
    )

    fun sound(id: String): NoiseSound? = SOUNDS.firstOrNull { it.id == id }
    fun scene(id: String): Scene? = SCENES.firstOrNull { it.id == id }
}

data class NoiseSound(val id: String, val name: String, val kind: String, val gain: Float)
data class Scene(val id: String, val name: String)
data class Quote(val text: String, val author: String)
