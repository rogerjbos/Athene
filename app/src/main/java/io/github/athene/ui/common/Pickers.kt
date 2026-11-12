package io.github.athene.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.athene.R
import io.github.athene.data.Language
import io.github.athene.data.Settings

/** A full-width row that can be picked; the picked one is outlined in the primary colour. */
@Composable
fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                }
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary)
        }
    }
}

@Composable
fun LanguagePicker(languages: List<Language>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (language in languages) {
            SelectableRow(
                selected = language.code == selected,
                onClick = { onSelect(language.code) },
                leading = { Text(language.flag, style = MaterialTheme.typography.headlineMedium) },
                title = language.name,
                subtitle = language.nativeName,
            )
        }
    }
}

@Composable
fun GoalPicker(selected: Int, onSelect: (Int) -> Unit) {
    val names = listOf(R.string.goal_casual, R.string.goal_regular, R.string.goal_serious, R.string.goal_intense)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Settings.DAILY_GOALS.forEachIndexed { index, goal ->
            SelectableRow(
                selected = goal == selected,
                onClick = { onSelect(goal) },
                title = stringResource(names[index]),
                subtitle = stringResource(R.string.goal_xp, goal),
            )
        }
    }
}
