package io.github.athene.ui.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.athene.R
import io.github.athene.lesson.Build
import io.github.athene.lesson.Choice
import io.github.athene.lesson.Exercise
import io.github.athene.lesson.Intro
import io.github.athene.lesson.Match
import io.github.athene.lesson.Speak
import io.github.athene.lesson.Type
import io.github.athene.lesson.Verdict
import io.github.athene.ui.theme.LocalOwlColors

@Composable
fun LessonScreen(onClose: () -> Unit, viewModel: LessonViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmQuit by remember { mutableStateOf(false) }
    val inProgress = ui.summary == null && ui.exercise != null
    BackHandler(enabled = inProgress) { confirmQuit = true }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text(stringResource(R.string.lesson_quit_title)) },
            text = { Text(stringResource(R.string.lesson_quit_text)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.lesson_quit_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text(stringResource(R.string.lesson_quit_cancel)) } },
        )
    }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val summary = ui.summary
            val exercise = ui.exercise
            if (summary == null) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (inProgress) confirmQuit = true else onClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                    val progress by animateFloatAsState(if (ui.total == 0) 0f else ui.done.toFloat() / ui.total, label = "progress")
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                }
            }
            when {
                summary != null -> SummaryContent(summary, ui.isReview, onClose)
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                exercise == null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lesson_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                else -> key(ui.sequence) {
                    ExerciseContent(
                        exercise = exercise,
                        languageName = ui.languageName,
                        languageCode = ui.languageCode,
                        locale = ui.locale,
                        hasVoice = ui.hasVoice,
                        feedback = ui.feedback,
                        onSay = viewModel::say,
                        spokenScore = viewModel::spokenScore,
                        onSkipSpeaking = viewModel::skipSpeaking,
                        onSubmit = viewModel::submit,
                        onNext = viewModel::next,
                        onMatchDone = viewModel::matchDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseContent(
    exercise: Exercise,
    languageName: String,
    languageCode: String,
    locale: String,
    hasVoice: Boolean,
    feedback: Feedback?,
    onSay: (String, Boolean) -> Unit,
    spokenScore: (List<String>) -> Int,
    onSkipSpeaking: () -> Unit,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onMatchDone: (Set<String>) -> Unit,
) {
    var answer by remember { mutableStateOf("") }
    val open = feedback == null
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            when (exercise) {
                is Intro -> IntroBody(exercise, hasVoice, onSay)
                is Choice -> ChoiceBody(exercise, languageName, answer, feedback, hasVoice, onSay, onSelect = { if (open) answer = it })
                is Match -> MatchBody(exercise, onDone = onMatchDone)
                is Build -> BuildBody(exercise, languageName, enabled = open, revealed = !open, hasVoice, onSay, onAnswer = { answer = it })
                is Speak -> SpeakBody(exercise, locale, enabled = open, hasVoice, onSay, spokenScore, onPassed = onSubmit)
                is Type -> TypeBody(
                    exercise, languageName, languageCode, answer, enabled = open,
                    onValue = { answer = it },
                    onDone = { if (answer.isNotBlank()) onSubmit(answer) },
                )
            }
        }
        when {
            feedback != null -> FeedbackPanel(feedback, exercise, onNext)
            exercise is Intro -> BottomButton(stringResource(R.string.action_continue), enabled = true, onClick = onNext)
            exercise is Match -> Unit
            exercise is Speak -> TextButton(
                onClick = onSkipSpeaking,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(52.dp),
            ) {
                Text(stringResource(R.string.speak_skip), style = MaterialTheme.typography.titleMedium)
            }
            else -> BottomButton(stringResource(R.string.action_check), enabled = answer.isNotBlank(), onClick = { onSubmit(answer) })
        }
    }
}

@Composable
private fun BottomButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(52.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FeedbackPanel(feedback: Feedback, exercise: Exercise, onNext: () -> Unit) {
    val owl = LocalOwlColors.current
    val colors = MaterialTheme.colorScheme
    val passed = feedback.verdict.passed
    val title = when (feedback.verdict) {
        Verdict.CORRECT -> R.string.feedback_correct
        Verdict.ACCENTS -> R.string.feedback_accents
        Verdict.TYPO -> R.string.feedback_typo
        Verdict.WRONG -> R.string.feedback_wrong
    }
    // After building a sentence in the new language, remind the learner what it meant.
    val meaning = (exercise as? Build)?.takeIf { passed && it.toTarget }?.item?.meaning
    Surface(
        color = if (passed) owl.goodContainer else colors.errorContainer,
        contentColor = if (passed) owl.onGoodContainer else colors.onErrorContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            if (feedback.verdict != Verdict.CORRECT) {
                Spacer(Modifier.height(4.dp))
                Text(feedback.correctAnswer, style = MaterialTheme.typography.bodyLarge)
            } else if (meaning != null) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.feedback_meaning, meaning), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (passed) owl.good else colors.error,
                    contentColor = if (passed) owl.onGood else colors.onError,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.action_continue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SummaryContent(summary: Summary, isReview: Boolean, onClose: () -> Unit) {
    val owl = LocalOwlColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).background(owl.goodContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = owl.good, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (isReview) R.string.summary_review else R.string.summary_lesson),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (summary.mistakes == 0) R.string.summary_perfect else R.string.summary_retry_note),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStat("+${summary.xp}", stringResource(R.string.summary_xp), Modifier.weight(1f))
                SummaryStat(summary.mistakes.toString(), stringResource(R.string.summary_mistakes), Modifier.weight(1f))
                SummaryStat(summary.streak.toString(), stringResource(R.string.summary_streak), Modifier.weight(1f))
            }
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp)) {
            Text(stringResource(R.string.action_continue), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
