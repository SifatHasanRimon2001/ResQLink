package com.resqlink.domain.model

import java.util.Locale

/**
 * Everything the automatic SOS text can carry. Any field may be missing;
 * [buildEmergencyMessage] omits unavailable data instead of sending empty
 * labels or failing.
 */
data class EmergencyMessageContent(
    val configuredMessage: String = "",
    val profile: EmergencyProfile = EmergencyProfile(),
    val primaryContact: EmergencyContact? = null,
    val location: LocationSnapshot? = null,
)

/**
 * Assembles the single automatic SMS body sent to every enabled trusted contact.
 *
 * Blocks are ordered by responder priority — who and where first, then why — and each
 * free-text block is bounded so one long note cannot crowd out the location or the call
 * target. A blank or whitespace-only value never produces a stray label.
 */
fun buildEmergencyMessage(content: EmergencyMessageContent): String {
    val name = content.profile.name.cleaned(LABEL_LIMIT)
    val blocks = mutableListOf(
        if (name.isEmpty()) "RESQLINK EMERGENCY" else "RESQLINK EMERGENCY — $name",
        locationBlock(content.location),
    )

    content.primaryContact?.phoneNumber?.cleaned(CONTACT_LIMIT)?.takeIf { it.isNotEmpty() }?.let { number ->
        val contactName = content.primaryContact?.name?.cleaned(LABEL_LIMIT).orEmpty()
        blocks.add(if (contactName.isEmpty()) "Call for assistance: $number" else "Call for assistance: $contactName ($number)")
    }

    blocks.add(normalizeEmergencyMessage(content.configuredMessage).cleaned(FIELD_LIMIT))
    labeled("EMERGENCY NOTES", content.profile.notes)?.let(blocks::add)
    labeled("HOME ADDRESS", content.profile.homeAddress)?.let(blocks::add)
    labeled("IMPORTANT INFORMATION", content.profile.importantInformation)?.let(blocks::add)

    return blocks.joinToString(separator = "\n\n")
}

/** Map link plus plain coordinates, so a responder can still act without link support. */
private fun locationBlock(location: LocationSnapshot?): String {
    if (location == null || !location.isUsable()) return "LOCATION: unavailable at the time of sending."
    val coordinates = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)
    val accuracy = location.accuracyMeters
        .takeIf { it > 0f && it.isFinite() }
        ?.let { String.format(Locale.US, " (±%.0f m)", it) }
        .orEmpty()
    return "LAST KNOWN LOCATION:\n${mapLink(location.latitude, location.longitude)}\nGPS coordinates: $coordinates$accuracy"
}

/** Returns a titled block, or nothing when the value is blank after sanitising. */
private fun labeled(title: String, value: String): String? {
    val cleaned = value.cleaned(FIELD_LIMIT)
    return if (cleaned.isEmpty()) null else "$title:\n$cleaned"
}

/**
 * Neutralises control and format characters that could spoof layout inside an SMS,
 * collapses all whitespace (including newlines a caller may have typed) to single
 * spaces, and bounds the result. Replaced characters become spaces rather than
 * nothing so a newline between two words cannot weld them together.
 */
private fun String.cleaned(limit: Int): String {
    val stripped = replace(Regex("[\\p{Cc}\\p{Cf}]"), " ").replace(Regex("\\s+"), " ").trim()
    return if (stripped.length <= limit) stripped else stripped.take(limit).trimEnd() + "…"
}

/** Rejects a fix a responder could not act on: non-finite, out of range, or a null-island placeholder. */
fun LocationSnapshot.isUsable(): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)

/** Universal link that renders as a tappable map pin in most SMS apps. */
fun mapLink(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", latitude, longitude)

/** Bounds keep one oversized field from crowding out the location or the call target. */
private const val FIELD_LIMIT = 400
private const val LABEL_LIMIT = 120
private const val CONTACT_LIMIT = 64