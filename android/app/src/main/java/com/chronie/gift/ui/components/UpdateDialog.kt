package com.chronie.gift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chronie.gift.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.LocalDismissState

@Composable
fun UpdateDialog(
    show: Boolean,
    version: String = "",
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
            show = show,
            onDismissRequest = {
                dismiss?.invoke()
                onDismiss()
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }
        }
    }
}