package dev.soupbase.domain;

import dev.soupbase.db.DatabaseRepository;
import dev.soupbase.domain.model.Database;
import dev.soupbase.domain.model.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DatabaseService {

    private static final int MAX_DATABASES = 3;
    private static final int MAX_NAME_LENGTH = 40;
    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*$");

    private static final String[] ADJECTIVES = {
        "amber", "brave", "calm", "dark", "eager",
        "fast", "gold", "jade", "keen", "lime",
        "mint", "navy", "pale", "rose", "ruby",
        "sage", "sky", "teal", "warm", "wild"
    };

    private static final String[] NOUNS = {
        "atlas", "bloom", "cedar", "coral", "dusk",
        "ember", "fern", "grove", "haven", "iris",
        "lark", "mesa", "nile", "opal", "pine",
        "quill", "reed", "tide", "vale", "wren"
    };

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final DatabaseRepository databaseRepository;
    private final ProvisioningService provisioningService;

    public DatabaseService(DatabaseRepository databaseRepository, ProvisioningService provisioningService) {
        this.databaseRepository = databaseRepository;
        this.provisioningService = provisioningService;
    }

    public Result<Database> createDatabase(User user, String requestedName) {
        int count = databaseRepository.countNonDeletedByUserId(user.id());
        if (count >= MAX_DATABASES) {
            return new Result.Err<>("DATABASE_LIMIT_REACHED", "Maximum of 3 databases allowed per user");
        }

        String name;
        if (requestedName == null || requestedName.isBlank()) {
            name = generateName();
        } else {
            name = requestedName.strip();
            if (name.length() > MAX_NAME_LENGTH) {
                return new Result.Err<>("INVALID_NAME", "Name must be at most 40 characters");
            }
            if (!VALID_NAME.matcher(name).matches()) {
                return new Result.Err<>("INVALID_NAME", "Name must contain only alphanumeric characters and hyphens");
            }
        }

        if (databaseRepository.existsByUserIdAndName(user.id(), name)) {
            return new Result.Err<>("DUPLICATE_NAME", "A database with this name already exists");
        }

        UUID dbId = UUID.randomUUID();
        String userHash = computeUserHash(user.clerkId());
        String shortId = dbId.toString().replace("-", "").substring(0, 8);
        String pgDatabaseName = "sb_" + userHash + "_" + shortId;
        String pgUsername = "sb_u_" + userHash + "_" + shortId;

        String password = generatePassword();
        String pgPasswordHash = PASSWORD_ENCODER.encode(password);

        Database database = databaseRepository.insert(
                dbId, user.id(), name, pgDatabaseName, pgUsername, pgPasswordHash);

        provisioningService.provision(database, password);

        return new Result.Ok<>(database);
    }

    private static String generateName() {
        String adj = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        return "db-" + adj + "-" + noun;
    }

    private static String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String computeUserHash(String clerkId) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(clerkId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
