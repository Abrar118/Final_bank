package io.github.abrar118.matbank.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;

/** Salted bcrypt hashes for passwords and admin PINs. The 2022 version stored both in plain text files. */
public final class PasswordHasher {

    /** Cost 12 takes roughly a quarter of a second per hash on a laptop: slow for attackers, fine for a login. */
    public static final int DEFAULT_COST = 12;

    private final int cost;
    private final String dummyHash;

    public PasswordHasher(int cost) {
        this.cost = cost;
        this.dummyHash = hash("not-a-real-password");
    }

    public String hash(String secret) {
        return BCrypt.withDefaults().hashToString(cost, secret.toCharArray());
    }

    public boolean verify(String secret, String hash) {
        if (secret == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.verifyer().verify(secret.getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8)).verified;
        } catch (IllegalArgumentException e) {
            // Over bcrypt's 72-byte limit or a malformed hash: either way not a match.
            return false;
        }
    }

    /**
     * Burns the same time as a real verification. Called for unknown emails so response time doesn't reveal
     * which addresses have accounts.
     */
    public void verifyDummy(String secret) {
        verify(secret == null ? "" : secret, dummyHash);
    }
}
