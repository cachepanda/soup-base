package dev.soupbase.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record User(
        UUID id,
        String clerkId,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
