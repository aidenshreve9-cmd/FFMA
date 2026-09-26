package com.focusfriend.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusfriend.app.model.Catalog
import com.focusfriend.app.model.MediaKind
import com.focusfriend.app.model.UserMedia
import com.focusfriend.app.search.SearchRecord
import com.focusfriend.app.session.Collections
import com.focusfriend.app.session.FocusViewModel
import com.focusfriend.app.session.SceneChoice
import com.focusfriend.app.ui.background.SceneView
import com.focusfriend.app.ui.background.Scenes
import com.focusfriend.app.ui.background.drawCover
import com.focusfriend.app.ui.background.rememberImage
import com.focusfriend.app.ui.components.AddButton
import com.focusfriend.app.ui.components.EmberSwitch
import com.focusfriend.app.ui.components.FieldInput
import com.focusfriend.app.ui.components.IconCircle
import com.focusfriend.app.ui.components.OnOffTag
import com.focusfriend.app.ui.components.glass
import com.focusfriend.app.ui.theme.Fonts
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.ui.theme.Type

/** Browsing is easier for short lists: sounds and atmospheres get a search box only past 12 options. */
private const val SEARCH_THRESHOLD = 13

@Composable
fun SettingsScreen(vm: FocusViewModel, time: State<Float>) {
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.addMedia(uri, MediaKind.SOUND)
    }
    val picturePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.addMedia(uri, MediaKind.PICTURE)
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let(vm::addPickedContact)
    }

    Box(Modifier.fillMaxSize()) {
        // The background is the chosen atmosphere, crossfading as you choose.
        val atmosphere = remember(vm.settings.scene, vm.media) {
            vm.sceneChoiceFor(vm.settings.scene) ?: SceneChoice.Painted(Catalog.DEFAULT_SCENE, Catalog.SCENES[0].name)
        }
        Crossfade(atmosphere, animationSpec = tween(700), label = "atmosphere") { choice ->
            SceneView(choice, time, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x9E03000C), .4f to Color(0x8003000C), 1f to Color(0xB303000C))))

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconCircle("Back to Focus", vm::closeSettings) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
                }
                Text("Settings", style = TextStyle(fontFamily = Fonts.Serif, fontSize = 26.sp, color = Palette.Text), modifier = Modifier.semantics { heading() })
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SoundSection(vm) { soundPicker.launch("audio/*") }
                AtmosphereSection(vm) { picturePicker.launch("image/*") }
                TrustedSection(vm) {
                    try {
                        contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    } catch (e: ActivityNotFoundException) {
                        vm.showToast("The contact picker isn't available here. Type the number instead.")
                    }
                }
                AlarmSection(vm)
                PermissionSection(vm)
                Text(
                    "Buy me a coffee",
                    style = TextStyle(fontFamily = Fonts.Sans, fontSize = 12.5.sp, color = Palette.Faint, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable(role = Role.Button, onClick = vm::showDonate).padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = Type.SectionTitle, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp).semantics { heading() })
}

@Composable
private fun Note(text: String, warn: Boolean = false) {
    Text(text, style = Type.Note, color = if (warn) Palette.Warn else Palette.Muted, modifier = Modifier.padding(top = 10.dp))
}

/* ---------------- Search ---------------- */

private class Filtered<T>(val items: List<T>, val suggestion: SearchRecord?, val active: Boolean)

/** Ranks and filters a list in place as the person types; nothing typed means everything, in order. */
private fun <T> filter(vm: FocusViewModel, collection: String, items: List<T>, id: (T) -> String, query: String, enabled: Boolean): Filtered<T> {
    if (!enabled || query.isBlank()) return Filtered(items, null, false)
    val rank = vm.search.search(collection, query, 200).mapIndexed { i, r -> r.record.id to i }.toMap()
    val shown = items.filter { id(it) in rank }.sortedBy { rank[id(it)] }
    return Filtered(shown, if (shown.isEmpty()) vm.search.suggest(collection, query) else null, true)
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit, placeholder: String) {
    FieldInput(
        value = query,
        onValueChange = onChange,
        placeholder = placeholder,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Faint, modifier = Modifier.padding(end = 8.dp).size(16.dp)) },
    )
}

@Composable
private fun NoMatches(plural: String, query: String, suggestion: SearchRecord?, onSuggestion: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (suggestion != null) {
            Text("No $plural found for “${query.trim()}”.", style = Type.Note.copy(fontSize = 13.5.sp))
            Row {
                Text("Did you mean ", style = Type.Note.copy(fontSize = 13.5.sp))
                Text(
                    suggestion.title,
                    style = Type.Note.copy(fontSize = 13.5.sp, color = Palette.Halo, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    modifier = Modifier.clickable(role = Role.Button) { onSuggestion(suggestion.title) },
                )
                Text("?", style = Type.Note.copy(fontSize = 13.5.sp))
            }
        } else {
            Text("No matching $plural", style = Type.Body.copy(fontWeight = FontWeight.SemiBold))
            Text("Try a different name.", style = Type.Note.copy(fontSize = 13.5.sp))
        }
    }
}

/* ---------------- Sound ---------------- */

private class SoundRow(val id: String, val name: String, val kind: String, val media: UserMedia?)

@Composable
private fun SoundSection(vm: FocusViewModel, onAdd: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = Catalog.SOUNDS.map { SoundRow(it.id, it.name, it.kind, null) } +
        SoundRow(Catalog.RANDOM, "Random", "EACH SESSION", null) +
        vm.media.filter { it.kind == MediaKind.SOUND }.map { SoundRow(it.id, it.name, "YOURS", it) }
    val searchable = rows.size >= SEARCH_THRESHOLD
    val f = filter(vm, Collections.SOUNDS, rows, { it.id }, query, searchable)

    Column {
        SectionTitle("Sound")
        if (searchable) SearchBox(query, { query = it }, "Search sounds…")
        Column(Modifier.fillMaxWidth().glass()) {
            f.items.forEachIndexed { i, row ->
                if (i > 0) Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChoiceRow(row.name, row.kind, selected = vm.settings.audio == row.id, modifier = Modifier.weight(1f)) {
                        vm.tapSound(row.id, fromSearch = f.active)
                    }
                    if (row.media != null) RemoveButton("Remove ${row.name}") { vm.removeMedia(row.media) }
                }
            }
            if (f.active && f.items.isEmpty()) NoMatches("sounds", query, f.suggestion) { query = it }
        }
        AddButton("Add your own sound", onAdd)
        Note("Use your phone's volume buttons to change how loud it is.")
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp).heightIn(min = 1.dp, max = 1.dp).background(Palette.Divider))
}

@Composable
private fun ChoiceRow(name: String, kind: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dot by animateFloatAsState(if (selected) 1f else 0f, tween(450), label = "radio")
    Row(
        modifier
            .heightIn(min = 54.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(20.dp)) {
            val r = size.minDimension / 2
            drawCircle(if (selected) Palette.Rose else Palette.Faint, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            if (selected) drawCircle(Palette.Rose.copy(alpha = 0.35f), r + 3.dp.toPx(), style = Stroke(3.dp.toPx()))
            if (dot > 0f) {
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFD2B0), Palette.Copper), center, 4.5.dp.toPx() * dot), 4.5.dp.toPx() * dot)
            }
        }
        Text(name, style = Type.Body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(kind, style = TextStyle(fontFamily = Fonts.Mono, fontSize = 10.5.sp, letterSpacing = 0.06.em, color = Palette.Faint), maxLines = 1)
    }
}

@Composable
private fun RemoveButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("×", style = TextStyle(fontSize = 20.sp, color = Palette.Muted))
    }
}

/* ---------------- Atmosphere ---------------- */

private class SceneTile(val id: String, val name: String, val media: UserMedia?)

@Composable
private fun AtmosphereSection(vm: FocusViewModel, onAdd: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val tiles = Catalog.SCENES.map { SceneTile(it.id, it.name, null) } +
        SceneTile(Catalog.RANDOM, "Random", null) +
        vm.media.filter { it.kind == MediaKind.PICTURE }.map { SceneTile(it.id, it.name, it) }
    val searchable = tiles.size >= SEARCH_THRESHOLD
    val f = filter(vm, Collections.SCENES, tiles, { it.id }, query, searchable)

    Column {
        SectionTitle("Atmosphere")
        if (searchable) SearchBox(query, { query = it }, "Search atmospheres…")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            f.items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile ->
                        SceneTileView(tile, selected = vm.settings.scene == tile.id, modifier = Modifier.weight(1f), onRemove = tile.media?.let { m -> { vm.removeMedia(m) } }) {
                            vm.selectScene(tile.id)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (f.active && f.items.isEmpty()) NoMatches("atmospheres", query, f.suggestion) { query = it }
        AddButton("Add your own picture", onAdd)
    }
}

@Composable
private fun SceneTileView(tile: SceneTile, selected: Boolean, modifier: Modifier, onRemove: (() -> Unit)?, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val ember by animateFloatAsState(if (selected) 1f else 0f, tween(450), label = "ember")
    Box(modifier.aspectRatio(4f / 5f)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Palette.Obsidian)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.Rose else Palette.GlassLine, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .semantics { contentDescription = tile.name },
        ) {
            when {
                tile.media != null -> {
                    val image by rememberImage(tile.media.file, maxSide = 400)
                    Canvas(Modifier.fillMaxSize()) { image?.let { drawCover(it) } }
                }
                tile.id == Catalog.RANDOM -> Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.radialGradient(0f to Color(0x738A2BE2), .45f to Color(0x591E005B), .8f to Palette.Obsidian, center = center.copy(y = size.height * .42f), radius = size.maxDimension * .6f))
                    val c = center.copy(y = size.height * .42f)
                    val s = 11.dp.toPx()
                    val stroke = 1.5.dp.toPx()
                    drawLine(Palette.Halo, c + androidx.compose.ui.geometry.Offset(-s, s), c + androidx.compose.ui.geometry.Offset(s, -s), stroke)
                    drawLine(Palette.Halo, c + androidx.compose.ui.geometry.Offset(-s, -s), c + androidx.compose.ui.geometry.Offset(-s * .2f, -s * .2f), stroke)
                    drawLine(Palette.Halo, c + androidx.compose.ui.geometry.Offset(s * .2f, s * .2f), c + androidx.compose.ui.geometry.Offset(s, s), stroke)
                }
                else -> Canvas(Modifier.fillMaxSize()) { Scenes.paint(this, tile.id, 6f) }
            }
            Text(
                tile.name,
                style = TextStyle(fontFamily = Fonts.Sans, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 7.dp),
            )
            if (ember > 0f) {
                Canvas(Modifier.align(Alignment.TopEnd).padding(8.dp).size(12.dp).alpha(ember)) {
                    drawCircle(Brush.radialGradient(listOf(Palette.Copper.copy(alpha = .5f), Color.Transparent), center, size.minDimension * 1.6f), size.minDimension * 1.6f)
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFD2B0), Palette.Copper), center, size.minDimension / 2), size.minDimension / 2)
                }
            }
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .padding(4.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .semantics { contentDescription = "Remove ${tile.name}" }
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Text("×", style = TextStyle(fontSize = 16.sp, color = Color.White)) }
        }
    }
}

/* ---------------- Trusted Contacts ---------------- */

@Composable
private fun TrustedSection(vm: FocusViewModel, onPick: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    val contacts = vm.settings.contacts
    val searchable = contacts.isNotEmpty()
    val f = filter(vm, Collections.CONTACTS, contacts, { it.id }, query, searchable)
    val on = vm.settings.trustedOn
    val add = {
        if (name.isBlank()) vm.showToast("Add a name and a phone number.")
        else {
            val err = vm.addContact(name, number)
            if (err != null) vm.showToast(err) else { name = ""; number = "" }
        }
    }

    Column {
        SectionTitle("Trusted Contacts")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard(
                title = "Trusted Contacts",
                on = on,
                text = "Android's Do Not Disturb can't let through only the people on this list yet, so this version of the app can't let their calls or messages through. Your list is saved for when it can.",
                onToggle = vm::toggleTrusted,
            )
            Column(Modifier.fillMaxWidth().glass().alpha(if (on) 1f else .5f).padding(16.dp)) {
                if (searchable) SearchBox(query, { query = it }, "Search contacts…")
                if (contacts.isEmpty()) {
                    Text("No trusted contacts yet", style = Type.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text("Add someone you want to be able to reach you during Focus.", style = Type.Note.copy(fontSize = 13.5.sp))
                }
                f.items.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = "${c.name}, ${c.number}" }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = Type.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.number, style = TextStyle(fontFamily = Fonts.Mono, fontSize = 12.5.sp, letterSpacing = 0.03.em, color = Palette.Muted))
                        }
                        RemoveButton("Remove ${c.name}") { vm.removeContact(c.id) }
                    }
                }
                if (f.active && f.items.isEmpty()) NoMatches("contacts", query, f.suggestion) { query = it }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FieldInput(name, { name = it.take(40) }, "Name", Modifier.weight(1f))
                    FieldInput(number, { number = it.take(24) }, "Phone number", Modifier.weight(1f), keyboardType = KeyboardType.Phone, mono = true)
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(Palette.Rose, Palette.Magenta)))
                            .clickable(role = Role.Button, onClick = add)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Add", style = Type.Button.copy(fontSize = 15.sp), color = Color.White) }
                }
            }
        }
        AddButton("Choose from contacts", onPick)
        Note("Stored only on this device.")
    }
}

/* ---------------- Alarm Safety & permissions ---------------- */

@Composable
private fun ToggleCard(title: String, on: Boolean, text: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$title — ", style = Type.Body.copy(fontWeight = FontWeight.SemiBold))
                OnOffTag(on)
            }
            Text(text, style = Type.Note.copy(fontSize = 13.5.sp), modifier = Modifier.padding(top = 6.dp))
        }
        EmberSwitch(on, onToggle, title, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun AlarmSection(vm: FocusViewModel) {
    Column {
        SectionTitle("Alarm Safety")
        ToggleCard(
            title = "Alarm Safety",
            on = vm.settings.alarmSafety,
            text = "Allow alarms to interrupt Focus. Alarms may be used for medication, emergencies, or other safety or health-related purposes.",
            onToggle = vm::toggleAlarmSafety,
        )
        Note("When on, alarms ring during Focus. When off, Do Not Disturb silences them too.")
    }
}

@Composable
private fun PermissionSection(vm: FocusViewModel) {
    val ok = vm.dndGranted
    Column {
        SectionTitle("Permissions")
        Row(Modifier.fillMaxWidth().glass().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (ok) "ALLOWED" else "NOT SET UP",
                style = TextStyle(fontFamily = Fonts.Mono, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em, color = if (ok) Palette.Halo else Palette.Warn),
                modifier = Modifier
                    .background(if (ok) Palette.Halo.copy(alpha = .1f) else Palette.Copper.copy(alpha = .12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text("Do Not Disturb access", style = Type.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Text(
                "Review",
                style = Type.Body.copy(fontWeight = FontWeight.SemiBold, color = Palette.Rose),
                modifier = Modifier.clickable(role = Role.Button, onClick = vm::reviewPermission).padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
        Note("Focus Friend turns on Do Not Disturb for each session and puts your phone back the way it was when the session ends.")
    }
}
