package com.drivedeck.tubedeck.ui.components

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.auth.GoogleAccountProfile

@Composable
fun GoogleAccountButton(
    account: GoogleAccountProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                contentDescription = if (account == null) "Connect YouTube account" else account.displayName,
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
fun GoogleAuthorizationProblemDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google sign-in unavailable") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
