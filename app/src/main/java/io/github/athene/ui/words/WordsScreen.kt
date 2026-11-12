package io.github.athene.ui.words

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.athene.R
import io.github.athene.data.Item
import io.github.athene.lesson.Grader
import io.github.athene.athene
import java.time.LocalDate

/** One learned item and when it is next due, in days from today (0 or less: due now). */
private data class Learned(val item: Item, val dueIn: Long)

private data class Loaded(val locale: String, val hasVoice: Boolean, val entries: List<Learned>)

/** Everything the learner has met so far, to browse, search and listen to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.athene
    val loaded by produceState<Loaded?>(null) {
        val language = app.settings.language?.let { app.content.language(it) }
        value = if (language == null) {
            Loaded("", false, emptyList())
        } else {
            val today = LocalDate.now().toEpochDay()
            val states = app.user.allStates(language.code)
            // SQLite limits how many values one query may bind, so look the items up in batches.
            val items = states.map { it.itemId }.chunked(500).flatMap { app.content.items(it) }.associateBy { it.id }
            val entries = states.mapNotNull { state -> items[state.itemId]?.let { Learned(it, state.dueDay - today) } }
            Loaded(language.locale, app.speaker.supports(language.locale), entries.sortedBy { Grader.fold(it.item.text) })
        }
    }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.words_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val data = loaded
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (data.entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.words_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val shown = remember(data, query) {
            val wanted = Grader.fold(query)
            if (wanted.isEmpty()) data.entries
            else data.entries.filter { wanted in Grader.fold(it.item.text) || wanted in Grader.fold(it.item.meaning) }
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.words_search)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
                items(shown, key = { it.item.id }) { entry ->
                    Entry(entry, data.hasVoice, onSay = { slow -> app.speaker.speak(entry.item.text, data.locale, slow) })
                }
            }
        }
    }
}

@Composable
private fun Entry(entry: Learned, hasVoice: Boolean, onSay: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.item.text, style = MaterialTheme.typography.titleMedium)
                Text(entry.item.meaning, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(
                    if (entry.dueIn <= 0) stringResource(R.string.words_due_now)
                    else pluralStringResource(R.plurals.words_due_in, entry.dueIn.toInt(), entry.dueIn.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.dueIn <= 0) colors.primary else colors.onSurfaceVariant,
                )
            }
            if (hasVoice) {
                IconButton(onClick = { onSay(true) }) {
                    Icon(Icons.Filled.SlowMotionVideo, contentDescription = stringResource(R.string.action_listen_slow), tint = colors.onSurfaceVariant)
                }
                IconButton(onClick = { onSay(false) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.action_listen), tint = colors.primary)
                }
            }
        }
    }
}
