package com.drivedeck.tubedeck.data.repository

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalDownloadCommitTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun completesAppPrivateDownloadWithoutFolderPicker() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val staged = temp.newFile("task-id.mp4").apply { writeBytes(bytes) }
        val completed = File(temp.root, "video_task-id.mp4")

        assertEquals(completed, LocalDownloadCommit.commit(staged, completed))
        assertArrayEquals(bytes, completed.readBytes())
        assertFalse(staged.exists())
    }

    @Test fun doesNotOverwriteAnExistingDifferentFile() {
        val staged = temp.newFile("task-id.mp4").apply { writeBytes(byteArrayOf(9, 8)) }
        val completed = temp.newFile("video_task-id.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertThrows(IllegalStateException::class.java) {
            LocalDownloadCommit.commit(staged, completed)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), completed.readBytes())
        assertTrue(staged.exists())
    }

    @Test fun acceptsIdenticalCommittedFileAfterInterruptedCatalogSave() {
        val staged = temp.newFile("task-id.mp4").apply { writeBytes(byteArrayOf(9, 8)) }
        val completed = temp.newFile("video_task-id.mp4").apply { writeBytes(byteArrayOf(9, 8)) }

        assertEquals(completed, LocalDownloadCommit.commit(staged, completed))
        assertArrayEquals(byteArrayOf(9, 8), completed.readBytes())
    }

    @Test fun refusesSameSizeButDifferentExistingMedia() {
        val staged = temp.newFile("task-id.mp4").apply { writeBytes(byteArrayOf(9, 8)) }
        val completed = temp.newFile("video_task-id.mp4").apply { writeBytes(byteArrayOf(3, 4)) }

        assertThrows(IllegalStateException::class.java) {
            LocalDownloadCommit.commit(staged, completed)
        }
        assertArrayEquals(byteArrayOf(3, 4), completed.readBytes())
        assertTrue(staged.exists())
    }

    @Test fun refusesMissingOrEmptyStagingFile() {
        val staged = File(temp.root, "missing.mp4")
        val completed = File(temp.root, "video.mp4")
        assertThrows(IllegalArgumentException::class.java) {
            LocalDownloadCommit.commit(staged, completed)
        }
        assertFalse(completed.exists())
    }
}
