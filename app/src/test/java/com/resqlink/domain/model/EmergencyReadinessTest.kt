package com.resqlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyReadinessTest {
    @Test
    fun `profile requires both name and emergency notes`() {
        assertFalse(EmergencyProfile().isConfigured)
        assertFalse(EmergencyProfile(name = "Sam").isConfigured)
        assertFalse(EmergencyProfile(notes = "Uses an inhaler").isConfigured)
        assertTrue(EmergencyProfile(name = "Sam", notes = "Uses an inhaler").isConfigured)
    }

    @Test
    fun `most trusted chooses enabled contact with lowest priority`() {
        val contacts = listOf(
            EmergencyContact(id = 1, name = "Backup", phoneNumber = "+10000001", priority = 1),
            EmergencyContact(id = 2, name = "Primary", phoneNumber = "+10000002", priority = 0),
            EmergencyContact(id = 3, name = "Disabled", phoneNumber = "+10000003", priority = -1, enabled = false),
        )

        assertEquals(2L, contacts.mostTrusted()?.id)
    }
}
