package com.fileguard.core;

import com.fileguard.model.ResultItem;
import com.fileguard.model.ScanReport;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a scan report as a CSV file that opens directly in Excel,
 * LibreOffice or any text editor.
 */
public final class ReportExporter {

    public static final String DISCLAIMER =
            "FileGuard reports differences between files. It cannot tell whether a change was intentional or caused by damage.";

    private ReportExporter() {
    }

    public static void exportCsv(ScanReport report, Path target) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            out.write('﻿'); // byte-order mark so Excel detects UTF-8 correctly

            // Short header block describing the scan.
            writeRow(out, "FileGuard report");
            writeRow(out, "Scan type", report.type().label());
            writeRow(out, "Folder", report.folder());
            if (report.secondFolder() != null) {
                writeRow(out, "Backup folder", report.secondFolder());
            }
            writeRow(out, "Started", Format.dateTime(report.startedAt()));
            writeRow(out, "Status", report.status().label());
            writeRow(out, "Files examined", String.valueOf(report.filesScanned()));
            writeRow(out, "Summary", report.summary());
            writeRow(out, "Note", DISCLAIMER);
            out.newLine();

            // Detailed results.
            writeRow(out, "Status", "Relative path", "Details", "Size (bytes)", "Duplicate group");
            for (ResultItem item : report.items()) {
                writeRow(out, item.status().label(), item.path(), item.detail(),
                        String.valueOf(item.size()), item.group() > 0 ? String.valueOf(item.group()) : "");
            }
        }
    }

    private static void writeRow(BufferedWriter out, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(values[i]));
        }
        out.newLine();
    }

    /**
     * Quotes a value if it contains a comma, quote or line break (RFC 4180).
     * Values starting with '=', '+' or '@' get a leading apostrophe so that
     * spreadsheet programs do not run a file name as a formula.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
