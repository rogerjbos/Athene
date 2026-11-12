package io.github.athene.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.athene.R
import io.github.athene.ui.theme.LocalOwlColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLesson: (String) -> Unit,
    onReview: () -> Unit,
    onSettings: () -> Unit,
    onWords: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var skipTo by remember { mutableStateOf<String?>(null) }
    skipTo?.let { lessonId ->
        AlertDialog(
            onDismissRequest = { skipTo = null },
            title = { Text(stringResource(R.string.home_skip_title)) },
            text = { Text(stringResource(R.string.home_skip_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.skipTo(lessonId)
                    skipTo = null
                }) { Text(stringResource(R.string.home_skip_confirm)) }
            },
            dismissButton = { TextButton(onClick = { skipTo = null }) { Text(stringResource(R.string.home_skip_cancel)) } },
        )
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ui.language?.let { Text("${it.flag}  ${it.name}") }
                },
                actions = {
                    Stat(Icons.Filled.LocalFireDepartment, ui.streak.toString(), LocalOwlColors.current.streak,
                        pluralStringResource(R.plurals.home_streak, ui.streak, ui.streak))
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onWords) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.words_title))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        // A fresh list position for each language, so a switch starts at the top of the new course.
        val listState = rememberSaveable(ui.language?.code, saver = LazyListState.Saver) { LazyListState() }
        // Open the path at the lesson to do next, once.
        var scrolled by rememberSaveable(ui.language?.code) { mutableStateOf(false) }
        LaunchedEffect(ui.sections, scrolled) {
            if (!scrolled && ui.sections.isNotEmpty()) {
                var index = 0
                search@ for (section in ui.sections) {
                    index++ // unit header
                    for (row in section.lessons) {
                        if (row.status == LessonStatus.CURRENT) break@search
                        index++
                    }
                }
                val done = ui.sections.any { s -> s.lessons.any { it.status == LessonStatus.DONE } }
                if (done) listState.scrollToItem((index - 1).coerceAtLeast(0))
                scrolled = true
            }
        }
        // The Today card stays put above the path so that Review is always within reach.
        Column(Modifier.fillMaxSize().padding(padding)) {
        Box(Modifier.padding(horizontal = 16.dp)) { TodayCard(ui, onReview) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            for (section in ui.sections) {
                item(key = section.unit.id) {
                    Column(Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)) {
                        Text(section.unit.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            section.unit.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                for (row in section.lessons) {
                    item(key = row.lesson.id) {
                        LessonCard(row, onClick = {
                            if (row.status == LessonStatus.LOCKED) skipTo = row.lesson.id else onLesson(row.lesson.id)
                        })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, tint: Color, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TodayCard(ui: HomeUi, onReview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (ui.xpToday >= ui.dailyGoal) stringResource(R.string.home_goal_reached)
                    else stringResource(R.string.home_xp_progress, ui.xpToday, ui.dailyGoal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (ui.dailyGoal == 0) 0f else (ui.xpToday.toFloat() / ui.dailyGoal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                trackColor = colors.surfaceVariant,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (ui.dueCount > 0) pluralStringResource(R.plurals.home_due, ui.dueCount, ui.dueCount)
                        else stringResource(R.string.home_nothing_due),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        pluralStringResource(R.plurals.home_learned, ui.learnedCount, ui.learnedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = onReview, enabled = ui.dueCount > 0) { Text(stringResource(R.string.home_review)) }
            }
        }
    }
}

@Composable
private fun LessonCard(row: LessonRow, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val owl = LocalOwlColors.current
    val current = row.status == LessonStatus.CURRENT
    val locked = row.status == LessonStatus.LOCKED
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (current) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (current) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .alpha(if (locked) 0.55f else 1f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, background, tint) = when (row.status) {
                LessonStatus.DONE -> Triple(Icons.Filled.Check, owl.goodContainer, owl.onGoodContainer)
                LessonStatus.CURRENT -> Triple(Icons.Filled.PlayArrow, colors.primary, colors.onPrimary)
                LessonStatus.LOCKED -> Triple(Icons.Filled.Lock, colors.surfaceVariant, colors.onSurfaceVariant)
            }
            Box(Modifier.size(44.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(if (locked) 20.dp else 24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.home_lesson, row.number), style = MaterialTheme.typography.titleMedium)
                Text(
                    row.lesson.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onClick) { Text(stringResource(R.string.action_start)) }
            }
        }
    }
}
