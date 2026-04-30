package dev.soupbase.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Database(
        UUID id,
        UUID userId,
        String name,
        String pgDatabaseName,
        String pgUsername,
        String pgPasswordHash,
        DatabaseStatus status,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
