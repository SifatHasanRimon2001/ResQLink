package com.resqlink.data.mapper

import com.resqlink.data.local.EmergencyContactEntity
import com.resqlink.data.local.EmergencyEventEntity
import com.resqlink.data.local.EmergencyProfileEntity
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EmergencyProfile
import com.resqlink.domain.model.EventStatus
import com.resqlink.domain.model.LocationStatus

fun EmergencyContactEntity.toDomain() = EmergencyContact(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    email = email,
    priority = priority,
    enabled = enabled,
)

fun EmergencyContact.toEntity(now: Long, createdAt: Long = now) = EmergencyContactEntity(
    id = id,
    name = name.trim(),
    phoneNumber = phoneNumber,
    email = email.trim(),
    priority = priority,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = now,
)

fun EmergencyProfileEntity.toDomain() = EmergencyProfile(
    name = name,
    notes = notes,
    homeAddress = homeAddress,
    importantInformation = importantInformation,
    preferredLanguage = preferredLanguage,
)

fun EmergencyProfile.toEntity(now: Long) = EmergencyProfileEntity(
    name = name.trim(),
    notes = notes.trim(),
    homeAddress = homeAddress.trim(),
    importantInformation = importantInformation.trim(),
    preferredLanguage = preferredLanguage.trim().ifBlank { "English" },
    updatedAt = now,
)

fun EmergencyEventEntity.toDomain(alertAttempts: Int = 0) = EmergencyEvent(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    status = EventStatus.valueOf(status),
    locationStatus = LocationStatus.valueOf(locationStatus),
    batteryLevel = batteryLevel,
    alertAttempts = alertAttempts,
)
