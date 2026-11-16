package com.euronet.eft.portal.inventory.importer;

import com.euronet.eft.portal.common.EnvironmentCodes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Reads the "Jenkins Automation - Tomcat Based Projects" workbook layout:
 * <pre>
 *   row 1: Application Name | Client Name | [Owner] | [Repository] | SIT (merged) | UAT (merged) | PERF ... | [any other column]
 *   row 3:                                                           | URL | Context Name | Tomcat Version | Java Version | Tomcat Admin Creation | Build.xml | ...
 *   row 4+: data
 * </pre>
 * It scales without code changes:
 * <ul>
 *   <li>every sheet is read; the sheet name is the owner unless an "Owner" column is present</li>
 *   <li>any column group whose sub-headers contain "URL" is an environment, so PERF / PREPROD / PROD / DR blocks import too</li>
 *   <li>header positions are discovered, so inserted or reordered columns still import</li>
 *   <li>an optional "Repository" column links GitHub repositories (comma separated) to the application</li>
 * </ul>
 */
@Component
public class InventoryExcelParser {

    private static final int HEADER_SEARCH_ROWS = 10;
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    private static Optional<String> fixedColumn(String header) {
        return switch (header) {
            case "application name", "application", "app name" -> Optional.of("app");
            case "client name", "client", "bank" -> Optional.of("client");
            case "owner", "owner name", "team owner" -> Optional.of("owner");
            case "repository", "repositories", "repo", "github repository", "github repo", "git repository" ->
                Optional.of("repo");
            default -> Optional.empty();
        };
    }

    private static String fieldKey(String header) {
        if (header.isEmpty()) {
            return null;
        }
        if (header.equals("url")) {
            return "url";
        }
        if (header.startsWith("context")) {
            return "context";
        }
        if (header.startsWith("tomcat version")) {
            return "tomcat";
        }
        if (header.startsWith("java")) {
            return "java";
        }
        if (header.contains("admin")) {
            return "admin";
        }
        if (header.replace(".", "").replace(" ", "").startsWith("buildxml")) {
            return "buildxml";
        }
        return null;
    }

    private static String column(Row row, Map<String, Integer> fixed, String key, FormulaEvaluator ev) {
        Integer col = fixed.get(key);
        return col == null ? "" : text(row.getCell(col), ev);
    }

    private static String value(Row row, Map<String, Integer> cols, String key, FormulaEvaluator ev) {
        Integer col = cols.get(key);
        return col == null ? "" : text(row.getCell(col), ev);
    }

    private static boolean containsText(Row row, String wanted, FormulaEvaluator ev) {
        for (Cell cell : row) {
            if (norm(text(cell, ev)).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    static String text(Cell cell, FormulaEvaluator ev) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell, ev).replace(' ', ' ').strip();
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static List<String> splitList(String s) {
        if (s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split("[,;\\n]")).map(String::strip).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private static boolean isDone(String s) {
        String v = norm(s);
        return v.equals("done") || v.equals("yes") || v.equals("y") || v.equals("true");
    }

    private static String trimUrl(String url) {
        String u = url.strip();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public InventoryWorkbook parse(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<ParsedDeployment> rows = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int sheetsRead = 0;
            for (Sheet sheet : workbook) {
                if (parseSheet(sheet, evaluator, rows, warnings)) {
                    sheetsRead++;
                }
            }
            return new InventoryWorkbook(sheetsRead, rows, warnings);
        }
    }

    private boolean parseSheet(Sheet sheet, FormulaEvaluator ev, List<ParsedDeployment> out, List<String> warnings) {
        String sheetName = sheet.getSheetName().strip();

        // 1. header row with "Application Name" and the optional fixed columns
        int headerRow = -1;
        Map<String, Integer> fixed = new HashMap<>();
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS) && headerRow < 0; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                fixedColumn(norm(text(cell, ev))).ifPresent(key -> fixed.putIfAbsent(key, cell.getColumnIndex()));
            }
            if (fixed.containsKey("app")) {
                headerRow = r;
            } else {
                fixed.clear();
            }
        }
        if (headerRow < 0) {
            warnings.add("Sheet '" + sheetName + "' skipped: no 'Application Name' header found");
            return false;
        }

        // 2. the sub-header row ("URL", "Context Name", ...)
        int subRow = -1;
        for (int r = headerRow; r <= Math.min(sheet.getLastRowNum(), headerRow + 4); r++) {
            Row row = sheet.getRow(r);
            if (row != null && containsText(row, "url", ev)) {
                subRow = r;
                break;
            }
        }
        if (subRow < 0) {
            warnings.add("Sheet '" + sheetName + "' skipped: no 'URL' sub-header row found");
            return false;
        }
        Row subHeader = sheet.getRow(subRow);

        // 3. environment groups: any other header cell whose column range has a "URL" sub-header
        TreeMap<Integer, String> candidates = new TreeMap<>();
        for (Cell cell : sheet.getRow(headerRow)) {
            String t = text(cell, ev);
            if (!t.isEmpty() && !fixed.containsValue(cell.getColumnIndex())) {
                candidates.put(cell.getColumnIndex(), t);
            }
        }
        Map<String, Map<String, Integer>> groups = new LinkedHashMap<>();
        for (var c : candidates.entrySet()) {
            Integer next = candidates.higherKey(c.getKey());
            int end = next == null ? subHeader.getLastCellNum() : next;
            Map<String, Integer> fields = new HashMap<>();
            for (int col = c.getKey(); col < end; col++) {
                String key = fieldKey(norm(text(subHeader.getCell(col), ev)));
                if (key != null) {
                    fields.putIfAbsent(key, col);
                }
            }
            if (fields.containsKey("url")) {
                Optional<String> env = EnvironmentCodes.normalize(c.getValue());
                if (env.isPresent()) {
                    groups.put(env.get(), fields);
                } else {
                    warnings.add("Sheet '" + sheetName + "': column group '" + c.getValue() + "' is not a usable environment name");
                }
            }
        }
        if (groups.isEmpty()) {
            warnings.add("Sheet '" + sheetName + "' skipped: no environment column groups (with a URL column) found");
            return false;
        }

        // 4. data rows
        int appCol = fixed.get("app");
        for (int r = subRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String app = text(row.getCell(appCol), ev);
            if (app.isEmpty()) {
                continue;
            }
            String client = column(row, fixed, "client", ev);
            String owner = column(row, fixed, "owner", ev);
            List<String> repos = splitList(column(row, fixed, "repo", ev));
            for (var group : groups.entrySet()) {
                Map<String, Integer> c = group.getValue();
                String url = trimUrl(value(row, c, "url", ev));
                String context = value(row, c, "context", ev);
                if (url.isEmpty() && context.isEmpty()) {
                    continue; // e.g. ACS 2.0 has a UAT block only
                }
                out.add(new ParsedDeployment(
                    owner.isEmpty() ? sheetName : owner, app, client, group.getKey(), context, blankToNull(url),
                    blankToNull(value(row, c, "tomcat", ev)),
                    JavaVersions.normalize(value(row, c, "java", ev)),
                    isDone(value(row, c, "admin", ev)),
                    isDone(value(row, c, "buildxml", ev)),
                    repos));
            }
        }
        return true;
    }
}
