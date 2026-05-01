package dev.soupbase.db;

import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.DatabaseStatus;
import dev.soupbase.db.generated.tables.records.DatabasesRecord;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.soupbase.db.generated.Tables.DATABASES;

@Repository
public class DatabaseRepository {

    private final DSLContext dsl;

    public DatabaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int countNonDeletedByUserId(UUID userId) {
        return dsl.selectCount()
                .from(DATABASES)
                .where(DATABASES.USER_ID.eq(userId))
                .and(DATABASES.STATUS.cast(String.class).ne("DELETED"))
                .fetchOne(0, int.class);
    }

    public boolean existsByUserIdAndName(UUID userId, String name) {
        return dsl.fetchExists(
                dsl.selectFrom(DATABASES)
                        .where(DATABASES.USER_ID.eq(userId))
                        .and(DATABASES.NAME.eq(name))
        );
    }

    public Database insert(UUID id, UUID userId, String name, String pgDatabaseName,
                           String pgUsername, String pgPasswordHash) {
        // Omit STATUS – the DB default 'PROVISIONING' avoids jOOQ H2→PostgreSQL enum binding issues.
        dsl.insertInto(DATABASES)
                .columns(DATABASES.ID, DATABASES.USER_ID, DATABASES.NAME,
                        DATABASES.PG_DATABASE_NAME, DATABASES.PG_USERNAME,
                        DATABASES.PG_PASSWORD_HASH)
                .values(id, userId, name, pgDatabaseName, pgUsername, pgPasswordHash)
                .execute();

        // Fetch only the DB-generated timestamp columns to avoid jOOQ enum binding
        // issues with PostgreSQL custom types (database_status) in DDL-generated code.
        Record2<OffsetDateTime, OffsetDateTime> timestamps = Objects.requireNonNull(
                dsl.select(DATABASES.CREATED_AT, DATABASES.UPDATED_AT)
                        .from(DATABASES)
                        .where(DATABASES.ID.eq(id))
                        .fetchOne(),
                "Database record not found after insert: " + id
        );

        return new Database(id, userId, name, pgDatabaseName, pgUsername, pgPasswordHash,
                DatabaseStatus.PROVISIONING, null,
                timestamps.get(DATABASES.CREATED_AT),
                timestamps.get(DATABASES.UPDATED_AT));
    }

    public void updateStatus(UUID id, DatabaseStatus status, String failureReason) {
        // Use raw SQL cast – avoids jOOQ H2-generated enum binding issues with PostgreSQL custom types.
        dsl.execute(
                "UPDATE databases SET status = ?::database_status, failure_reason = ?, updated_at = NOW() WHERE id = ?",
                status.name(), failureReason, id
        );
    }

    public List<Database> findStuckInProvisioning(OffsetDateTime cutoff) {
        return dsl.selectFrom(DATABASES)
                .where(DATABASES.STATUS.cast(String.class).eq("PROVISIONING"))
                .and(DATABASES.CREATED_AT.lt(cutoff))
                .fetch(DatabaseRepository::toDatabase);
    }

    private static Database toDatabase(DatabasesRecord r) {
        return new Database(
                r.getId(),
                r.getUserId(),
                r.getName(),
                r.getPgDatabaseName(),
                r.getPgUsername(),
                r.getPgPasswordHash(),
                DatabaseStatus.valueOf(r.getStatus().getLiteral()),
                r.getFailureReason(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
