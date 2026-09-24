package app.focusfriend.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.focusfriend.core.Catalog
import app.focusfriend.core.FocusSettings
import app.focusfriend.core.TrustedContact
import app.focusfriend.core.search.SearchController
import app.focusfriend.data.MediaItem

/** Everything Settings needs from the activity. Keeps platform calls out of the view code. */
interface SettingsHost {
    val settings: FocusSettings
    val search: SearchController
    fun update(transform: (FocusSettings) -> FocusSettings)
    fun userSounds(): List<MediaItem>
    fun userPictures(): List<MediaItem>
    fun pictureThumb(item: MediaItem, px: Int): android.graphics.Bitmap?
    fun pickSound()
    fun pickPicture()
    fun pickContact()
    fun addContact(name: String, number: String): String?
    fun removeContact(id: String)
    fun removeMedia(id: String)
    /** A sound row was tapped: selects it and plays a 5-second preview, or deselects it if it was chosen. Returns the new choice. */
    fun tapSound(id: String, fromSearch: Boolean): String
    /** The atmosphere to show for [id] (Random resolved): scene id and, for the person's own picture, the picture. */
    fun atmosphere(id: String): Pair<String, android.graphics.Bitmap?>
    fun trustedContactsSwitched(on: Boolean)
    fun contactsAccessGranted(): Boolean
    fun doNotDisturbAllowed(): Boolean
    fun showPermissionSheet()
    fun showDonationSheet()
    fun close()
}

/** Settings, in the required order: Sound, Atmosphere, Trusted Contacts, Alarm Safety, Permissions. Its background is the chosen atmosphere. */
@SuppressLint("ViewConstructor")
class SettingsScreen(private val c: Context, private val host: SettingsHost) : FrameLayout(c) {
    private val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20f), dpi(4f), dpi(20f), dpi(30f)) }
    private val scroll = ScrollView(c).apply { addView(body); isFillViewport = true }

    private val soundList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; background = GlassDrawable(c) }
    private val sceneGrid = GridLayout(c).apply { columnCount = 3 }
    private val contactList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private val soundSearch = SearchField(c, "Search sounds…", "Search sounds")
    private val sceneSearch = SearchField(c, "Search atmospheres…", "Search atmospheres")
    private val contactSearch = SearchField(c, "Search contacts…", "Search trusted contacts")
    private val soundEmpty = emptyState(c)
    private val sceneEmpty = emptyState(c)
    private val contactEmpty = emptyState(c)
    private val trustSwitch = EmberSwitch(c, "Trusted Contacts")
    private val alarmSwitch = EmberSwitch(c, "Alarm Safety")
    private val trustState = stateLabel(c)
    private val alarmState = stateLabel(c)
    private val trustNote = note(c, "")
    private val permPill = label(c, "", 10.5f, Palette.WARN, Fonts.mono(c, 500)).apply { letterSpacing = .08f }
    private val nameField = input(c, "Name", "Contact name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
    private val numberField = input(c, "Phone number", "Phone number", InputType.TYPE_CLASS_PHONE)

    private val sounds: SearchableSection = SearchableSection("sounds", soundSearch, soundList, soundEmpty, "sound", "sounds", threshold = 13,
        onSearchPick = { id -> select(sounds, host.tapSound(id, fromSearch = true)) })
    private val scenes = SearchableSection("scenes", sceneSearch, sceneGrid, sceneEmpty, "atmosphere", "atmospheres", threshold = 13)

    // The chosen atmosphere behind everything, crossfading when it changes, dimmed for readability.
    private val bgBack = SceneView(c).apply { dimmed = false }
    private val bgFront = SceneView(c).apply { dimmed = false; alpha = 0f }
    private var bgFor: String? = null
    private val contacts = SearchableSection("trustedContacts", contactSearch, contactList, contactEmpty, "contact", "contacts", threshold = 1)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(bgBack, LayoutParams(-1, -1)); addView(bgFront, LayoutParams(-1, -1))
        addView(View(c).apply { setBackgroundColor(0x9E03000C.toInt()); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, LayoutParams(-1, -1))
        val head = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(20f), dpi(14f), dpi(20f), dpi(12f))
            addView(ImageButton(c).apply {
                contentDescription = "Back to Focus"; setImageDrawable(BackDrawable(c)); background = GlassDrawable(c, 999f, prism = false)
                setOnClickListener { host.close() }
            }, LinearLayout.LayoutParams(dpi(48f), dpi(48f)))
            addView(label(c, "Settings", 26f, Palette.TEXT, Fonts.serif(c)).apply { accessibilityHeading(this) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(12f) })
        }
        addView(LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(head); addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)) }, LayoutParams(-1, -1))

        section("Sound")
        body.addView(soundSearch, lp(bottom = 12f)); body.addView(soundList); body.addView(soundEmpty)
        body.addView(addButton(c, "＋ Add your own sound") { host.pickSound() }, lp(top = 12f))
        body.addView(note(c, "Use your phone's volume buttons to change how loud it is."), lp(top = 10f))

        section("Atmosphere")
        body.addView(sceneSearch, lp(bottom = 12f)); body.addView(sceneGrid); body.addView(sceneEmpty)
        body.addView(addButton(c, "＋ Add your own picture") { host.pickPicture() }, lp(top = 12f))

        section("Trusted Contacts")
        body.addView(toggleCard(c, "Trusted Contacts", trustState,
            "When on, these people can call and message you during Focus.", trustSwitch))
        body.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; background = GlassDrawable(c); setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
            addView(contactSearch, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(12f) })
            addView(contactList); addView(contactEmpty)
            addView(LinearLayout(c).apply {
                addView(nameField, LinearLayout.LayoutParams(0, -2, 1f))
                addView(numberField, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(8f) })
                addView(pillButton(c, "Add", primary = true).apply { minWidth = dpi(64f); setOnClickListener { submitContact() } },
                    LinearLayout.LayoutParams(-2, dpi(48f)).apply { marginStart = dpi(8f) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        }, lp(top = 12f))
        body.addView(addButton(c, "＋ Choose from contacts") { host.pickContact() }, lp(top = 12f))
        body.addView(trustNote, lp(top = 10f))
        numberField.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { submitContact(); true } else false }
        numberField.imeOptions = EditorInfo.IME_ACTION_DONE

        section("Alarm Safety")
        body.addView(toggleCard(c, "Alarm Safety", alarmState,
            "Allow alarms to interrupt Focus. Alarms may be used for medication, emergencies, or other safety or health-related purposes.", alarmSwitch))

        section("Permissions")
        body.addView(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL; background = GlassDrawable(c); setPadding(dpi(16f), dpi(12f), dpi(12f), dpi(12f))
            addView(permPill)
            addView(label(c, "Do Not Disturb access", 14f, Palette.TEXT, Fonts.sans(c, 500)), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(12f) })
            addView(TextView(c).apply {
                text = "Review"; setTextColor(Palette.ROSE); typeface = Fonts.sans(c, 600); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minHeight = dpi(48f); gravity = Gravity.CENTER; setPadding(dpi(8f), 0, dpi(8f), 0)
                isClickable = true; isFocusable = true; setOnClickListener { host.showPermissionSheet() }
                accessibilityDelegate = ButtonRole
            })
        }, lp(top = 0f))
        body.addView(note(c, "Focus uses its own Do Not Disturb rule and switches it off when the session ends, so your own settings come back exactly as they were."), lp(top = 10f))

        body.addView(TextView(c).apply {
            text = "Buy me a coffee"; setTextColor(Palette.FAINT); typeface = Fonts.sans(c); paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            gravity = Gravity.CENTER; minHeight = dpi(48f); isClickable = true; isFocusable = true
            setOnClickListener { host.showDonationSheet() }; accessibilityDelegate = ButtonRole
        }, lp(top = 24f).apply { gravity = Gravity.CENTER_HORIZONTAL })

        trustSwitch.onChange = { on -> host.update { it.copy(trustedContactsOn = on) }; host.trustedContactsSwitched(on); renderTrust() }
        alarmSwitch.onChange = { on -> host.update { it.copy(alarmSafety = on) }; renderAlarm() }
    }

    fun render() {
        if (bgFor != host.settings.scene) showAtmosphere(host.settings.scene, animate = isShown)
        renderSounds(); renderScenes(); renderContacts(); renderTrust(); renderAlarm(); renderPermissions()
    }

    fun setAnimating(on: Boolean) { bgBack.setAnimating(on); bgFront.setAnimating(on && bgFront.alpha > 0f) }

    /** Shows [id] behind Settings; with motion, the new atmosphere fades in over the old one. */
    private fun showAtmosphere(id: String, animate: Boolean) {
        bgFor = id
        val (scene, picture) = host.atmosphere(id)
        bgFront.animate().cancel()
        if (!animate || !Motion.enabled) { bgBack.show(scene, picture); bgFront.alpha = 0f; bgFront.setAnimating(false); return }
        bgFront.show(scene, picture); bgFront.setAnimating(true)
        bgFront.animate().alpha(1f).setDuration(700).withEndAction {
            bgBack.show(scene, picture); bgFront.alpha = 0f; bgFront.setAnimating(false)
        }.start()
    }

    private fun section(title: String) { body.addView(eyebrow(c, title), lp(top = 22f, bottom = 12f)) }

    // ---------- Sound ----------
    private fun renderSounds() {
        val rows = LinkedHashMap<String, View>()
        (Catalog.SOUNDS.map { Triple(it.id, it.name, it.kind) } + Triple(Catalog.RANDOM, "Random", "EACH SESSION") +
            host.userSounds().map { Triple(it.id, it.name, "YOURS") }).forEach { (id, name, kind) ->
            rows[id] = choiceRow(c, id, name, kind, host.settings.sound == id,
                onSelect = { select(sounds, host.tapSound(id, fromSearch = false)) },
                onRemove = if (id.startsWith(Catalog.USER_SOUND_PREFIX)) ({ host.removeMedia(id) }) else null)
        }
        sounds.setRows(rows)
    }

    // ---------- Atmosphere ----------
    private fun renderScenes() {
        val rows = LinkedHashMap<String, View>()
        val tile = dpi(100f)
        Catalog.SCENES.forEach { s ->
            rows[s.id] = sceneTile(c, s.id, s.name, ScenePainter.thumbnail(s.id, tile, (tile * 1.25f).toInt(), resources.displayMetrics.density), host.settings.scene == s.id,
                onSelect = { pickScene(s.id) }, onRemove = null)
        }
        rows[Catalog.RANDOM] = sceneTile(c, Catalog.RANDOM, "Random", null, host.settings.scene == Catalog.RANDOM,
            onSelect = { pickScene(Catalog.RANDOM) }, onRemove = null)
        host.userPictures().forEach { p ->
            rows[p.id] = sceneTile(c, p.id, p.name, host.pictureThumb(p, tile), host.settings.scene == p.id,
                onSelect = { pickScene(p.id) }, onRemove = { host.removeMedia(p.id) })
        }
        scenes.setRows(rows)
    }

    private fun pickScene(id: String) {
        select(scenes, id); host.update { it.copy(scene = id) }
        showAtmosphere(id, animate = true)
    }

    // ---------- Trusted Contacts ----------
    private fun renderContacts() {
        val rows = LinkedHashMap<String, View>()
        host.settings.contacts.forEach { ct -> rows[ct.id] = contactRow(c, ct) { host.removeContact(ct.id) } }
        contacts.setRows(rows)
        if (rows.isEmpty()) {
            contactEmpty.removeAllViews()
            contactEmpty.addView(label(c, "No trusted contacts yet", 14f, Palette.TEXT, Fonts.sans(c, 600)))
            contactEmpty.addView(label(c, "Add someone you want to be able to reach you during Focus.", 13.5f, Palette.MUTED))
            contactEmpty.visibility = View.VISIBLE
        }
    }

    private fun renderTrust() {
        val s = host.settings; val n = s.contacts.size
        trustSwitch.set(s.trustedContactsOn); trustState.text = if (s.trustedContactsOn) "ON" else "OFF"
        trustState.setTextColor(if (s.trustedContactsOn) Palette.COPPER else Palette.FAINT)
        contactList.alpha = if (s.trustedContactsOn) 1f else .6f
        trustNote.text = when {
            !s.trustedContactsOn -> "The Trusted Contacts list is off. During Focus, only emergency alerts and repeat callers get through. Stored only on this phone."
            !host.contactsAccessGranted() -> "Trusted Contacts need access to your contacts so Android can recognise these people. Without it, only emergency alerts and repeat callers get through."
            n == 0 -> "On, but no one is added yet. Stored only on this phone."
            else -> "On: $n ${if (n == 1) "person" else "people"} can reach you during Focus. Android lets starred contacts through as a group, so people you've already starred in Contacts can reach you too. Stored only on this phone."
        }
    }

    private fun renderAlarm() {
        val on = host.settings.alarmSafety
        alarmSwitch.set(on); alarmState.text = if (on) "ON" else "OFF"; alarmState.setTextColor(if (on) Palette.COPPER else Palette.FAINT)
    }

    private fun renderPermissions() {
        val ok = host.doNotDisturbAllowed()
        permPill.text = if (ok) "ALLOWED" else "NOT SET UP"
        permPill.setTextColor(if (ok) Palette.HALO else Palette.WARN)
    }

    private fun submitContact() {
        val err = host.addContact(nameField.text.toString(), numberField.text.toString())
        if (err == null) { nameField.setText(""); numberField.setText(""); nameField.requestFocus() }
    }

    /** Updates selection in place (including rows hidden by a search) so the ember glides instead of the list rebuilding. */
    private fun select(section: SearchableSection, id: String) {
        section.forEachRow { v -> (v.getTag(TAG_SELECTABLE) as? Selectable)?.let { s -> s.selected = s.id == id } }
    }

    /** Local search-as-you-type over one Settings list. Never on Home. Filters in place, ranked. */
    private inner class SearchableSection(
        val collection: String, val field: SearchField, val container: ViewGroup, val empty: LinearLayout,
        val singular: String, val plural: String, val threshold: Int,
        /** What the keyboard's search action does with the top result (default: tap it). */
        val onSearchPick: ((String) -> Unit)? = null,
    ) {
        private val rows = LinkedHashMap<String, View>()
        private val handler = Handler(Looper.getMainLooper())
        private var pending: String? = null
        private val speak = Runnable { pending?.let { field.edit.announceForAccessibility(it) } }

        init {
            field.edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun afterTextChanged(s: Editable?) = apply()
            })
            field.edit.setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    val top = firstVisible()
                    val topId = rows.entries.firstOrNull { it.value === top }?.key
                    if (onSearchPick != null && topId != null) onSearchPick.invoke(topId) else top?.performClick()
                    true
                } else false
            }
            field.edit.setOnKeyListener { _, code, e ->
                if (code == KeyEvent.KEYCODE_ESCAPE && e.action == KeyEvent.ACTION_DOWN && field.edit.text.isNotEmpty()) {
                    field.edit.setText(""); field.edit.announceForAccessibility("Search cleared."); true
                } else false
            }
        }

        fun forEachRow(f: (View) -> Unit) = rows.values.forEach(f)

        fun setRows(newRows: LinkedHashMap<String, View>) {
            val fresh = newRows.keys - rows.keys
            rows.clear(); rows.putAll(newRows)
            field.visibility = if (rows.size >= threshold) View.VISIBLE else View.GONE
            if (field.visibility != View.VISIBLE && field.edit.text.isNotEmpty()) field.edit.setText("")
            apply()
            if (rows.size > fresh.size) fresh.forEach { id -> rows[id]?.let(::enter) }
        }

        fun apply() {
            val q = field.edit.text.toString()
            if (q.isBlank() || field.visibility != View.VISIBLE) {
                show(rows.values.toList())
                empty.visibility = View.GONE; handler.removeCallbacks(speak); return
            }
            val results = host.search.search(collection, q, 200)
            show(results.mapNotNull { rows[it.item.id] })
            if (results.isEmpty()) showEmpty(q) else empty.visibility = View.GONE
            pending = if (results.isNotEmpty()) "${results.size} matching ${if (results.size == 1) singular else plural}." else "No matching $plural."
            // Wait for a pause in typing so the screen reader isn't interrupted on every keystroke.
            handler.removeCallbacks(speak); handler.postDelayed(speak, 900)
        }

        /**
         * Shows exactly [wanted], in order. Rows that drop out slide away; rows that appear fade in.
         * With reduced motion every change is immediate.
         */
        private fun show(wanted: List<View>) {
            val keep = wanted.toSet()
            for (i in container.childCount - 1 downTo 0) {
                val v = container.getChildAt(i)
                if (v in keep) continue
                if (Motion.enabled && v.getTag(TAG_LEAVING) == null) {
                    v.setTag(TAG_LEAVING, true)
                    v.animate().alpha(0f).translationX(dp(14f)).setDuration(180).withEndAction {
                        if (v.getTag(TAG_LEAVING) == true) { container.removeView(v); v.setTag(TAG_LEAVING, null); v.alpha = 1f; v.translationX = 0f }
                    }.start()
                } else if (!Motion.enabled) container.removeView(v)
            }
            wanted.forEach { v ->
                val wasShown = v.parent === container && v.getTag(TAG_LEAVING) == null
                if (v.getTag(TAG_LEAVING) != null) { v.animate().cancel(); v.setTag(TAG_LEAVING, null); v.alpha = 1f; v.translationX = 0f }
                add(v)                                   // re-append in rank order
                if (!wasShown) enter(v)
            }
        }

        private fun add(v: View) {
            (v.parent as? ViewGroup)?.removeView(v)
            if (container is GridLayout) container.addView(v, GridLayout.LayoutParams().apply {
                width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dpi(5f), dpi(5f), dpi(5f), dpi(5f))
            }) else container.addView(v)
        }

        private fun enter(v: View) {
            if (!Motion.enabled) return
            v.alpha = 0f; v.translationY = dp(8f); v.animate().alpha(1f).translationY(0f).setDuration(520).start()
        }

        private fun firstVisible(): View? = if (container.childCount > 0) container.getChildAt(0) else null

        private fun showEmpty(q: String) {
            empty.removeAllViews()
            val s = host.search.suggest(collection, q)
            if (s != null) {
                empty.addView(label(c, "No $plural found for “${q.trim()}”.", 13.5f, Palette.MUTED))
                empty.addView(LinearLayout(c).apply {
                    addView(label(c, "Did you mean ", 13.5f, Palette.MUTED))
                    addView(TextView(c).apply {
                        text = s.text; setTextColor(Palette.HALO); paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f); minHeight = dpi(48f); gravity = Gravity.CENTER_VERTICAL
                        isClickable = true; isFocusable = true; accessibilityDelegate = ButtonRole
                        setOnClickListener { field.edit.setText(s.text); field.edit.setSelection(s.text.length) }
                    })
                    addView(label(c, "?", 13.5f, Palette.MUTED))
                })
            } else {
                empty.addView(label(c, "No matching $plural", 14f, Palette.TEXT, Fonts.sans(c, 600)))
                empty.addView(label(c, "Try a different name.", 13.5f, Palette.MUTED))
            }
            empty.visibility = View.VISIBLE
        }
    }

    private fun lp(top: Float = 0f, bottom: Float = 0f) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(top); bottomMargin = dpi(bottom) }

    // ---------- small building blocks ----------
    class Selectable(val id: String, private val view: View, private val onChange: (Boolean) -> Unit = {}) {
        var selected = false
            set(v) { field = v; view.isSelected = v; onChange(v); view.invalidate(); view.stateText(if (v) "Selected" else "Not selected") }
    }

    private fun choiceRow(c: Context, id: String, name: String, kind: String, selected: Boolean, onSelect: () -> Unit, onRemove: (() -> Unit)?): View {
        val radio = EmberRadio(c)
        val row = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dpi(54f); setPadding(dpi(16f), 0, dpi(if (onRemove != null) 4f else 16f), 0)
            isClickable = true; isFocusable = true
            addView(radio, LinearLayout.LayoutParams(dpi(20f), dpi(20f)))
            addView(label(c, name, 15f, Palette.TEXT, Fonts.sans(c, 500)).also { ellipsize(it) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(14f) })
            addView(label(c, kind, 10.5f, Palette.FAINT, Fonts.mono(c)).apply { letterSpacing = .06f })
            if (onRemove != null) addView(removeButton(c, name) { slideOutThen(this, onRemove) }, LinearLayout.LayoutParams(dpi(48f), dpi(48f)))
            contentDescription = name
            accessibilityDelegate = RadioRole
        }
        val sel = Selectable(id, row) { radio.setChecked(it) }
        row.setTag(TAG_SELECTABLE, sel)
        row.setOnClickListener { onSelect() }
        sel.selected = selected
        return row
    }

    private fun sceneTile(c: Context, id: String, name: String, bmp: android.graphics.Bitmap?, selected: Boolean, onSelect: () -> Unit, onRemove: (() -> Unit)?): View {
        val tile = object : FrameLayout(c) {
            override fun onMeasure(w: Int, h: Int) { val width = MeasureSpec.getSize(w); super.onMeasure(w, MeasureSpec.makeMeasureSpec((width * 1.25f).toInt(), MeasureSpec.EXACTLY)) }
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (isSelected) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = Palette.ROSE
                    canvas.drawRoundRect(dp(1f), dp(1f), width - dp(1f), height - dp(1f), dp(14f), dp(14f), p)
                    p.style = Paint.Style.FILL
                    val cx = width - dp(14f); val cy = dp(14f)
                    p.shader = RadialGradient(cx, cy, dp(6f), intArrayOf(Color.WHITE, 0xFFFFD2B0.toInt(), Palette.COPPER), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, dp(6f), p)
                }
            }
        }.apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(14f)) }
            }
            setBackgroundColor(Palette.OBSIDIAN)
            isClickable = true; isFocusable = true; contentDescription = name; accessibilityDelegate = RadioRole
            if (bmp != null) addView(ImageView(c).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP }, LayoutParams(-1, -1))
            else addView(label(c, "⤮", 30f, Palette.HALO, Fonts.sans(c), Gravity.CENTER), LayoutParams(-1, -1))
            addView(label(c, name, 11.5f, Color.WHITE, Fonts.sans(c, 600)).apply {
                ellipsize(this); setPadding(dpi(8f), dpi(20f), dpi(8f), dpi(7f))
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0xCC000000.toInt()))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, -2, Gravity.BOTTOM))
            if (onRemove != null) addView(removeButton(c, name) { slideOutThen(this, onRemove) }.apply { background = GlassDrawable(c, 999f, prism = false) },
                LayoutParams(dpi(40f), dpi(40f), Gravity.TOP or Gravity.START))
            setOnClickListener { onSelect() }
        }
        val sel = Selectable(id, tile)
        tile.setTag(TAG_SELECTABLE, sel)
        sel.selected = selected
        return tile
    }

    private fun contactRow(c: Context, ct: TrustedContact, onRemove: () -> Unit): View = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dpi(52f); isFocusable = true
        contentDescription = "${ct.name}, ${ct.number}"
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(label(c, ct.name, 15f, Palette.TEXT, Fonts.sans(c, 600)).also { ellipsize(it) })
            addView(label(c, ct.number, 12.5f, Palette.MUTED, Fonts.mono(c)))     // the person's own formatting
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(removeButton(c, ct.name) { slideOutThen(this, onRemove) }, LinearLayout.LayoutParams(dpi(48f), dpi(48f)))
    }

    /** Removed items slide out (immediate with reduced motion), then the change is applied. */
    private fun slideOutThen(v: View, action: () -> Unit) {
        if (Motion.enabled) v.animate().alpha(0f).translationX(dp(18f)).setDuration(300).withEndAction(action).start() else action()
    }

    private fun removeButton(c: Context, name: String, onRemove: () -> Unit) = TextView(c).apply {
        text = "×"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextColor(Palette.MUTED); gravity = Gravity.CENTER
        contentDescription = "Remove $name"; isClickable = true; isFocusable = true; accessibilityDelegate = ButtonRole
        setOnClickListener { onRemove() }
    }

    companion object {
        private val TAG_SELECTABLE = "selectable".hashCode()
        private val TAG_LEAVING = "leaving".hashCode()
    }
}

/** Search box styled for the theme; small and never visually dominant. */
class SearchField(c: Context, hint: String, a11y: String) : FrameLayout(c) {
    val edit = EditText(c).apply {
        this.hint = hint; setHintTextColor(Palette.FAINT); setTextColor(Palette.TEXT); typeface = Fonts.sans(c)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f); contentDescription = a11y
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH; isSingleLine = true
        background = GlassDrawable(c, 12f, prism = false); setPadding(dpi(14f), 0, dpi(12f), 0); minHeight = dpi(48f)
    }
    init { visibility = GONE; addView(edit, LayoutParams(-1, dpi(48f))) }
}

/** Radio indicator: the ember dot glides in when selected. Immediate with reduced motion. */
class EmberRadio(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var k = 0f
    private var checked = false
    private var anim: ValueAnimator? = null
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setChecked(on: Boolean) {
        if (on == checked && anim == null) { k = if (on) 1f else 0f; invalidate(); return }
        checked = on
        anim?.cancel(); anim = null
        val target = if (on) 1f else 0f
        if (!Motion.enabled || !isAttachedToWindow) { k = target; invalidate(); return }
        anim = ValueAnimator.ofFloat(k, target).apply {
            duration = 450
            addUpdateListener { k = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.shader = null
        p.color = if (k > .5f) Palette.ROSE else Palette.FAINT
        canvas.drawCircle(cx, cy, dp(9f), p)
        if (k > 0f) {
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(cx, cy, dp(4.5f) * k + .01f, intArrayOf(Color.WHITE, 0xFFFFD2B0.toInt(), Palette.COPPER), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, dp(4.5f) * k, p)
        }
    }
}

object RadioRole : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = "android.widget.RadioButton"; info.isCheckable = true; info.isChecked = host.isSelected
    }
}
object ButtonRole : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.Button"
    }
}

private fun stateLabel(c: Context) = label(c, "OFF", 12f, Palette.FAINT, Fonts.mono(c)).apply { letterSpacing = .1f }
private fun note(c: Context, text: String) = label(c, text, 13f, Palette.MUTED)
private fun emptyState(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(c.dpi(16f), c.dpi(14f), c.dpi(16f), c.dpi(14f)); visibility = View.GONE }
private fun input(c: Context, hint: String, a11y: String, type: Int) = EditText(c).apply {
    this.hint = hint; setHintTextColor(Palette.FAINT); setTextColor(Palette.TEXT); inputType = type; isSingleLine = true
    contentDescription = a11y; background = GlassDrawable(c, 12f, prism = false); setPadding(c.dpi(12f), 0, c.dpi(12f), 0); minHeight = c.dpi(48f)
    typeface = if (type == InputType.TYPE_CLASS_PHONE) Fonts.mono(c) else Fonts.sans(c)
}
private fun addButton(c: Context, text: String, onClick: () -> Unit) = TextView(c).apply {
    this.text = text; setTextColor(Palette.TEXT); typeface = Fonts.sans(c, 500); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    minHeight = c.dpi(48f); gravity = Gravity.CENTER_VERTICAL; setPadding(c.dpi(18f), 0, c.dpi(18f), 0)
    background = GlassDrawable(c, 999f, prism = false); isClickable = true; isFocusable = true; accessibilityDelegate = ButtonRole
    setOnClickListener { onClick() }
}.let { v -> FrameLayout(c).apply { addView(v, FrameLayout.LayoutParams(-2, -2)) } }

private fun toggleCard(c: Context, title: String, state: TextView, text: String, sw: EmberSwitch) = LinearLayout(c).apply {
    background = GlassDrawable(c); setPadding(c.dpi(16f), c.dpi(16f), c.dpi(12f), c.dpi(16f))
    addView(LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(c).apply {
            addView(label(c, "$title — ", 15f, Palette.TEXT, Fonts.sans(c, 600)))
            addView(state)
        })
        addView(label(c, text, 13.5f, Palette.MUTED), LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dpi(6f) })
    }, LinearLayout.LayoutParams(0, -2, 1f))
    addView(sw, LinearLayout.LayoutParams(-2, -2).apply { marginStart = c.dpi(12f) })
}

/** Back chevron drawn in code. */
class BackDrawable(private val c: Context) : android.graphics.drawable.Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = c.dp(1.6f); color = Palette.MUTED; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun getIntrinsicWidth() = c.dpi(20f)
    override fun getIntrinsicHeight() = c.dpi(20f)
    override fun draw(canvas: Canvas) {
        val x = bounds.exactCenterX(); val y = bounds.exactCenterY(); val s = c.dp(6f)
        canvas.drawLine(x + s * .5f, y - s, x - s * .5f, y, p); canvas.drawLine(x - s * .5f, y, x + s * .5f, y + s, p)
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
