package ru.fourvic.chess.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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

/** Version, legal documents on the website, and the open-source licenses bundled with the app. */
@Composable
fun AboutSection() {
    val uri = LocalUriHandler.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    HorizontalDivider()
    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.build_label, BuildConfig.STORE, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyMedium,
    )
    listOf(
        R.string.about_privacy to "/privacy.html",
        R.string.about_terms to "/terms.html",
        R.string.about_delete_account to "/delete-account.html",
    ).forEach { (label, path) ->
        TextButton(onClick = { uri.openUri(BuildConfig.SITE_URL + path) }) { Text(stringResource(label)) }
    }
    TextButton(onClick = { showLicenses = true }) { Text(stringResource(R.string.about_licenses)) }

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
