package com.euronet.eft.portal.security.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Password rules for local accounts: at least 12 characters, at most 128, at least three of
 * lower case / upper case / digit / symbol, not containing the username, no long single-character runs.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** Problems with the password; empty when it is acceptable. */
    public static List<String> violations(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("at least " + MIN_LENGTH + " characters");
            return problems;
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("at most " + MAX_LENGTH + " characters");
        }
        int classes = 0;
        classes += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        classes += password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0;
        if (classes < 3) {
            problems.add("at least three of: lower case, upper case, digit, symbol");
        }
        if (username != null && username.length() >= 3
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("must not contain the username");
        }
        if (password.matches(".*(.)\\1{3,}.*")) {
            problems.add("no character repeated 4 or more times in a row");
        }
        return problems;
    }

    public static void require(String password, String username) {
        List<String> problems = violations(password, username);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Password does not meet the policy: " + String.join("; ", problems));
        }
    }
}
