package com.resqlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactValidationTest {
    @Test fun `normalizes a readable phone number`() {
        val result = validateContact("Amina", "+880 1712-345678")
        assertTrue(result.valid)
        assertEquals("+8801712345678", result.normalizedPhone)
    }

    @Test fun `rejects missing name and short phone`() {
        val result = validateContact("  ", "123")
        assertFalse(result.valid)
        assertTrue(result.nameError)
        assertTrue(result.phoneError)
    }
}
