package ca.kloosterman.bccmediatv.ui.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ca.kloosterman.bccmediatv.R
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.kloosterman.bccmediatv.data.LanguageRepository

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: (hasOtherProfiles: Boolean) -> Unit,
    onSwitchAccount: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var activePicker by remember { mutableStateOf<String?>(null) }
    val delayOptions = listOf(0, 5, 10, 15, 30)

    val context = LocalContext.current

    BackHandler {
        if (activePicker != null) activePicker = null else onBack()
    }

    val languageOptions = LanguageRepository.SUPPORTED_LANGUAGES.map { (code, name) -> code to name }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))

                SettingRow(
                    label = stringResource(R.string.settings_app_language),
                    value = languageName(state.selectedLanguage),
                    onClick = { activePicker = "content" }
                )
                SettingRow(
                    label = stringResource(R.string.settings_preferred_audio),
                    value = languageName(state.selectedAudioLanguage),
                    onClick = { activePicker = "audio" }
                )
                SettingRow(
                    label = stringResource(R.string.settings_subtitles),
                    value = state.selectedSubtitleLanguage?.let { languageName(it) } ?: stringResource(R.string.settings_subtitles_off),
                    onClick = { activePicker = "subtitle" }
                )
                SettingRow(
                    label = stringResource(R.string.settings_autoplay),
                    value = if (state.autoPlayDelay == 0) stringResource(R.string.settings_autoplay_off)
                            else stringResource(R.string.autoplay_delay_seconds, state.autoPlayDelay),
                    onClick = { activePicker = "autoplay_delay" }
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onSwitchAccount,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.settings_switch_account), fontSize = 18.sp)
                }
                Button(onClick = { onLogout(viewModel.logout()) }) {
                    Text(stringResource(R.string.settings_remove_account), fontSize = 18.sp)
                }
            }

            if (activePicker == "content") {
                LanguagePickerDialog(
                    title = stringResource(R.string.settings_app_language),
                    options = languageOptions,
                    selected = state.selectedLanguage,
                    onSelect = { it?.let { code -> viewModel.setLanguage(code); (context as? Activity)?.recreate() }; activePicker = null },
                    onDismiss = { activePicker = null }
                )
            }
            if (activePicker == "audio") {
                LanguagePickerDialog(
                    title = stringResource(R.string.settings_preferred_audio),
                    options = languageOptions,
                    selected = state.selectedAudioLanguage,
                    onSelect = { it?.let { code -> viewModel.setAudioLanguage(code) }; activePicker = null },
                    onDismiss = { activePicker = null }
                )
            }
            if (activePicker == "subtitle") {
                LanguagePickerDialog(
                    title = stringResource(R.string.settings_subtitles),
                    options = listOf(null to stringResource(R.string.settings_subtitles_off)) + languageOptions,
                    selected = state.selectedSubtitleLanguage,
                    onSelect = { viewModel.setSubtitleLanguage(it); activePicker = null },
                    onDismiss = { activePicker = null }
                )
            }
            if (activePicker == "autoplay_delay") {
                LanguagePickerDialog(
                    title = stringResource(R.string.settings_autoplay),
                    options = delayOptions.map { s ->
                        s.toString() to if (s == 0) stringResource(R.string.settings_autoplay_off)
                                        else stringResource(R.string.autoplay_delay_seconds, s)
                    },
                    selected = state.autoPlayDelay.toString(),
                    onSelect = { it?.toIntOrNull()?.let { s -> viewModel.setAutoPlayDelay(s) }; activePicker = null },
                    onDismiss = { activePicker = null }
                )
            }
        }
    }
}

private fun languageName(code: String): String =
    LanguageRepository.SUPPORTED_LANGUAGES.find { it.first == code }?.second ?: code

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(420.dp),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(value, style = MaterialTheme.typography.bodyMedium)
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LanguagePickerDialog(
    title: String,
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequesters = remember(options) { List(options.size) { FocusRequester() } }
    val initialIndex = options.indexOfFirst { (code, _) -> code == selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { runCatching { focusRequesters[initialIndex].requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            options.forEachIndexed { index, (code, label) ->
                val isSelected = code == selected
                Button(
                    onClick = { onSelect(code) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[index]),
                    colors = if (isSelected) ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary
                    ) else ButtonDefaults.colors()
                ) {
                    Text(label, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
