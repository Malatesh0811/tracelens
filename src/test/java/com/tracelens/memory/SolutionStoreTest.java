package com.tracelens.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolutionStoreTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve(".tracelens").resolve("solutions.txt");
    }

    @Test
    void missingFileMeansNoFixesYet() throws IOException {
        assertTrue(new SolutionStore(file()).all().isEmpty());
    }

    @Test
    void savedFixSurvivesARestart() throws IOException {
        new SolutionStore(file()).save("fp-1", "java.sql.SQLException: link failure", "started MySQL", DAY);

        Solution loaded = new SolutionStore(file()).all().get(0);   // new object = fresh start
        assertEquals("S1", loaded.id());
        assertEquals("fp-1", loaded.fingerprint());
        assertEquals("java.sql.SQLException: link failure", loaded.rootCause());
        assertEquals("started MySQL", loaded.fix());
        assertEquals(1, loaded.worked());
        assertEquals(0, loaded.failed());
        assertEquals("2026-11-12", loaded.savedOn());
    }

    @Test
    void idsCountUp() throws IOException {
        SolutionStore store = new SolutionStore(file());
        store.save("fp-1", "A", "fix one", DAY);
        Solution second = store.save("fp-2", "B", "fix two", DAY);

        assertEquals("S2", second.id());
    }

    @Test
    void sameFixForSameErrorIsCountedNotDuplicated() throws IOException {
        SolutionStore store = new SolutionStore(file());
        Solution first = store.save("fp-1", "A", "started MySQL", DAY);
        Solution again = store.save("fp-1", "A", "  Started mysql ", DAY);

        assertSame(first, again);
        assertEquals(1, store.all().size());
        assertEquals(2, again.worked());
    }

    @Test
    void feedbackChangesCountsAndIsSaved() throws IOException {
        SolutionStore store = new SolutionStore(file());
        Solution fix = store.save("fp-1", "A", "started MySQL", DAY);
        store.feedback(fix, true);
        store.feedback(fix, false);

        Solution loaded = new SolutionStore(file()).all().get(0);
        assertEquals(2, loaded.worked());
        assertEquals(1, loaded.failed());
        assertEquals(1, loaded.score());
    }

    @Test
    void findByIdIgnoresCase() throws IOException {
        SolutionStore store = new SolutionStore(file());
        store.save("fp-1", "A", "fix", DAY);

        assertTrue(store.findById("s1").isPresent());
        assertTrue(store.findById("S9").isEmpty());
    }

    @Test
    void tabsAndLineBreaksInTextDoNotBreakTheFile() throws IOException {
        new SolutionStore(file()).save("fp-1", "A", "line one\nline\ttwo", DAY);

        assertEquals("line one line two", new SolutionStore(file()).all().get(0).fix());
    }

    @Test
    void damagedFileGivesAClearError() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "S1\tonly three\tcolumns\n");

        IOException error = assertThrows(IOException.class, () -> new SolutionStore(file()));
        assertTrue(error.getMessage().contains("Line 1"));
    }
}
