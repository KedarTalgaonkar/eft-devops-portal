package com.euronet.eft.portal.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive values before text leaves the portal for an AI model.
 * <ul>
 *   <li><b>Secrets</b> (passwords, tokens, keys, credentials in URLs, private keys) become {@code [SECRET]} and are never restored.</li>
 *   <li><b>IP addresses, internal host names and e-mail addresses</b> become numbered placeholders ({@code [IP_1]},
 *       {@code [HOST_2]}, {@code [EMAIL_1]}); the same value always gets the same placeholder, so the model can still
 *       reason about "the same host", and {@link Redaction#restore(String)} puts the real values back for display.</li>
 * </ul>
 */
public final class Redactor {

    static final String SECRET = "[SECRET]";

    private static final Pattern PRIVATE_KEY = Pattern.compile(
        "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/\\s:@]+:[^/\\s@]+@");
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)\\b(authorization\\s*[:=]\\s*(?:bearer|basic|token)\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b");
    private static final Pattern KNOWN_TOKENS = Pattern.compile(
        "\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|xox[abprs]-[A-Za-z0-9-]{10,}"
            + "|sk-ant-[A-Za-z0-9_-]{10,}|AKC[a-zA-Z0-9]{10,}|cmVmd[A-Za-z0-9]{20,})\\b");
    /**
     * key=value, key: value, -Dkey=value, "key": "value" for secret-like keys (db.password, client_secret, apiKey, ...).
     */
    private static final Pattern KEY_VALUE = Pattern.compile(
        "(?i)((?<![\\w.-])(?:-D)?[\\w.-]*(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|credentials?)"
            + "[\"']?\\s*[:=]\\s*[\"']?)([^\\s\"',;)}]+)");
    /**
     * Command-line flags: --password value, -token value.
     */
    private static final Pattern CLI_FLAG = Pattern.compile(
        "(?i)((?<![\\w-])--?(?:password|passwd|pwd|secret|token|api-?key)\\s+)([^\\s\"']+)");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern IPV4 = Pattern.compile(
        "(?<![\\d.])(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}(?![\\d.]*\\d)");
    private static final Pattern HOST = Pattern.compile("(?i)\\b[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+\\b");

    private final List<String> maskDomains;
    private final List<String> knownHosts;

    /**
     * @param maskDomains host names ending in one of these are masked (e.g. ".internal")
     * @param knownHosts  host names / IPs that are always masked (the inventory's Tomcat hosts)
     */
    public Redactor(Collection<String> maskDomains, Collection<String> knownHosts) {
        this.maskDomains = maskDomains.stream().filter(d -> d != null && !d.isBlank())
            .map(d -> d.strip().toLowerCase(Locale.ROOT))
            .map(d -> d.startsWith(".") ? d : "." + d)
            .toList();
        this.knownHosts = knownHosts.stream().filter(h -> h != null && h.length() > 2)
            .map(h -> h.strip().toLowerCase(Locale.ROOT))
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed()) // longest first
            .toList();
    }

    private static String secret(Map<String, Integer> counts) {
        counts.merge("secrets", 1, Integer::sum);
        return SECRET;
    }

    private static String placeholder(String kind, String value, Map<String, Masked> placeholders, Map<String, Integer> counts) {
        String key = value.toLowerCase(Locale.ROOT);
        Masked existing = placeholders.get(key);
        if (existing != null) {
            return existing.placeholder();
        }
        long n = placeholders.values().stream().filter(p -> p.placeholder().startsWith("[" + kind + "_")).count() + 1;
        String ph = "[" + kind + "_" + n + "]";
        placeholders.put(key, new Masked(ph, value));
        counts.merge(switch (kind) {
            case "IP" -> "IP addresses";
            case "HOST" -> "host names";
            default -> "e-mail addresses";
        }, 1, Integer::sum);
        return ph;
    }

    private static String replaceAll(Pattern pattern, String text, java.util.function.Function<Matcher, String> replacement) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        boolean found = false;
        while (m.find()) {
            found = true;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.apply(m)));
        }
        if (!found) {
            return text;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Redaction redact(String text) {
        if (text == null || text.isEmpty()) {
            return new Redaction("", Map.of(), Map.of());
        }
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Masked> placeholders = new LinkedHashMap<>(); // lower-case original -> placeholder
        String out = text;

        out = replaceAll(PRIVATE_KEY, out, m -> secret(counts));
        out = replaceAll(URL_CREDENTIALS, out, m -> m.group(1) + secret(counts) + "@");
        out = replaceAll(AUTH_HEADER, out, m -> m.group(1) + secret(counts));
        out = replaceAll(JWT, out, m -> secret(counts));
        out = replaceAll(KNOWN_TOKENS, out, m -> secret(counts));
        out = replaceAll(KEY_VALUE, out, m -> m.group(2).startsWith("[SECRET") ? m.group() : m.group(1) + secret(counts));
        out = replaceAll(CLI_FLAG, out, m -> m.group(2).startsWith("[SECRET") ? m.group() : m.group(1) + secret(counts));
        out = replaceAll(EMAIL, out, m -> placeholder("EMAIL", m.group(), placeholders, counts));
        for (String host : knownHosts) {
            out = replaceAll(Pattern.compile("(?i)(?<![\\w.-])" + Pattern.quote(host) + "(?![\\w-]|\\.\\w)"), out,
                m -> placeholder(IPV4.matcher(host).matches() ? "IP" : "HOST", m.group(), placeholders, counts));
        }
        out = replaceAll(IPV4, out, m -> placeholder("IP", m.group(), placeholders, counts));
        out = replaceAll(HOST, out, m -> isInternal(m.group())
            ? placeholder("HOST", m.group(), placeholders, counts) : m.group());

        Map<String, String> restore = new LinkedHashMap<>();
        placeholders.values().forEach(p -> restore.put(p.placeholder(), p.original()));
        return new Redaction(out, restore, counts);
    }

    private boolean isInternal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return maskDomains.stream().anyMatch(h::endsWith);
    }

    private record Masked(String placeholder, String original) {
    }

    /**
     * Result of masking.
     *
     * @param text    the masked text (what is sent)
     * @param restore placeholder -> original value (never contains secrets)
     * @param counts  what was masked, e.g. {"IP addresses": 2, "secrets": 1}
     */
    public record Redaction(String text, Map<String, String> restore, Map<String, Integer> counts) {

        private static String singular(String k) {
            return switch (k) {
                case "IP addresses" -> "IP address";
                case "host names" -> "host name";
                case "e-mail addresses" -> "e-mail address";
                case "secrets" -> "secret";
                default -> k;
            };
        }

        /**
         * Puts real host names / IPs / e-mails back into text produced by the model, for display in the portal.
         */
        public String restore(String modelOutput) {
            if (modelOutput == null || restore.isEmpty()) {
                return modelOutput;
            }
            String out = modelOutput;
            // longest placeholder first so [IP_12] is not partly replaced by [IP_1]
            List<Map.Entry<String, String>> entries = new ArrayList<>(restore.entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            for (Map.Entry<String, String> e : entries) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        }

        /**
         * Human-readable summary for the audit trail and the UI, e.g. "2 IP addresses, 1 secret masked".
         */
        public String summary() {
            if (counts.isEmpty()) {
                return "nothing to mask";
            }
            List<String> parts = new ArrayList<>();
            counts.forEach((k, v) -> parts.add(v + " " + (v == 1 ? singular(k) : k)));
            return String.join(", ", parts) + " masked";
        }
    }
}
