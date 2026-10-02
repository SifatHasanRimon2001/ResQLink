package com.resqlink.domain.model

import com.resqlink.core.util.attemptOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SecurityValidationTest {
    @Test fun rejectsDialCodesAndExtensionsWithoutSilentlyChangingDestination() {
        listOf("*21*+15550100001#", "+15550100001,123", "+15550100001;123",
            "+15550100001 ext 22", "tel:+15550100001", "abc15550100001",
            "++15550100001", "1555+0100001", "+15550100001\n123").forEach {
            assertFalse("Accepted unsafe dial input", validateContact("Test", it).valid)
        }
    }

    @Test fun rejectsUnicodeDigitsAndInvisibleCharacters() {
        listOf("١٢٣٤٥٦٧٨٩", "１２３４５６７８９", "+1555\u200B0100001",
            "+1555\u202E0100001", "+1555\u00000100001").forEach {
            assertFalse(validateContact("Test", it).valid)
        }
    }

    @Test fun acceptsPresentationSeparators() {
        assertEquals("+15550100001", validateContact("Test", " +1 (555) 010-0001 ").normalizedPhone)
        assertTrue(validateContact("Test", "555.010.0001").valid)
    }

    @Test fun boundsNumbersAndNames() {
        assertFalse(validateContact("Test", "1".repeat(16)).valid)
        assertFalse(validateContact("Test", "1".repeat(6)).valid)
        assertTrue(validateContact("Test", "1".repeat(15)).valid)
        assertFalse(validateContact("A".repeat(201), "+15550100001").valid)
        assertFalse(validateContact("Test\nInjected", "+15550100001").valid)
    }

    @Test fun emptyMessageHasUsableFallback() {
        assertEquals(AppSettings.DEFAULT_EMERGENCY_MESSAGE, normalizeEmergencyMessage(" \n\t"))
        assertEquals("Help", normalizeEmergencyMessage(" Help "))
    }

    @Test fun messageHasBoundedSmsCost() {
        assertEquals(500, normalizeEmergencyMessage("x".repeat(10_000)).length)
    }

    @Test fun operationFailuresCanBeReported() = runBlocking {
        assertTrue(attemptOperation<Unit> { throw IllegalStateException("Test failure") }.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedToSuccessOrFailure() = runBlocking {
        attemptOperation<Unit> { throw CancellationException("Test cancellation") }
        Unit
    }
}
