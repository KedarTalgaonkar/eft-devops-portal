package com.euronet.eft.portal.security.access;

/**
 * Access to one module, ordered: NONE (hidden) &lt; VIEW (read) &lt; EDIT (read and change).
 */
public enum AccessLevel {
    NONE, VIEW, EDIT;

    public boolean atLeast(AccessLevel other) {
        return compareTo(other) >= 0;
    }

    public static AccessLevel max(AccessLevel a, AccessLevel b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static AccessLevel min(AccessLevel a, AccessLevel b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
