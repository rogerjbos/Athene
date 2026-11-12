package io.github.athene.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.athene.R
import io.github.athene.data.Language
import io.github.athene.athene
import io.github.athene.ui.common.GoalPicker
import io.github.athene.ui.common.LanguagePicker

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.athene
    val languages by produceState(emptyList<Language>()) { value = app.content.languages() }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var goal by rememberSaveable { mutableIntStateOf(app.settings.dailyGoal) }
    // With a single course there is nothing to choose.
    val chosen = language ?: languages.singleOrNull()?.code

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(24.dp))
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(120.dp))
                Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                LanguagePicker(languages, chosen, onSelect = { language = it })
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.onboarding_goal), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                GoalPicker(goal, onSelect = { goal = it })
                Spacer(Modifier.height(24.dp))
            }
            Button(
                onClick = {
                    app.settings.language = chosen
                    app.settings.dailyGoal = goal
                    onDone()
                },
                enabled = chosen != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
            ) {
                Text(stringResource(R.string.onboarding_start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
