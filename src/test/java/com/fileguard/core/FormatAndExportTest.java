package com.fileguard.core;

import com.fileguard.model.LogLevel;
import com.fileguard.service.ActivityLogger;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Small helpers: number/date formatting, CSV escaping and the logger shortcuts. */
class FormatAndExportTest {

    @Test
    void formatsByteSizes() {
        assertEquals("0 B", Format.bytes(0));
        assertEquals("1023 B", Format.bytes(1023));
        assertEquals("1.5 KB", Format.bytes(1536));
        assertEquals("1.0 MB", Format.bytes(1024 * 1024));
        assertEquals("3.0 GB", Format.bytes(3L * 1024 * 1024 * 1024));
    }

    @Test
    void formatsDurationsAndCounts() {
        assertEquals("250 ms", Format.duration(250));
        assertEquals("1.5 s", Format.duration(1500));
        assertEquals("1m 5s", Format.duration(65_000));
        assertEquals("1 file", Format.count(1, "file"));
        assertEquals("3 files", Format.count(3, "file"));
        assertEquals("2 copies", Format.count(2, "copy", "copies"));
        assertEquals("18 Nov 2026, 14:05", Format.dateTime(LocalDateTime.of(2026, 11, 18, 14, 5)));
        assertEquals("-", Format.dateTime(null));
    }

    @Test
    void escapesCsvValuesSafely() {
        assertEquals("plain", ReportExporter.escape("plain"));
        assertEquals("", ReportExporter.escape(null));
        assertEquals("\"a,b\"", ReportExporter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ReportExporter.escape("say \"hi\""));
        assertEquals("\"two\nlines\"", ReportExporter.escape("two\nlines"));
        // A file name starting with '=' must not run as a spreadsheet formula.
        assertEquals("'=SUM(A1)", ReportExporter.escape("=SUM(A1)"));
        assertEquals("'+cmd", ReportExporter.escape("+cmd"));
        assertEquals("'@x", ReportExporter.escape("@x"));
        assertEquals("'-2+3", ReportExporter.escape("-2+3"));
        assertEquals("'\tTAB", ReportExporter.escape("\tTAB"));
    }

    @Test
    void activityLoggerShortcutsUseTheRightLevel() {
        List<String> seen = new ArrayList<>();
        ActivityLogger logger = (level, category, message, details) -> seen.add(level + "/" + category + "/" + message + "/" + details);
        logger.info("APP", "a");
        logger.warn("SCAN", "b");
        logger.error("EXPORT", "c", "d");
        ActivityLogger.NONE.info("X", "ignored");
        assertEquals(List.of("INFO/APP/a/", "WARN/SCAN/b/", "ERROR/EXPORT/c/d"), seen);
        assertEquals(LogLevel.values().length, 3);
    }
}
