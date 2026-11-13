package com.euronet.eft.portal.inventory.importer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalises the Java versions typed into the inventory sheet into one notation.
 * <ul>
 *   <li>{@code 1.8.0_351} becomes {@code 8u351}</li>
 *   <li>{@code 8.351} (also stored by Excel as the number 8.3510000000000009) becomes {@code 8u351}</li>
 *   <li>{@code 8.31} becomes {@code 8u310}: Java 8 updates since 8u101 have three digits, and Excel drops trailing zeros</li>
 *   <li>anything else ({@code 17.0.12}, {@code 21}) is kept as typed</li>
 * </ul>
 */
public final class JavaVersions {

    private static final Pattern LEGACY = Pattern.compile("^1\\.(\\d+)\\.\\d+_(\\d+)$");
    private static final Pattern MAJOR_DOT_UPDATE = Pattern.compile("^([5-8])\\.(\\d{1,3})\\d*$");

    private JavaVersions() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip();
        if (v.isEmpty()) {
            return null;
        }
        Matcher legacy = LEGACY.matcher(v);
        if (legacy.matches()) {
            return legacy.group(1) + "u" + Integer.parseInt(legacy.group(2));
        }
        Matcher shortForm = MAJOR_DOT_UPDATE.matcher(v);
        if (shortForm.matches()) {
            String update = (shortForm.group(2) + "00").substring(0, 3);
            return shortForm.group(1) + "u" + Integer.parseInt(update);
        }
        return v;
    }
}
