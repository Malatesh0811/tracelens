package com.tracelens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private static final String NPE_LOG = """
            Exception in thread "main" java.lang.NullPointerException: user is null
            \tat com.shop.UserService.getUserEmail(UserService.java:42)
            """;

    private static final String DB_LOG_REFUSED = """
            2026-11-12 10:02:11 ERROR Application failed
            java.sql.SQLException: Communications link failure - Connection refused
            \tat com.shop.config.DbConfig.connect(DbConfig.java:18)
            """;

    private static final String DB_LOG_TIMED_OUT = """
            2026-11-27 09:00:00 ERROR Request failed
            java.sql.SQLException: Communications link failure - Connection timed out
            \tat com.shop.repo.ProductRepo.findAll(ProductRepo.java:22)
            """;

    @TempDir
    Path dir;

    private ByteArrayOutputStream out = new ByteArrayOutputStream();
    private ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(InputStream in, String... args) {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        return Main.run(args, in,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                dir.resolve(".tracelens"));
    }

    private int run(String... args) {
        return run(InputStream.nullInputStream(), args);
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String errors() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private String log(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file.toString();
    }

    // ------------------------------------------------------------ general

    @Test
    void noArgumentsPrintsUsage() {
        assertEquals(0, run());
        assertTrue(output().contains(Main.VERSION));
        assertTrue(output().contains("feedback"));
    }

    @Test
    void unknownCommandIsRejected() {
        assertEquals(1, run("explode"));
        assertTrue(errors().contains("Unknown command: explode"));
    }

    // ------------------------------------------------------------ analyze

    @Test
    void analyzePrintsRootCauseOfALogFile() throws IOException {
        assertEquals(0, run("analyze", log("app.log", NPE_LOG)));
        assertTrue(output().contains("ROOT CAUSE  java.lang.NullPointerException: user is null"));
        assertTrue(output().contains("YOUR CODE   UserService.java:42  getUserEmail()"));
    }

    @Test
    void analyzeReadsPipedInputWithDash() {
        InputStream piped = new ByteArrayInputStream(NPE_LOG.getBytes(StandardCharsets.UTF_8));

        assertEquals(0, run(piped, "analyze", "-"));
        assertTrue(output().contains("piped input"));
        assertTrue(output().contains("NullPointerException"));
    }

    @Test
    void analyzeIgnoresByteOrderMarkFromPowerShellPipe() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] log = NPE_LOG.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + log.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(log, 0, withBom, bom.length, log.length);

        assertEquals(0, run(new ByteArrayInputStream(withBom), "analyze", "-"));
        assertTrue(output().contains("Found 1 error."));
    }

    @Test
    void decodeRemovesOnlyTheLeadingByteOrderMark() {
        assertEquals("abc", Main.decode("﻿abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("abc", Main.decode("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void analyzeUsesPackageOption() throws IOException {
        assertEquals(0, run("analyze", log("app.log", NPE_LOG), "--package", "com.other"));
        assertTrue(output().contains("try --package"));
    }

    @Test
    void missingFileGivesFriendlyError() {
        assertEquals(1, run("analyze", "does-not-exist.log"));
        assertTrue(errors().contains("File not found: does-not-exist.log"));
    }

    @Test
    void folderInsteadOfFileGivesFriendlyError() {
        assertEquals(1, run("analyze", dir.toString()));
        assertTrue(errors().contains("Could not read"));
    }

    @Test
    void analyzeWithoutFileExplainsUsage() {
        assertEquals(1, run("analyze"));
        assertTrue(errors().contains("Please give a log file"));
    }

    @Test
    void packageOptionNeedsAValue() {
        assertEquals(1, run("analyze", "app.log", "--package"));
        assertTrue(errors().contains("--package needs a value"));
    }

    // ------------------------------------------------------------ the full story

    @Test
    void savedFixIsSuggestedForTheSameAndForASimilarError() throws IOException {
        // Day 1: crash, then save the fix
        run("analyze", log("day1.log", DB_LOG_REFUSED));
        assertFalse(output().contains("SOLVED BEFORE"));
        assertEquals(0, run("solve", "E1", "MySQL was stopped. Fix: net start mysql80"));
        assertTrue(output().contains("Saved as S1 for E1"));

        // Same error again: exact match
        run("analyze", log("day2.log", DB_LOG_REFUSED));
        assertTrue(output().contains("SOLVED BEFORE"));
        assertTrue(output().contains("S1  same error - worked 1 time"));
        assertTrue(output().contains("MySQL was stopped. Fix: net start mysql80"));

        // Day 15: different file and message, similar error
        run("analyze", log("day15.log", DB_LOG_TIMED_OUT));
        assertTrue(output().contains("S1  67% similar"));

        // It worked again
        assertEquals(0, run("feedback", "S1", "yes"));
        assertTrue(output().contains("S1 has now worked 2 times"));
    }

    @Test
    void fixWithoutQuotesIsJoined() throws IOException {
        run("analyze", log("app.log", NPE_LOG));
        run("solve", "e1", "added", "a", "null", "check");

        run("history");
        assertTrue(output().contains("fix:    added a null check"));
    }

    @Test
    void sameFixTwiceIsCountedNotDuplicated() throws IOException {
        run("analyze", log("app.log", NPE_LOG));
        run("solve", "E1", "added a null check");
        run("solve", "E1", "added a null check");

        assertTrue(output().contains("already saved as S1"));
        run("history");
        assertTrue(output().contains("1 saved fix "));
    }

    // ------------------------------------------------------------ solve errors

    @Test
    void solveBeforeAnyAnalysisExplainsWhatToDo() {
        assertEquals(1, run("solve", "E1", "something"));
        assertTrue(errors().contains("First run: tracelens analyze"));
    }

    @Test
    void solveWithUnknownErrorId() throws IOException {
        run("analyze", log("app.log", NPE_LOG));

        assertEquals(1, run("solve", "E7", "something"));
        assertTrue(errors().contains("No error E7"));
    }

    @Test
    void solveNeedsAFix() {
        assertEquals(1, run("solve", "E1"));
        assertTrue(errors().contains("Usage: tracelens solve"));
        assertEquals(1, run("solve", "E1", "  "));
        assertTrue(errors().contains("Please describe the fix"));
    }

    // ------------------------------------------------------------ feedback and history

    @Test
    void negativeFeedbackIsCounted() throws IOException {
        run("analyze", log("app.log", NPE_LOG));
        run("solve", "E1", "restarted the app");

        assertEquals(0, run("feedback", "s1", "NO"));
        assertTrue(output().contains("S1 has failed 1 time."));
    }

    @Test
    void feedbackChecksItsArguments() {
        assertEquals(1, run("feedback", "S1", "maybe"));
        assertTrue(errors().contains("Usage: tracelens feedback"));
        assertEquals(1, run("feedback", "S9", "yes"));
        assertTrue(errors().contains("No saved fix S9"));
    }

    @Test
    void emptyHistoryExplainsHowToSaveAFix() {
        assertEquals(0, run("history"));
        assertTrue(output().contains("No saved fixes yet"));
    }

    @Test
    void historyShowsMostTrustedFixFirst() throws IOException {
        run("analyze", log("a.log", NPE_LOG));
        run("solve", "E1", "fix for npe");
        run("analyze", log("b.log", DB_LOG_REFUSED));
        run("solve", "E1", "fix for db");
        run("feedback", "S2", "yes");

        run("history");
        String text = output();
        assertTrue(text.contains("2 saved fixes"));
        assertTrue(text.indexOf("S2  java.sql.SQLException") < text.indexOf("S1  java.lang.NullPointerException"));
    }

    @Test
    void damagedSolutionsFileGivesClearError() throws IOException {
        Files.createDirectories(dir.resolve(".tracelens"));
        Files.writeString(dir.resolve(".tracelens").resolve("solutions.txt"), "broken line\n");

        assertEquals(1, run("history"));
        assertTrue(errors().contains("is damaged"));
    }
}
