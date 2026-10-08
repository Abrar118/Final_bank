package io.github.abrar118.matbank.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small JDBC helpers shared by the repositories. */
public final class Sql {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Fixed-width UTC timestamps, so text comparison in SQL orders them correctly.
     * {@code Instant.toString()} drops zero fractions ("...:05Z" vs "...:05.120Z"), which would sort wrongly.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private Sql() {
    }

    public static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant);
    }

    public static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        bind(ps, params);
        return ps;
    }

    public static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int index = i + 1;
            switch (p) {
                case null -> ps.setNull(index, Types.NULL);
                case Instant instant -> ps.setString(index, timestamp(instant));
                case LocalDate date -> ps.setString(index, date.toString());
                case Enum<?> e -> ps.setString(index, e.name());
                case Boolean b -> ps.setInt(index, b ? 1 : 0);
                case byte[] bytes -> ps.setBytes(index, bytes);
                default -> ps.setObject(index, p);
            }
        }
    }

    public static <T> List<T> list(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapper.map(rs));
            }
            return result;
        }
    }

    public static <T> Optional<T> one(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
        }
    }

    public static long scalarLong(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        }
    }

    /** Executes an INSERT and returns the generated row id. */
    public static long insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No id generated for: " + sql);
                }
                return keys.getLong(1);
            }
        }
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Instant.parse(value);
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }

    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** Escapes {@code %}, {@code _} and {@code \} for use in a {@code LIKE ... ESCAPE '\'} pattern. */
    public static String likeContains(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
