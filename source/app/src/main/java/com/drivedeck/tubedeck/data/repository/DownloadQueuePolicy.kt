package com.drivedeck.tubedeck.data.repository

import com.drivedeck.tubedeck.data.model.DownloadTask
import com.drivedeck.tubedeck.data.model.TaskStatus

internal object DownloadQueuePolicy {
    fun pendingTasks(tasks: List<DownloadTask>): List<DownloadTask> =
        tasks.filter {
            it.status == TaskStatus.QUEUED ||
                it.status == TaskStatus.DOWNLOADING ||
                it.status == TaskStatus.PAUSED ||
                it.status == TaskStatus.FAILED
        }

    fun currentSessionTasks(tasks: List<DownloadTask>): List<DownloadTask> {
        if (tasks.isEmpty()) return emptyList()
        val activeAnchor = tasks
            .filter {
                it.status == TaskStatus.QUEUED ||
                    it.status == TaskStatus.DOWNLOADING ||
                    it.status == TaskStatus.PAUSED
            }
            .maxByOrNull { it.createdAt }
        val anchor = activeAnchor ?: tasks.maxByOrNull { maxOf(it.eventAt, it.createdAt) }
            ?: return emptyList()
        if (anchor.sessionId <= 0L) return tasks
        return tasks.filter { it.sessionId == anchor.sessionId }
    }
}
