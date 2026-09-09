package com.malapay.classroom.util;

import java.security.SecureRandom;

/**
 * Generates class codes like "A3F9K2" — 6 characters, uppercase letters and
 * digits. Ambiguous-looking characters (0/O, 1/I) are excluded so codes are
 * easy for students to read aloud and type correctly.
 */
public final class ClassCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ClassCodeGenerator() {
        // no instances
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
