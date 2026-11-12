package io.github.athene.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.athene.R
import io.github.athene.data.Language
import io.github.athene.athene
import io.github.athene.ui.common.GoalPicker
import io.github.athene.ui.common.LanguagePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.athene
    val languages by produceState(emptyList<Language>()) { value = app.content.languages() }
    var language by remember { mutableStateOf(app.settings.language) }
    var goal by remember { mutableIntStateOf(app.settings.dailyGoal) }
    var listening by remember { mutableStateOf(app.settings.listening) }
    var speaking by remember { mutableStateOf(app.settings.speaking) }
    val current = languages.firstOrNull { it.code == language }
    // null while the speech engine is still starting.
    val hasVoice by produceState<Boolean?>(null, current) {
        value = current?.let { app.speaker.supports(it.locale) }
    }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Heading(stringResource(R.string.settings_language))
            LanguagePicker(languages, language, onSelect = {
                language = it
                app.settings.language = it
            })
            Heading(stringResource(R.string.onboarding_goal))
            GoalPicker(goal, onSelect = {
                goal = it
                app.settings.dailyGoal = it
            })
            Heading(stringResource(R.string.settings_exercises))
            if (hasVoice == false && current != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(stringResource(R.string.settings_no_voice, current.name), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            try {
                                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                            } catch (e: ActivityNotFoundException) {
                                // No speech engine on this device at all; nothing to open.
                            }
                        }) { Text(stringResource(R.string.settings_install_voice)) }
                    }
                }
            }
            Toggle(
                stringResource(R.string.settings_listening), stringResource(R.string.settings_listening_text),
                checked = listening && hasVoice != false, enabled = hasVoice != false,
            ) {
                listening = it
                app.settings.listening = it
            }
            Toggle(stringResource(R.string.settings_speaking), stringResource(R.string.settings_speaking_text), speaking) {
                speaking = it
                app.settings.speaking = it
            }
            Heading(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
