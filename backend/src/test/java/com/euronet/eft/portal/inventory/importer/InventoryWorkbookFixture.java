package com.euronet.eft.portal.inventory.importer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Builds a workbook with the same layout as "Jenkins Automation - Tomcat Based Projects.xlsx"
 * (merged SIT/UAT group headers in rows 1-2, field headers in row 3, data from row 4, column A empty),
 * with fake hosts.
 */
public final class InventoryWorkbookFixture {

    private static final String[] FIELDS = {"URL", "Context Name", "Tomcat Version", "Java Version", "Tomcat Admin Creation", "Build.xml"};

    private InventoryWorkbookFixture() {
    }

    /**
     * A workbook that grows beyond the original layout: an explicit Owner column, a Repository column,
     * two new environments ("PERF" and "Pre-Prod") and an unrelated trailing "Remarks" column.
     */
    public static byte[] scalable(String owner, String preProdUrl) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Payments team");
            Row top = s.createRow(0);
            top.createCell(1).setCellValue("Application Name");
            top.createCell(2).setCellValue("Client Name");
            top.createCell(3).setCellValue("Owner");
            top.createCell(4).setCellValue("Repository");
            top.createCell(5).setCellValue("PERF");
            top.createCell(11).setCellValue("Pre-Prod");
            top.createCell(17).setCellValue("Remarks");
            s.createRow(1);
            s.addMergedRegion(new CellRangeAddress(0, 1, 5, 10));
            s.addMergedRegion(new CellRangeAddress(0, 1, 11, 16));
            Row fields = s.createRow(2);
            for (int i = 0; i < FIELDS.length; i++) {
                fields.createCell(5 + i).setCellValue(FIELDS[i]);
                fields.createCell(11 + i).setCellValue(FIELDS[i]);
            }
            Row r = s.createRow(3);
            r.createCell(1).setCellValue("Recon Engine");
            r.createCell(2).setCellValue("Euronet");
            r.createCell(3).setCellValue(owner);
            r.createCell(4).setCellValue("eft-org/recon-engine, eft-org/recon-common");
            block(r, 5, "http://perf-recon.example.internal:8080", "recon", "10.1.30", "21.0.5", "Done", "Done");
            block(r, 11, preProdUrl, "recon", "10.1.30", "21.0.5", "Done", "Pending");
            r.createCell(17).setCellValue("migrated to Java 21");
            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] twoOwners() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet samar = header(wb, "Samar");
            // QR ENGINE: Java stored as a number, the way Excel keeps 8.145
            Row r = samar.createRow(3);
            r.createCell(1).setCellValue("QR ENGINE");
            r.createCell(2).setCellValue("CBC");
            block(r, 3, "http://sit-qr.example.internal:9091", "SITCBCQRAPI", "8.032", null, "Done", "Done");
            r.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(8.1449999999999996);
            block(r, 9, "http://uat-qr.example.internal:9085/", "UATCBCQRAPI", "8.032", "1.8.0_145", "Done", " Done");

            Sheet narsing = header(wb, "Narsing");
            Row a = narsing.createRow(3);
            a.createCell(1).setCellValue("IMPS Portal");
            a.createCell(2).setCellValue("COSMOS BANK ");
            block(a, 3, " http://sit-imps.example.internal:9045", "imps_portal", " 9.0.67", " 8.311", "Done", "Done");
            block(a, 9, " http://uat-imps.example.internal:9045", "imps_portal", " 9.0.67", " 8.311", " Done", "Pending");
            // ACS 2.0 has a UAT block only
            Row b = narsing.createRow(4);
            b.createCell(1).setCellValue("ACS 2.0");
            b.createCell(2).setCellValue("COSMOS");
            block(b, 9, "https://uat-acs.example.internal:8443", "IMS2COSMOSINC", "9.0.5", "1.8.0_321", " Done", " Done");
            // blank spacer row and a row without application name are ignored
            narsing.createRow(6).createCell(3).setCellValue("stray note");

            Sheet notes = wb.createSheet("Notes");
            notes.createRow(0).createCell(0).setCellValue("free text, no inventory here");

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static Sheet header(XSSFWorkbook wb, String owner) {
        Sheet s = wb.createSheet(owner);
        Row top = s.createRow(0);
        top.createCell(1).setCellValue("Application Name");
        top.createCell(2).setCellValue("Client Name");
        top.createCell(3).setCellValue("SIT");
        top.createCell(9).setCellValue("UAT");
        s.createRow(1);
        s.addMergedRegion(new CellRangeAddress(0, 2, 1, 1));
        s.addMergedRegion(new CellRangeAddress(0, 2, 2, 2));
        s.addMergedRegion(new CellRangeAddress(0, 1, 3, 8));
        s.addMergedRegion(new CellRangeAddress(0, 1, 9, 14));
        Row fields = s.createRow(2);
        for (int i = 0; i < FIELDS.length; i++) {
            fields.createCell(3 + i).setCellValue(FIELDS[i]);
            fields.createCell(9 + i).setCellValue(FIELDS[i]);
        }
        return s;
    }

    private static void block(Row row, int start, String url, String context, String tomcat, String java, String admin, String buildXml) {
        String[] values = {url, context, tomcat, java, admin, buildXml};
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                row.createCell(start + i).setCellValue(values[i]);
            }
        }
    }
}
