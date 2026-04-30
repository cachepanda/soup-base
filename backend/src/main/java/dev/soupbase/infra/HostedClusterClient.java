package dev.soupbase.infra;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class HostedClusterClient implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HostedClusterClient.class);

    // Only lowercase alphanumeric + underscores, must start with a letter.
    // Matches the sb_{hash}_{id} convention from ADR-001.
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;

    public HostedClusterClient(
            @Value("${hosted-cluster.datasource.url}") String url,
            @Value("${hosted-cluster.datasource.username}") String username,
            @Value("${hosted-cluster.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setPoolName("hosted-cluster-admin");
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(2);
        ds.setConnectionTimeout(5_000);
        ds.setIdleTimeout(300_000);
        ds.setMaxLifetime(600_000);
        // Do not block startup if the hosted cluster is unreachable at boot time.
        // DDL operations are low-frequency and will fail at call time, not at startup.
        ds.setInitializationFailTimeout(-1);
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
    }

    /**
     * Creates a new database on the hosted cluster owned by the given role.
     * The owning role automatically receives all privileges on the new database.
     */
    public void createDatabase(String dbName, String ownerRole) {
        log.info("Creating hosted database '{}' with owner '{}'", dbName, ownerRole);
        jdbc.execute("CREATE DATABASE " + quoted(dbName) + " OWNER " + quoted(ownerRole));
    }

    /**
     * Creates a login role on the hosted cluster and sets its password.
     * Password is set via ALTER ROLE so it does not appear in CREATE ROLE DDL.
     */
    public void createRole(String roleName, String password) {
        log.info("Creating hosted role '{}'", roleName);
        jdbc.execute("CREATE ROLE " + quoted(roleName) + " WITH LOGIN");
        jdbc.execute("ALTER ROLE " + quoted(roleName) + " WITH PASSWORD '" + escapeLiteral(password) + "'");
    }

    /**
     * Grants CONNECT on the database to the role and revokes it from PUBLIC,
     * ensuring only the dedicated role (and superusers) can connect.
     */
    public void grantPrivileges(String dbName, String roleName) {
        log.info("Granting privileges on '{}' to '{}'", dbName, roleName);
        jdbc.execute("GRANT CONNECT ON DATABASE " + quoted(dbName) + " TO " + quoted(roleName));
        jdbc.execute("REVOKE CONNECT ON DATABASE " + quoted(dbName) + " FROM PUBLIC");
    }

    /**
     * Terminates active connections to the database and then drops it.
     */
    public void dropDatabase(String dbName) {
        log.info("Dropping hosted database '{}'", dbName);
        jdbc.execute(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
            "WHERE datname = '" + escapeLiteral(dbName) + "' AND pid <> pg_backend_pid()");
        jdbc.execute("DROP DATABASE IF EXISTS " + quoted(dbName));
    }

    /**
     * Drops the role if it exists.
     */
    public void dropRole(String roleName) {
        log.info("Dropping hosted role '{}'", roleName);
        jdbc.execute("DROP ROLE IF EXISTS " + quoted(roleName));
    }

    @Override
    public void destroy() {
        dataSource.close();
    }

    private static String quoted(String identifier) {
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Unsafe identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private static String escapeLiteral(String value) {
        return value.replace("'", "''");
    }
}
