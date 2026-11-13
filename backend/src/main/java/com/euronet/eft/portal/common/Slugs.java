package com.euronet.eft.portal.common;

import java.util.Locale;

public final class Slugs {

    private Slugs() {
    }

    /** "Euronet Prepaid (PPI)" becomes "euronet-prepaid-ppi". */
    public static String slug(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
    }
}
