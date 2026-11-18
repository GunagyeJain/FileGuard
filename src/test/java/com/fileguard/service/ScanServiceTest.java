package com.fileguard.service;

import com.fileguard.core.ProgressListener;
import com.fileguard.core.ReportExporter;
import com.fileguard.core.TestListeners;
import com.fileguard.db.Database;
import com.fileguard.model.ResultStatus;
import com.fileguard.model.ScanReport;
import com.fileguard.model.ScanStatus;
import com.fileguard.model.ScanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests: real files, real SQLite database. */
class ScanServiceTest {

    @TempDir
    Path tmp;
    private Path dbFile;
    private Path folder;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tmp.resolve("db/test.db");
        folder = Files.createDirectories(tmp.resolve("watched"));
        Files.writeString(folder.resolve("keep.txt"), "unchanged");
        Files.writeString(folder.resolve("edit.txt"), "version 1");
        Files.writeString(folder.resolve("remove.txt"), "bye");
    }

    private ScanService openService() throws Exception {
        Database db = new Database(dbFile);
        db.initialize();
        return new ScanService(db);
    }

    @Test
    void integrityCheckDetectsChangesAfterBaseline() throws Exception {
        ScanService service = openService();
        ScanReport baseline = service.createBaseline(folder, ProgressListener.NONE);
        assertEquals(3, baseline.filesScanned());

        Files.writeString(folder.resolve("edit.txt"), "version 2");
        Files.delete(folder.resolve("remove.txt"));
        Files.writeString(folder.resolve("added.txt"), "new");

        ScanReport check = service.checkIntegrity(folder, ProgressListener.NONE);
        assertEquals(1, check.countOf(ResultStatus.ADDED));
        assertEquals(1, check.countOf(ResultStatus.MODIFIED));
        assertEquals(1, check.countOf(ResultStatus.DELETED));
        assertEquals(3, check.issuesFound());
    }

    @Test
    void integrityCheckWithoutBaselineIsRejected() throws Exception {
        ScanService service = openService();
        assertThrows(IllegalStateException.class, () -> service.checkIntegrity(folder, ProgressListener.NONE));
    }

    @Test
    void dataSurvivesReopeningTheDatabase() throws Exception {
        // First "session"
        ScanService first = openService();
        first.createBaseline(folder, ProgressListener.NONE);
        Files.writeString(folder.resolve("edit.txt"), "changed");
        long checkId = first.checkIntegrity(folder, ProgressListener.NONE).id();

        // Second "session": brand new Database and ScanService objects on the same file,
        // exactly what happens when the application is restarted.
        ScanService second = openService();
        assertTrue(second.findBaseline(folder).isPresent());
        assertEquals(1, second.baselineCount());
        assertEquals(2, second.completedScanCount());
        List<ScanReport> history = second.recentScans(10);
        assertEquals(2, history.size());
        ScanReport reloaded = second.loadScan(checkId).orElseThrow();
        assertEquals(ScanType.INTEGRITY, reloaded.type());
        assertEquals(1, reloaded.countOf(ResultStatus.MODIFIED));
        assertEquals(1, reloaded.issuesFound());

        // The baseline still works after the restart.
        ScanReport again = second.checkIntegrity(folder, ProgressListener.NONE);
        assertEquals(1, again.countOf(ResultStatus.MODIFIED));
    }

    @Test
    void newBaselineReplacesOldOne() throws Exception {
        ScanService service = openService();
        service.createBaseline(folder, ProgressListener.NONE);
        Files.writeString(folder.resolve("edit.txt"), "changed");
        service.createBaseline(folder, ProgressListener.NONE);

        assertEquals(1, service.baselineCount());
        assertEquals(0, service.checkIntegrity(folder, ProgressListener.NONE).issuesFound());
    }

    @Test
    void cancelledScanIsRecordedAndSavesNoBaseline() throws Exception {
        ScanService service = openService();
        Files.write(folder.resolve("big.bin"), new byte[1024 * 1024]);

        assertThrows(CancellationException.class, () -> service.createBaseline(folder, TestListeners.cancelAfter(3)));

        assertTrue(service.findBaseline(folder).isEmpty(), "a cancelled baseline must not be saved");
        ScanReport last = service.recentScans(1).get(0);
        assertEquals(ScanStatus.CANCELLED, last.status());
        assertEquals(0, service.completedScanCount());
    }

    @Test
    void duplicatesAndBackupAreStored() throws Exception {
        ScanService service = openService();
        Files.writeString(folder.resolve("keep-copy.txt"), "unchanged");
        ScanReport dup = service.findDuplicates(folder, ProgressListener.NONE);
        assertEquals(2, dup.countOf(ResultStatus.DUPLICATE));
        assertEquals("unchanged".length(), dup.redundantBytes());

        Path backup = Files.createDirectories(tmp.resolve("backup"));
        Files.writeString(backup.resolve("keep.txt"), "unchanged");
        ScanReport bk = service.verifyBackup(folder, backup, ProgressListener.NONE);
        assertEquals(3, bk.countOf(ResultStatus.MISSING));

        assertEquals(dup.redundantBytes(),
                service.latestCompleted(ScanType.DUPLICATES).orElseThrow().redundantBytes());
    }

    @Test
    void exportsCsvReport() throws Exception {
        ScanService service = openService();
        service.createBaseline(folder, ProgressListener.NONE);
        Files.writeString(folder.resolve("edit.txt"), "a, \"quoted\" change");
        Files.writeString(folder.resolve("new, file.txt"), "x");
        ScanReport check = service.checkIntegrity(folder, ProgressListener.NONE);

        Path csv = tmp.resolve("report.csv");
        ReportExporter.exportCsv(check, csv);
        String text = Files.readString(csv, StandardCharsets.UTF_8);

        assertTrue(text.contains("Integrity check"));
        assertTrue(text.contains("Status,Relative path,Details,Size (bytes),Duplicate group"));
        assertTrue(text.contains("Modified,edit.txt,"));
        assertTrue(text.contains("Added,\"new, file.txt\","), "values with commas must be quoted");
        assertTrue(text.contains(ReportExporter.DISCLAIMER.substring(0, 30)));
    }
}
