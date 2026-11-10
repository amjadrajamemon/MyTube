package com.drivedeck.tubedeck.data.repository

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.drivedeck.tubedeck.data.model.OfflineMedia
import java.io.File

/**
 * Android Sharesheet interoperability, including compatible local-network
 * transfer applications such as Local-Beam if installed and share-enabled.
 * Only a single temporary READ grant is supplied; no public file path leaks.
 */
internal object OfflineMediaShare {
    fun openChooser(activity: Activity, media: OfflineMedia) {
        check(OfflineVaultStorage.isReadable(activity, media)) {
            "Saved file unavailable. Reconnect USB/SD storage."
        }
        val source = Uri.parse(media.mediaUri)
        val shareUri = when (source.scheme) {
            "content" -> source
            "file" -> FileProvider.getUriForFile(
                activity, "${activity.packageName}.vault.files",
                File(requireNotNull(source.path))
            )
            else -> error("Only verified local media can be shared")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (media.isAudioOnly) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TITLE, media.title)
            clipData = android.content.ClipData.newUri(
                activity.contentResolver, media.title, shareUri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "Send saved video to…"))
    }
}
