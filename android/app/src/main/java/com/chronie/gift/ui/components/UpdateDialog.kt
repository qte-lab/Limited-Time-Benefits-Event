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
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateDialog(
    show: Boolean,
    versionName: String,
    changelog: String,
    fileSize: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismiss = LocalDismissState.current
    
    if (show) {
        WindowDialog(
            title = stringResource(R.string.update_dialog_title),
            summary = changelog,
            show = true,
            onDismissRequest = {
                dismiss?.invoke()
                onDismiss()
            }
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
                    onClick = {
                        dismiss?.invoke()
                        onUpdate()
                    },
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
}