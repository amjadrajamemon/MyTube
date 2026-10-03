package com.drivedeck.tubedeck.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.auth.GoogleAccountProfile
import com.drivedeck.tubedeck.auth.GoogleDeviceCodePrompt

@Composable
fun GoogleAccountButton(
    account: GoogleAccountProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabelWhenSignedOut: Boolean = false
) {
    if (account == null && showLabelWhenSignedOut) {
        TextButton(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text("Sign in with Google", color = Color.White)
        }
        return
    }

    IconButton(onClick = onClick, modifier = modifier.size(38.dp)) {
        val photo = account?.photoUrl
        if (!photo.isNullOrBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = account.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(CircleShape)
            )
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = if (account == null) "Sign in with Google" else account.displayName,
                tint = Color.White,
                modifier = Modifier.size(29.dp)
            )
        }
    }
}

@Composable
fun GoogleAccountDialog(
    account: GoogleAccountProfile,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val photo = account.photoUrl
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = account.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                )
            } else {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(54.dp))
            }
        },
        title = { Text(account.displayName) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (account.email.isNotBlank()) {
                    Text(account.email)
                }
                account.subscriptionCount?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("$it subscriptions")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSignOut) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun GoogleDeviceCodeDialog(
    prompt: GoogleDeviceCodePrompt,
    status: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in with Google code") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Open Google and enter this code:")
                Text(
                    prompt.userCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    prompt.verificationUrl,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (status.isNotBlank()) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(prompt.verificationUrl))
                        )
                    }
                }
            ) { Text("Open Google") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("MyTube Google sign-in code", prompt.userCode)
                        )
                    }
                ) { Text("Copy code") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}


@Composable
fun GoogleAuthorizationProblemDialog(
    message: String,
    registrationInfo: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSignInWithCode: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google sign-in needs attention") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    message.ifBlank {
                        "Google did not complete the YouTube authorization request."
                    }
                )
                Text(
                    registrationInfo,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("Retry sign-in") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onSignInWithCode?.let { signInWithCode ->
                    TextButton(onClick = signInWithCode) { Text("Sign in with code") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
