package com.chronie.gift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chronie.gift.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Update available prompt.
 *
 * The dialog must stay composed across the whole hide animation, so [show] is
 * handed straight to [WindowDialog] instead of wrapping it in `if (show)`. That
 * wrapper used to tear the dialog out of the composition the instant the flag
 * flipped, which skipped the 260 ms exit animation and made it vanish. The
 * caller keeps the update metadata until after the animation anyway.
 */
@Composable
fun UpdateDialog(
    show: Boolean,
    versionName: String,
    changelog: String,
    fileSize: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        title = stringResource(R.string.update_dialog_title),
        summary = changelog,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.update_version_name).format(versionName),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }

            TextButton(
                text = stringResource(R.string.update_dialog_button).format(fileSize),
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.update_dialog_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                )
            }
        }
    }
}
