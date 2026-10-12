package pt.tiago.blocknight.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.progress.Settings
import pt.tiago.blocknight.ui.components.PrimaryButton
import pt.tiago.blocknight.ui.components.SecondaryButton
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Definições (PLANO §3.5): som, vibração, controlos, canhoto, DAS/ARR e cópia do progresso. */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    status: String?,
    importPreview: Int?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlockNightColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .widthIn(max = 520.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryButton(text = stringResource(R.string.cd_back), onClick = onBack)
        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge.copy(color = BlockNightColors.TextStrong, fontSize = 28.sp))
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SwitchRow(stringResource(R.string.settings_sound), settings.sound) { onChange(settings.copy(sound = it)) }
            SwitchRow(stringResource(R.string.settings_vibration), settings.vibration) { onChange(settings.copy(vibration = it)) }
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = stringResource(R.string.settings_controls), style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong))
            Segmented(
                options = listOf(
                    Settings.CONTROLS_GESTURES to stringResource(R.string.settings_controls_gestures),
                    Settings.CONTROLS_BUTTONS to stringResource(R.string.settings_controls_buttons),
                ),
                selected = settings.controls,
                onSelect = { onChange(settings.copy(controls = it)) },
            )
            SwitchRow(stringResource(R.string.settings_left_handed), settings.leftHanded) { onChange(settings.copy(leftHanded = it)) }
            Stepper(
                label = stringResource(R.string.settings_das) + " · " + settings.das + " " + stringResource(R.string.settings_tick_unit),
                value = settings.das,
                range = 4..20,
                onChange = { onChange(settings.copy(das = it)) },
            )
            Stepper(
                label = stringResource(R.string.settings_arr) + " · " + settings.arr + " " + stringResource(R.string.settings_tick_unit),
                value = settings.arr,
                range = 1..6,
                onChange = { onChange(settings.copy(arr = it)) },
            )
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.settings_export), style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong))
            SecondaryButton(text = stringResource(R.string.settings_export), onClick = onExport, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = stringResource(R.string.settings_import), onClick = onImport, modifier = Modifier.fillMaxWidth())
            status?.let { Text(text = it, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted)) }
            importPreview?.let { count -> ConfirmImport(count = count, onConfirm = onConfirmImport, onCancel = onCancelImport) }
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong))
            Text(text = stringResource(R.string.settings_about_text), style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text))
            // A small accent caption separates the author from the description.
            Text(
                text = stringResource(R.string.settings_about_author_label).uppercase(),
                style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Accent, fontSize = 11.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(text = stringResource(R.string.settings_about_author), style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.TextStrong))
            Text(text = stringResource(R.string.settings_about_email), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().card(radius = 14.dp).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(
                        if (on) BlockNightColors.Accent.copy(alpha = 0.14f) else BlockNightColors.Surface,
                        androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(color = if (on) BlockNightColors.Accent else BlockNightColors.TextMuted),
                )
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text), modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(48.dp).card(radius = 12.dp).clickable(enabled = value > range.first) { onChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) { Text(text = "−", style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong)) }
        Box(
            modifier = Modifier.size(48.dp).card(radius = 12.dp).clickable(enabled = value < range.last) { onChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) { Text(text = "+", style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong)) }
    }
}

@Composable
fun ConfirmImport(count: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(R.string.settings_import_preview, count), style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.TextStrong))
        Text(text = stringResource(R.string.settings_import_confirm), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
        PrimaryButton(text = stringResource(R.string.settings_import_yes), onClick = onConfirm)
        SecondaryButton(text = stringResource(R.string.settings_import_no), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}
