package dev.soupbase.api.dto;

import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.DatabaseStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateDatabaseResponse(
        UUID id,
        String name,
        DatabaseStatus status,
        OffsetDateTime createdAt,
        String connectionString,
        String host,
        Integer port,
        String database,
        String username
) {
    public static CreateDatabaseResponse from(Database db) {
        return new CreateDatabaseResponse(
                db.id(),
                db.name(),
                db.status(),
                db.createdAt(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
