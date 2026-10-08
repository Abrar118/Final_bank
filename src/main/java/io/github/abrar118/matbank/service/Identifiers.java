package io.github.abrar118.matbank.service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** Generates transaction references and account numbers. */
public final class Identifiers {

    /** Crockford-style alphabet: no I, L, O or U, so references read back over the phone unambiguously. */
    private static final String REFERENCE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private final RandomGenerator random;

    public Identifiers() {
        this(new SecureRandom());
    }

    public Identifiers(RandomGenerator random) {
        this.random = random;
    }

    /** E.g. {@code MAT-7KQ2-9XFD}. */
    public String reference() {
        StringBuilder sb = new StringBuilder("MAT-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Twelve digits starting with 2022, the year MAT Bank was first built, e.g. {@code 2022 4821 0937}. */
    public String accountNumber() {
        return String.format("2022 %04d %04d", random.nextInt(10_000), random.nextInt(10_000));
    }
}
