package com.drivedeck.tubedeck.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class VerifiedFileTransferTest {
    @Test fun moveDeletesSourceOnlyAfterCompletedAndVerifiedCopy() {
        val output = ByteArrayOutputStream()
        var removed = false
        val bytes = VerifiedFileTransfer.copyThenCommit(
            { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, { output }, 3, { output.size().toLong() }, true,
            { assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray()); removed = true; true }, { fail("No rollback expected") })
        assertEquals(3L, bytes)
        assertTrue(removed)
    }
    @Test fun truncatedCopyPreservesSourceAndRemovesPartialDestination() {
        var sourceDeleted = false
        var rolledBack = false
        val result = runCatching { VerifiedFileTransfer.copyThenCommit(
            { ByteArrayInputStream(byteArrayOf(1)) }, { ByteArrayOutputStream() }, 10, { 1 }, true,
            { sourceDeleted = true; true }, { rolledBack = true }) }
        assertTrue(result.isFailure)
        assertFalse(sourceDeleted)
        assertTrue(rolledBack)
    }
    @Test fun cancellationOrWriteFailurePreservesSource() {
        var sourceDeleted = false
        var rolledBack = false
        val result = runCatching { VerifiedFileTransfer.copyThenCommit(
            { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, { ByteArrayOutputStream() }, 3, { 3 }, true,
            { sourceDeleted = true; true }, { rolledBack = true }, { error("Transfer interrupted") }) }
        assertTrue(result.isFailure)
        assertFalse(sourceDeleted)
        assertTrue(rolledBack)
    }
    @Test fun copyingDoesNotDeleteSource() {
        VerifiedFileTransfer.copyThenCommit({ ByteArrayInputStream(byteArrayOf(1)) }, { ByteArrayOutputStream() },
            1, { 1 }, false, { fail("Copy must retain source"); false }, {})
    }
}
