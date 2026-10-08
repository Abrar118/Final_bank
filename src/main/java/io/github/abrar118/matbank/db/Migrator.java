package io.github.abrar118.matbank.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies the numbered SQL scripts in {@code db/migration} that the database hasn't seen yet.
 * A deliberately small stand-in for Flyway: one schema, a handful of scripts, no extra dependencies.
 */
public final class Migrator {

    /** Every migration in order. Add new scripts to the end; never edit one that has shipped. */
    static final List<String> MIGRATIONS = List.of(
            "V1__initial_schema.sql");

    private static final String RESOURCE_DIR = "/io/github/abrar118/matbank/db/migration/";

    private final Database database;
    private final Clock clock;

    public Migrator(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /** @return the schema version after migrating */
    public int migrate() {
        database.write(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version    INTEGER PRIMARY KEY,
                            script     TEXT NOT NULL,
                            applied_at TEXT NOT NULL
                        )""");
            }
            int current = currentVersion(c);
            if (current > MIGRATIONS.size()) {
                throw new IllegalStateException("Database schema v" + current
                        + " is newer than this app understands (v" + MIGRATIONS.size() + "). Update MAT Bank.");
            }
            for (int version = current + 1; version <= MIGRATIONS.size(); version++) {
                String script = MIGRATIONS.get(version - 1);
                for (String sql : statements(load(script))) {
                    try (Statement s = c.createStatement()) {
                        s.execute(sql);
                    }
                }
                try (var insert = c.prepareStatement(
                        "INSERT INTO schema_version (version, script, applied_at) VALUES (?, ?, ?)")) {
                    insert.setInt(1, version);
                    insert.setString(2, script);
                    insert.setString(3, Sql.timestamp(clock.instant()));
                    insert.executeUpdate();
                }
            }
        });
        return database.read(Migrator::currentVersion);
    }

    private static int currentVersion(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String load(String script) {
        try (InputStream in = Objects.requireNonNull(
                Migrator.class.getResourceAsStream(RESOURCE_DIR + script), "Missing migration " + script)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read migration " + script, e);
        }
    }

    /** Splits a script on semicolons that end a line, dropping {@code --} comments. */
    static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().strip();
                result.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString().strip());
        }
        return result;
    }
}
