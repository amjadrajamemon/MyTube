package com.drivedeck.tubedeck.data.repository

import com.drivedeck.tubedeck.data.model.DownloadTask
import com.drivedeck.tubedeck.data.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueuePolicyTest {
    private fun task(
        id: String,
        status: TaskStatus,
        session: Long,
        created: Long
    ) = DownloadTask(
        id = id,
        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        videoId = "dQw4w9WgXcQ",
        title = id,
        thumbnailUrl = "",
        status = status,
        createdAt = created,
        sessionId = session,
        eventAt = created
    )

    @Test fun pendingExcludesCompletedActivityOnlyRows() {
        val items = listOf(
            task("q", TaskStatus.QUEUED, 2, 20),
            task("f", TaskStatus.FAILED, 2, 21),
            task("done", TaskStatus.COMPLETED, 2, 22),
            task("cancel", TaskStatus.CANCELLED, 2, 23),
            task("retry", TaskStatus.RETRIED, 2, 24)
        )
        assertEquals(listOf("q", "f"), DownloadQueuePolicy.pendingTasks(items).map { it.id })
    }

    @Test fun activeNewestSessionWinsNotificationScope() {
        val items = listOf(
            task("oldDone", TaskStatus.COMPLETED, 1, 10),
            task("oldFailed", TaskStatus.FAILED, 1, 11),
            task("newQueued", TaskStatus.QUEUED, 2, 20),
            task("newDone", TaskStatus.COMPLETED, 2, 21)
        )
        val current = DownloadQueuePolicy.currentSessionTasks(items)
        assertEquals(setOf("newQueued", "newDone"), current.map { it.id }.toSet())
        assertTrue(current.all { it.sessionId == 2L })
    }
}
