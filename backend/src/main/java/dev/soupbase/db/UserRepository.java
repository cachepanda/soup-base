package dev.soupbase.db;

import dev.soupbase.domain.model.User;
import dev.soupbase.db.generated.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

import static dev.soupbase.db.generated.Tables.USERS;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<User> findByClerkId(String clerkId) {
        UsersRecord record = dsl.selectFrom(USERS)
                .where(USERS.CLERK_ID.eq(clerkId))
                .fetchOne();
        return Optional.ofNullable(record).map(UserRepository::toUser);
    }

    public User findOrCreate(String clerkId, String email) {
        dsl.insertInto(USERS)
                .columns(USERS.CLERK_ID, USERS.EMAIL)
                .values(clerkId, email)
                .onConflictDoNothing()
                .execute();

        return toUser(Objects.requireNonNull(
                dsl.selectFrom(USERS)
                        .where(USERS.CLERK_ID.eq(clerkId))
                        .fetchOne(),
                "User not found after insert: " + clerkId
        ));
    }

    private static User toUser(UsersRecord r) {
        return new User(r.getId(), r.getClerkId(), r.getEmail(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
