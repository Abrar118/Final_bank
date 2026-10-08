package io.github.abrar118.matbank.db;

/** Unchecked wrapper for {@link java.sql.SQLException}, so service code doesn't drown in try/catch. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
