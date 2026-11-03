package com.drivedeck.tubedeck.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** No accessibility permissions or key logger: keys are inspected only while this dialog is open. */
internal object TvRemoteKeyLearningPolicy {
    private val protectedKeys = setOf(
        KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ESCAPE
    )

    fun allowed(keyCode: Int): Boolean =
        keyCode in 1..KeyEvent.getMaxKeyCode() && keyCode !in protectedKeys

    fun remap(
        keyCode: Int,
        repeatCount: Int,
        nextCode: Int,
        previousCode: Int
    ): TvPlayerKeyDecision? {
        if (repeatCount != 0 || !allowed(keyCode)) return null
        return when {
            keyCode == nextCode && nextCode > 0 -> TvPlayerKeyDecision(TvPlayerKeyAction.NEXT)
            keyCode == previousCode && previousCode > 0 -> TvPlayerKeyDecision(TvPlayerKeyAction.PREVIOUS)
            else -> null
        }
    }
}

@Composable
internal fun RemoteInputTrainingDialog(
    nextCode: Int,
    previousCode: Int,
    onNextCode: (Int) -> Unit,
    onPreviousCode: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var learn by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Choose Next or Previous, then press one remote or keyboard button.") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(learn) {
        if (learn.isNotBlank()) {
            delay(100)
            runCatching { focus.requestFocus() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TV remote / keyboard learning") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Current mapping · Next: ${if (nextCode == 0) "Default" else KeyEvent.keyCodeToString(nextCode)}" +
                        " · Previous: ${if (previousCode == 0) "Default" else KeyEvent.keyCodeToString(previousCode)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { learn = "Next"; status = "Press the key you want for Next…" }) {
                    Text("Learn Next button")
                }
                Button(onClick = { learn = "Previous"; status = "Press the key you want for Previous…" }) {
                    Text("Learn Previous button")
                }
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .then(if (learn.isNotBlank()) Modifier.focusRequester(focus) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (learn.isBlank() || event.type != KeyEventType.KeyDown ||
                                event.nativeKeyEvent.repeatCount > 0
                            ) return@onPreviewKeyEvent false
                            val code = event.nativeKeyEvent.keyCode
                            if (code == KeyEvent.KEYCODE_BACK) {
                                learn = ""
                                status = "Key learning cancelled"
                                return@onPreviewKeyEvent true
                            }
                            if (!TvRemoteKeyLearningPolicy.allowed(code)) {
                                status = "That key controls system navigation or volume; select a different button."
                                return@onPreviewKeyEvent true
                            }
                            if (learn == "Next") {
                                onNextCode(code)
                                if (code == previousCode) onPreviousCode(0)
                            } else {
                                onPreviousCode(code)
                                if (code == nextCode) onNextCode(0)
                            }
                            status = "Captured ${KeyEvent.keyCodeToString(code)} for $learn. Save Settings to activate."
                            learn = ""
                            true
                        }
                        .focusable()
                        .padding(14.dp)
                ) {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    onNextCode(0); onPreviousCode(0)
                    learn = ""
                    status = "Default TV remote buttons restored; save Settings to apply."
                }) { Text("Reset mappings") }
                Text(
                    "Hardware keys can be learned only when this dialog has keyboard focus. Mouse clicks and typing remain standard Android behavior.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
