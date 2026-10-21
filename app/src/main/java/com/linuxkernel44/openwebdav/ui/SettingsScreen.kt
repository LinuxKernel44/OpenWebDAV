package com.linuxkernel44.openwebdav.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.linuxkernel44.openwebdav.BuildConfig
import com.linuxkernel44.openwebdav.R
import com.linuxkernel44.openwebdav.data.Credentials
import com.linuxkernel44.openwebdav.data.LanguagePref
import com.linuxkernel44.openwebdav.data.ThemeMode
import com.linuxkernel44.openwebdav.util.ExternalOpen

private const val REPO_URL = "https://github.com/LinuxKernel44/OpenWebDAV"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, credentials: Credentials, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme by viewModel.theme.collectAsState()
    val language by viewModel.language.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionTitle(stringResource(R.string.appearance))
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val modes = ThemeMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(stringResource(if (mode == ThemeMode.DARK) R.string.theme_dark else R.string.theme_light))
                    }
                }
            }

            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            LanguagePref.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = language == option, role = Role.RadioButton) { viewModel.setLanguage(option) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = language == option, onClick = null)
                    Text(
                        stringResource(
                            when (option) {
                                LanguagePref.AUTO -> R.string.language_auto
                                LanguagePref.EN -> R.string.language_en
                                LanguagePref.FR -> R.string.language_fr
                            },
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle(stringResource(R.string.account))
            Text(
                stringResource(R.string.signed_in_as, credentials.username),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                credentials.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(stringResource(R.string.log_out), modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle(stringResource(R.string.about))
            Text(
                "${stringResource(R.string.app_name)} · ${stringResource(R.string.version, BuildConfig.VERSION_NAME)}",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.source_code),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { ExternalOpen.openWebPage(context, REPO_URL) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.log_out_title)) },
            text = { Text(stringResource(R.string.log_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text(stringResource(R.string.log_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
