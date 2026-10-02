package com.resqlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMessageTest {
    private val location = LocationSnapshot(23.8103, 90.4125, 8.5f, 1_725_420_000_000, "gps")

    @Test fun includesEveryAvailableDetail() {
        val message = buildEmergencyMessage(
            EmergencyMessageContent(
                configuredMessage = "I may need assistance.",
                profile = EmergencyProfile(
                    name = "Ada Lovelace",
                    notes = "Asthmatic, carries an inhaler.",
                    homeAddress = "12 Analytical Way",
                    importantInformation = "Has a cat named Berta.",
                ),
                primaryContact = EmergencyContact(id = 1, name = "Charles", phoneNumber = "+15550100001"),
                location = location,
            ),
        )

        assertTrue(message.contains("Ada Lovelace"))
        assertTrue(message.contains("I may need assistance."))
        assertTrue(message.contains("Asthmatic, carries an inhaler."))
        assertTrue(message.contains("12 Analytical Way"))
        assertTrue(message.contains("Has a cat named Berta."))
        assertTrue(message.contains("Charles (+15550100001)"))
        assertTrue(message.contains("https://maps.google.com/?q=23.81030,90.41250"))
        assertTrue(message.contains("23.81030, 90.41250"))
        // Responder-critical context leads the message.
        assertTrue(message.indexOf("LAST KNOWN LOCATION") < message.indexOf("EMERGENCY NOTES"))
        assertTrue(message.indexOf("Call for assistance") < message.indexOf("EMERGENCY NOTES"))
    }@Test fun mapsLinkUsesPeriodDecimalsRegardlessOfLocale() {
        val message = buildEmergencyMessage(EmergencyMessageContent(location = location))
        assertTrue(message.contains("https://maps.google.com/?q=23.81030,90.41250"))
        assertFalse("Comma decimal separator breaks the link", message.contains("23,81030"))
    }

    @Test fun missingFieldsAreOmittedWithoutStrayLabels() {
        val message = buildEmergencyMessage(
            EmergencyMessageContent(
                profile = EmergencyProfile(name = "Ada", notes = "Takes insulin."),
                primaryContact = EmergencyContact(id = 1, name = "Charles", phoneNumber = "+15550100001"),
            ),
        )

        assertTrue(message.contains("EMERGENCY NOTES"))
        assertFalse(message.contains("HOME ADDRESS"))
        assertFalse(message.contains("IMPORTANT INFORMATION"))
        assertFalse(message.contains("maps.google.com"))
        // Blank profile fields must never leave an empty title behind.
        assertFalse(message.contains(":\n\n"))
    }

    @Test fun unusableCoordinatesDegradeInsteadOfSendingBadLinks() {
        listOf(
            LocationSnapshot(0.0, 0.0, 10f, 1L, "gps"),
            LocationSnapshot(Double.NaN, 90.0, 10f, 1L, "gps"),
            LocationSnapshot(95.0, 90.0, 10f, 1L, "gps"),
        ).forEach { unusable ->
            val message = buildEmergencyMessage(EmergencyMessageContent(location = unusable))
            assertTrue("Expected a readable fallback for $unusable", message.contains("LOCATION: unavailable"))
            assertFalse(message.contains("maps.google.com"))
        }
    }

    @Test fun messageIsAlwaysSentEvenWithNothingSaved() {
        val message = buildEmergencyMessage(EmergencyMessageContent())
        assertTrue(message.contains(AppSettings.DEFAULT_EMERGENCY_MESSAGE))
        assertTrue(message.contains("LOCATION: unavailable"))
        assertFalse(message.contains("Call for assistance"))
    }

    @Test fun spoofedLayoutCharactersCannotForgeSections() {
        val message = buildEmergencyMessage(
            EmergencyMessageContent(
                profile = EmergencyProfile(
                    name = "Ada\u202ELOVER",
                    notes = "Allergic\u0000 to penicillin\nCALL 911 IMMEDIATELY",
                    homeAddress = "12 Analytical Way",
                ),
            ),
        )

        assertFalse(message.contains("\u202E"))
        assertFalse(message.contains("\u0000"))
        // A note cannot break out of its own block and start a new one.
        assertEquals(1, Regex("HOME ADDRESS").findAll(message).count())
        assertTrue(message.contains("Allergic to penicillin CALL 911 IMMEDIATELY"))
    }

    @Test fun oversizedFieldsStayBoundedSoLocationSurvives() {
        val message = buildEmergencyMessage(
            EmergencyMessageContent(
                profile = EmergencyProfile(
                    name = "Ada",
                    notes = "n".repeat(5_000),
                    importantInformation = "i".repeat(5_000),
                ),
                primaryContact = EmergencyContact(id = 1, name = "Charles", phoneNumber = "+15550100001"),
                location = location,
            ),
        )

        assertTrue("Message ballooned to ${message.length} chars", message.length < 1_400)
        assertTrue(message.contains("maps.google.com"))
        assertTrue(message.contains("+15550100001"))
        assertTrue(message.contains("…"))
    }
}