package com.euronet.eft.portal.integration.jenkins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The part of a Jenkins console log worth analysing: its last {@code maxBytes}, plus error-looking lines from
 * earlier in the log (the first compile error is often far above the end).
 *
 * @param tail        last part of the log
 * @param earlyErrors error lines that appear before the tail (at most {@value #MAX_EARLY_ERRORS})
 * @param totalBytes  size of the whole log
 * @param truncated   true when the log was longer than the tail
 */
public record BuildLog(String tail, List<String> earlyErrors, long totalBytes, boolean truncated) {

    static final int MAX_EARLY_ERRORS = 80;
    private static final int MAX_LINE = 2000;
    private static final Pattern ERROR_LINE = Pattern.compile(
        "(?i)(\\[ERROR]|\\berror\\b|exception|\\bfailed\\b|\\bfailure\\b|\\bfatal\\b|caused by)");

    /**
     * Streams a log and keeps only what is needed, so a 100 MB log does not end up in memory.
     */
    public static BuildLog read(InputStream in, int maxBytes) {
        ArrayDeque<String> tail = new ArrayDeque<>();
        List<long[]> errorIndex = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long tailBytes = 0;
        long total = 0;
        long lineNo = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE) {
                    line = line.substring(0, MAX_LINE) + " …";
                }
                long size = line.getBytes(StandardCharsets.UTF_8).length + 1L;
                total += size;
                if (ERROR_LINE.matcher(line).find() && errors.size() < 400) {
                    errors.add(line);
                    errorIndex.add(new long[]{lineNo});
                }
                tail.addLast(line);
                tailBytes += size;
                while (tailBytes > maxBytes && tail.size() > 1) {
                    tailBytes -= tail.removeFirst().getBytes(StandardCharsets.UTF_8).length + 1L;
                }
                lineNo++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        long tailStart = lineNo - tail.size();
        List<String> early = new ArrayList<>();
        for (int i = 0; i < errors.size() && early.size() < MAX_EARLY_ERRORS; i++) {
            if (errorIndex.get(i)[0] < tailStart) {
                early.add(errors.get(i));
            }
        }
        return new BuildLog(String.join("\n", tail), List.copyOf(early), total, tailStart > 0);
    }
}
