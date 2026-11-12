package io.github.athene.ui.lesson

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.athene.lesson.Speak
import io.github.athene.speech.ListenError
import io.github.athene.speech.Listener
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.athene.R
import io.github.athene.data.Item
import io.github.athene.lesson.Build
import io.github.athene.lesson.Choice
import io.github.athene.lesson.ExerciseGenerator
import io.github.athene.lesson.Grader
import io.github.athene.lesson.Intro
import io.github.athene.lesson.Match
import io.github.athene.lesson.Type
import io.github.athene.ui.theme.LocalOwlColors
import kotlinx.coroutines.delay

@Composable
private fun Instruction(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun Prompt(text: String, reading: String? = null) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
        if (reading != null) {
            Text(reading, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A small speaker next to text that can be read aloud. */
@Composable
private fun SayButton(text: String, onSay: (String, Boolean) -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = { onSay(text, false) }, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(R.string.action_listen),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The prompt of an exercise that is heard, not read: a large speaker and a slower replay. */
@Composable
private fun AudioPrompt(text: String, onSay: (String, Boolean) -> Unit) {
    Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { onSay(text, false) }, modifier = Modifier.size(88.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.action_listen),
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        FilledTonalIconButton(onClick = { onSay(text, true) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SlowMotionVideo, contentDescription = stringResource(R.string.action_listen_slow))
        }
    }
}

/** A rounded, tappable box; the base of options, tiles and matching cells. */
@Composable
private fun Tile(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
    outline: Color = MaterialTheme.colorScheme.outlineVariant,
    outlineWidth: Int = 1,
    centered: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(outlineWidth.dp, outline),
    ) {
        Box(
            Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        }
    }
}

@Composable
fun IntroBody(exercise: Intro, hasVoice: Boolean, onSay: (String, Boolean) -> Unit) {
    val item = exercise.item
    Instruction(stringResource(if (item.kind == Item.KIND_WORD) R.string.lesson_new_word else R.string.lesson_new_phrase))
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.text, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            item.reading?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasVoice) SayButton(item.text, onSay)
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                item.meaning,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            val others = item.meanings.drop(1)
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lesson_also, others.joinToString("; ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ChoiceBody(
    exercise: Choice,
    languageName: String,
    selected: String,
    feedback: Feedback?,
    hasVoice: Boolean,
    onSay: (String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val owl = LocalOwlColors.current
    Instruction(
        when {
            exercise.audio -> stringResource(R.string.lesson_listen_meaning)
            exercise.toTarget -> stringResource(R.string.lesson_pick_target, languageName)
            else -> stringResource(R.string.lesson_pick_meaning)
        },
    )
    when {
        // What was said is shown once the answer is in.
        exercise.audio && feedback == null -> AudioPrompt(exercise.item.text, onSay)
        exercise.toTarget -> Prompt(exercise.prompt)
        else -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f, fill = false)) { Prompt(exercise.prompt, exercise.item.reading) }
            if (hasVoice) SayButton(exercise.item.text, onSay)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (option in exercise.options) {
            val isAnswer = feedback != null && option == exercise.answer
            val isMistake = feedback != null && option == selected && option != exercise.answer
            val isPicked = feedback == null && option == selected
            Tile(
                text = option,
                onClick = { onSelect(option) },
                modifier = Modifier.fillMaxWidth(),
                container = when {
                    isAnswer -> owl.goodContainer
                    isMistake -> colors.errorContainer
                    isPicked -> colors.primaryContainer
                    else -> colors.surfaceContainer
                },
                content = when {
                    isAnswer -> owl.onGoodContainer
                    isMistake -> colors.onErrorContainer
                    isPicked -> colors.onPrimaryContainer
                    else -> colors.onSurface
                },
                outline = when {
                    isAnswer -> owl.good
                    isMistake -> colors.error
                    isPicked -> colors.primary
                    else -> colors.outlineVariant
                },
                outlineWidth = if (isAnswer || isMistake || isPicked) 2 else 1,
            )
        }
    }
}

@Composable
fun MatchBody(exercise: Match, onDone: (Set<String>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val owl = LocalOwlColors.current
    val left = remember { exercise.items.shuffled() }
    val right = remember { exercise.items.shuffled() }
    val matchedLeft = remember { mutableStateListOf<String>() }
    val matchedRight = remember { mutableStateListOf<String>() }
    val missed = remember { mutableStateListOf<String>() }
    var pickedLeft by remember { mutableStateOf<String?>(null) }
    var pickedRight by remember { mutableStateOf<String?>(null) }
    var wrong by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Two picks make a pair: keep it if the meanings agree, otherwise flash both and let go.
    LaunchedEffect(pickedLeft, pickedRight) {
        val a = pickedLeft
        val b = pickedRight
        if (a == null || b == null) return@LaunchedEffect
        val leftItem = exercise.items.first { it.id == a }
        val rightItem = exercise.items.first { it.id == b }
        // Two words that share a meaning may be paired either way round.
        if (Grader.plain(leftItem.meaning) == Grader.plain(rightItem.meaning)) {
            matchedLeft += a
            matchedRight += b
            pickedLeft = null
            pickedRight = null
        } else {
            if (a !in missed) missed += a
            wrong = a to b
            delay(500)
            wrong = null
            pickedLeft = null
            pickedRight = null
        }
    }

    // Kept apart from the effect above, which restarts (and would cancel this) whenever a pick changes.
    LaunchedEffect(matchedLeft.size) {
        if (matchedLeft.size == exercise.items.size) {
            delay(350)
            onDone(missed.toSet())
        }
    }

    Instruction(stringResource(R.string.lesson_match))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((side, cells) in listOf(true to left, false to right)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (item in cells) {
                    val done = item.id in (if (side) matchedLeft else matchedRight)
                    val picked = (if (side) pickedLeft else pickedRight) == item.id
                    val flash = wrong != null && (if (side) wrong?.first else wrong?.second) == item.id
                    Tile(
                        text = if (side) item.text else item.meaning,
                        onClick = {
                            if (wrong == null) {
                                if (side) pickedLeft = item.id else pickedRight = item.id
                            }
                        },
                        enabled = !done,
                        centered = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).alpha(if (done) 0.4f else 1f),
                        container = when {
                            flash -> colors.errorContainer
                            done -> owl.goodContainer
                            picked -> colors.primaryContainer
                            else -> colors.surfaceContainer
                        },
                        content = when {
                            flash -> colors.onErrorContainer
                            done -> owl.onGoodContainer
                            picked -> colors.onPrimaryContainer
                            else -> colors.onSurface
                        },
                        outline = when {
                            flash -> colors.error
                            done -> owl.good
                            picked -> colors.primary
                            else -> colors.outlineVariant
                        },
                        outlineWidth = if (flash || picked) 2 else 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildBody(
    exercise: Build,
    languageName: String,
    enabled: Boolean,
    revealed: Boolean,
    hasVoice: Boolean,
    onSay: (String, Boolean) -> Unit,
    onAnswer: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val chosen = remember { mutableStateListOf<Int>() }
    fun report() = onAnswer(chosen.joinToString(" ") { exercise.tiles[it] })

    Instruction(
        when {
            exercise.audio -> stringResource(R.string.lesson_listen_build)
            exercise.toTarget -> stringResource(R.string.lesson_build_target, languageName)
            else -> stringResource(R.string.lesson_build_english)
        },
    )
    when {
        exercise.audio && !revealed -> AudioPrompt(exercise.item.text, onSay)
        exercise.audio -> Prompt(exercise.item.text, exercise.item.reading)
        exercise.toTarget -> Prompt(exercise.prompt)
        else -> HintedPrompt(exercise.item, hasVoice, onSay)
    }

    // The answer line.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 124.dp)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (index in chosen.toList()) {
            Tile(
                text = exercise.tiles[index],
                onClick = {
                    chosen.remove(index)
                    report()
                },
                enabled = enabled,
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
                outline = colors.secondary,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    // The tile bank. A used tile leaves a gap so that the others do not jump around.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        exercise.tiles.forEachIndexed { index, tile ->
            val used = index in chosen
            Tile(
                text = tile,
                onClick = {
                    chosen.add(index)
                    report()
                },
                enabled = enabled && !used,
                modifier = Modifier.alpha(if (used) 0.15f else 1f),
            )
        }
    }
}

/** A sentence in the language being learned; tapping a word shows its meaning underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HintedPrompt(item: Item, hasVoice: Boolean = false, onSay: (String, Boolean) -> Unit = { _, _ -> }) {
    val colors = MaterialTheme.colorScheme
    val hints = remember(item) { item.hints }
    var shown by remember { mutableStateOf<String?>(null) }
    val words = remember(item) {
        if (item.unspaced) item.surfaces.ifEmpty { listOf(item.text) }
        else item.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (item.unspaced) 3.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (word in words) {
            val key = if (item.unspaced) word else ExerciseGenerator.wordsOf(word).firstOrNull()?.lowercase()
            val hint = hints[key]
            val dotted = colors.outline
            Text(
                word,
                style = MaterialTheme.typography.headlineMedium,
                color = if (hint != null && shown == key) colors.primary else colors.onSurface,
                modifier = if (hint == null) Modifier else Modifier
                    .clickable { shown = if (shown == key) null else key }
                    .drawBehind {
                        drawLine(
                            dotted,
                            Offset(0f, size.height - 2.dp.toPx()),
                            Offset(size.width, size.height - 2.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                        )
                    },
            )
        }
        if (hasVoice) SayButton(item.text, onSay)
    }
    item.reading?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
    Text(
        shown?.let { hints[it] } ?: stringResource(R.string.lesson_tap_words),
        style = MaterialTheme.typography.bodyMedium,
        color = if (shown != null) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
    )
}

/** Letters a standard keyboard makes awkward, offered as buttons under the text field. */
private val specialLetters = mapOf(
    "es" to "áéíóúñü¿¡",
    "fr" to "éèêàçùâîôûëïœ",
    "de" to "äöüß",
    "nl" to "éëïó",
    "it" to "àèéìòù",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeBody(
    exercise: Type,
    languageName: String,
    languageCode: String,
    value: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    // Keeps the cursor, which a plain String cannot.
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Instruction(stringResource(R.string.lesson_type_target, languageName))
    Prompt(exercise.prompt)
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onValue(it.text)
        },
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.lesson_type_hint)) },
        textStyle = MaterialTheme.typography.titleLarge,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    val letters = specialLetters[languageCode].orEmpty()
    if (letters.isNotEmpty() && enabled) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (letter in letters) {
                Tile(
                    text = letter.toString(),
                    centered = true,
                    onClick = {
                        val start = field.selection.min
                        val text = field.text.replaceRange(start, field.selection.max, letter.toString())
                        field = TextFieldValue(text, TextRange(start + 1))
                        onValue(text)
                    },
                )
            }
        }
    }
}

/** Say a phrase into the microphone. Only speech close enough to the phrase is reported, through [onPassed]. */
@Composable
fun SpeakBody(
    exercise: Speak,
    locale: String,
    enabled: Boolean,
    hasVoice: Boolean,
    onSay: (String, Boolean) -> Unit,
    spokenScore: (List<String>) -> Int,
    onPassed: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val listener = remember { Listener(context) }
    DisposableEffect(Unit) { onDispose { listener.cancel() } }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun listen() {
        listening = true
        message = null
        listener.listen(
            locale,
            onResult = { heard ->
                listening = false
                if (spokenScore(heard) >= LessonViewModel.SPOKEN_PASS) onPassed(heard.first())
                else message = context.getString(R.string.speak_heard, heard.first())
            },
            onError = { error ->
                listening = false
                message = context.getString(
                    when (error) {
                        ListenError.NOTHING_HEARD -> R.string.speak_nothing
                        ListenError.NO_PERMISSION -> R.string.speak_no_permission
                        ListenError.UNAVAILABLE -> R.string.speak_unavailable
                    },
                )
            },
        )
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else message = context.getString(R.string.speak_no_permission)
    }

    Instruction(stringResource(R.string.lesson_speak))
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f, fill = false)) { Prompt(exercise.item.text, exercise.item.reading) }
        if (hasVoice) SayButton(exercise.item.text, onSay)
    }
    Text(
        exercise.item.meaning,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 32.dp),
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) listen() else askPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = enabled && !listening,
            modifier = Modifier.size(96.dp),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.speak_start), modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                listening -> stringResource(R.string.speak_listening)
                else -> message ?: stringResource(R.string.speak_tap)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (message != null && !listening) colors.error else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
