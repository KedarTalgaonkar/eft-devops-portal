package com.euronet.eft.portal.security;

import java.util.regex.Pattern;

/**
 * Open-redirect protection: only same-origin relative paths are accepted as a post-login destination.
 */
public final class SafeRedirects {

    private static final Pattern SAFE = Pattern.compile("^/(?![/\\\\])[A-Za-z0-9\\-._~/?#\\[\\]@!$&'()*+,;=%]*$");

    private SafeRedirects() {
    }

    /**
     * Returns {@code path} when it is a plain relative path like "/pipelines/all?q=x", otherwise "/".
     */
    public static String relativeOrRoot(String path) {
        if (path == null || path.isBlank() || path.length() > 500) {
            return "/";
        }
        String p = path.strip();
        if (!SAFE.matcher(p).matches() || p.contains("://") || p.toLowerCase().contains("javascript:")
            || p.startsWith("/login") || p.startsWith("/api/") || p.startsWith("/oauth2/")) {
            return "/";
        }
        return p;
    }
}
