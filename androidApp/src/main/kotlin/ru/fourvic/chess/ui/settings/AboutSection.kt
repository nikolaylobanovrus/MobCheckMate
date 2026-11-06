package ru.fourvic.chess.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.BuildConfig
import ru.fourvic.chess.R
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.SurfaceCard

/** Version, legal documents on the website, and the open-source licenses bundled with the app. */
@Composable
fun AboutSection() {
    val uri = LocalUriHandler.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.about_title))
    SurfaceCard(contentPadding = PaddingValues(6.dp)) {
        SettingRow(
            R.drawable.ic_info,
            stringResource(R.string.app_name),
            subtitle = stringResource(R.string.build_label, BuildConfig.STORE, BuildConfig.VERSION_NAME),
        )
        listOf(
            Triple(R.string.about_privacy, "/privacy.html", R.drawable.ic_lock),
            Triple(R.string.about_terms, "/terms.html", R.drawable.ic_description),
            Triple(R.string.about_delete_account, "/delete-account.html", R.drawable.ic_delete),
        ).forEach { (label, path, icon) ->
            SettingRow(icon, stringResource(label), onClick = { uri.openUri(BuildConfig.SITE_URL + path) }) {
                AppIcon(R.drawable.ic_north_east, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingRow(R.drawable.ic_description, stringResource(R.string.about_licenses), onClick = { showLicenses = true }) {
            AppIcon(R.drawable.ic_chevron_right, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showLicenses) {
        val context = LocalContext.current
        val text = rememberSaveable {
            runCatching { context.assets.open("licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = {
                Text(
                    text,
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
