-- MAT Bank schema v1.
-- Timestamps are ISO-8601 instants in UTC, dates are ISO local dates, money is integer poisha.

CREATE TABLE users (
    id            INTEGER PRIMARY KEY,
    email         TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT    NOT NULL,
    pin_hash      TEXT,
    role          TEXT    NOT NULL CHECK (role IN ('CLIENT', 'ADMIN')),
    full_name     TEXT    NOT NULL,
    gender        TEXT    NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    birth_date    TEXT,
    phone         TEXT,
    facebook      TEXT,
    address       TEXT,
    avatar        BLOB,
    failed_logins INTEGER NOT NULL DEFAULT 0,
    locked_until  TEXT,
    created_at    TEXT    NOT NULL,
    CHECK (role <> 'ADMIN' OR pin_hash IS NOT NULL)
);

CREATE TABLE accounts (
    id            INTEGER PRIMARY KEY,
    user_id       INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type          TEXT    NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS')),
    number        TEXT    NOT NULL UNIQUE,
    balance_cents INTEGER NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
    created_at    TEXT    NOT NULL,
    UNIQUE (user_id, type)
);

CREATE TABLE ledger_entries (
    id                  INTEGER PRIMARY KEY,
    account_id          INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    kind                TEXT    NOT NULL CHECK (kind IN ('OPENING_BALANCE', 'DEPOSIT', 'TRANSFER_IN', 'TRANSFER_OUT',
                                                         'INTERNAL_IN', 'INTERNAL_OUT', 'FEE')),
    amount_cents        INTEGER NOT NULL,
    balance_after_cents INTEGER NOT NULL CHECK (balance_after_cents >= 0),
    counterparty        TEXT,
    description         TEXT    NOT NULL,
    note                TEXT,
    reference           TEXT    NOT NULL,
    created_at          TEXT    NOT NULL
);

CREATE INDEX idx_ledger_account_time ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_reference ON ledger_entries (reference);

CREATE TABLE support_messages (
    id           INTEGER PRIMARY KEY,
    sender_id    INTEGER REFERENCES users (id) ON DELETE SET NULL,
    sender_name  TEXT    NOT NULL,
    sender_email TEXT,
    body         TEXT    NOT NULL,
    is_read      INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL
);

CREATE TABLE feedback (
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER REFERENCES users (id) ON DELETE SET NULL,
    user_name  TEXT    NOT NULL,
    rating     INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body       TEXT    NOT NULL,
    created_at TEXT    NOT NULL
);

CREATE TABLE login_events (
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER REFERENCES users (id) ON DELETE SET NULL,
    email      TEXT    NOT NULL,
    user_name  TEXT,
    outcome    TEXT    NOT NULL CHECK (outcome IN ('SUCCESS', 'WRONG_CREDENTIALS', 'UNKNOWN_USER', 'LOCKED')),
    created_at TEXT    NOT NULL
);

CREATE INDEX idx_login_events_time ON login_events (created_at);

CREATE TABLE audit_log (
    id          INTEGER PRIMARY KEY,
    actor_id    INTEGER REFERENCES users (id) ON DELETE SET NULL,
    actor_email TEXT,
    action      TEXT    NOT NULL,
    details     TEXT,
    created_at  TEXT    NOT NULL
);

CREATE INDEX idx_audit_time ON audit_log (created_at);

CREATE TABLE recurring_payments (
    id              INTEGER PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_account    TEXT    NOT NULL CHECK (from_account IN ('CHECKING', 'SAVINGS')),
    recipient_email TEXT    NOT NULL COLLATE NOCASE,
    amount_cents    INTEGER NOT NULL CHECK (amount_cents > 0),
    note            TEXT,
    frequency       TEXT    NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    start_date      TEXT    NOT NULL,
    runs_completed  INTEGER NOT NULL DEFAULT 0,
    next_run        TEXT    NOT NULL,
    active          INTEGER NOT NULL DEFAULT 1,
    last_result     TEXT,
    created_at      TEXT    NOT NULL
);

CREATE INDEX idx_recurring_due ON recurring_payments (active, next_run);
