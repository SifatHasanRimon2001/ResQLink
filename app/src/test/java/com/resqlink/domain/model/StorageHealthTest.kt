package com.resqlink.domain.model

import com.resqlink.data.security.StorageHealth
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class StorageHealthTest {
    @Test fun failedReadsWaitForRetryWithoutEmittingEmptyData() = runBlocking {
        val health = StorageHealth()
        var available = false
        val result = async {
            health.observe("database", flow {
                if (!available) throw IOException("Test failure")
                emit("saved data")
            }).first()
        }
        withTimeout(2_000) { health.failedSources.first { it.isNotEmpty() } }
        assertFalse(result.isCompleted)
        available = true
        health.retry()
        assertEquals("saved data", withTimeout(2_000) { result.await() })
        assertTrue(health.failedSources.value.isEmpty())
    }

    @Test fun successfulSourceDoesNotHideOtherFailedSources() = runBlocking {
        val health = StorageHealth()
        val failed = async {
            health.observe<String>("settings", flow { throw IOException() }).first()
        }
        withTimeout(2_000) { health.failedSources.first { it.isNotEmpty() } }
        health.observe("contacts", flow { emit(emptyList<String>()) }).first()
        assertEquals(setOf("settings"), health.failedSources.value)
        failed.cancelAndJoin()
    }

    @Test fun cancellationIsNeverReportedAsStorageFailure() = runBlocking {
        val health = StorageHealth()
        try {
            health.observe<Unit>("database", flow { throw CancellationException() }).first()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(health.failedSources.value.isEmpty())
        }
    }
}
