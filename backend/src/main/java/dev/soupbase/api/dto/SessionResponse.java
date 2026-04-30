package dev.soupbase.api.dto;

import dev.soupbase.domain.model.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String clerkId,
        String email,
        OffsetDateTime createdAt
) {
    public static SessionResponse from(User user) {
        return new SessionResponse(user.id(), user.clerkId(), user.email(), user.createdAt());
    }
}
