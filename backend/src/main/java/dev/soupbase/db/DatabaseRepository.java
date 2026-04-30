package dev.soupbase.db;

import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.DatabaseStatus;
import dev.soupbase.db.generated.tables.records.DatabasesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.UUID;

import static dev.soupbase.db.generated.Tables.DATABASES;
import static dev.soupbase.db.generated.enums.DatabaseStatus.PROVISIONING;

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
                .and(DATABASES.STATUS.ne(dev.soupbase.db.generated.enums.DatabaseStatus.DELETED))
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
        DatabasesRecord record = Objects.requireNonNull(
                dsl.insertInto(DATABASES)
                        .columns(DATABASES.ID, DATABASES.USER_ID, DATABASES.NAME,
                                DATABASES.PG_DATABASE_NAME, DATABASES.PG_USERNAME,
                                DATABASES.PG_PASSWORD_HASH, DATABASES.STATUS)
                        .values(id, userId, name, pgDatabaseName, pgUsername, pgPasswordHash, PROVISIONING)
                        .returning()
                        .fetchOne(),
                "Insert did not return a record for database id: " + id
        );
        return toDatabase(record);
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
