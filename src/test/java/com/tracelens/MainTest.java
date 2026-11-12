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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private static final String NPE_LOG = """
            Exception in thread "main" java.lang.NullPointerException: user is null
            \tat com.shop.UserService.getUserEmail(UserService.java:42)
            """;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(InputStream in, String... args) {
        return Main.run(args, in,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
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

    @Test
    void noArgumentsPrintsUsage() {
        assertEquals(0, run());
        assertTrue(output().contains(Main.VERSION));
        assertTrue(output().contains("analyze"));
    }

    @Test
    void analyzePrintsRootCauseOfALogFile(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        Files.writeString(log, NPE_LOG);

        assertEquals(0, run("analyze", log.toString()));
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
    void analyzeUsesPackageOption(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        Files.writeString(log, NPE_LOG);

        assertEquals(0, run("analyze", log.toString(), "--package", "com.other"));
        assertTrue(output().contains("try --package"));
    }

    @Test
    void missingFileGivesFriendlyError() {
        assertEquals(1, run("analyze", "does-not-exist.log"));
        assertTrue(errors().contains("File not found: does-not-exist.log"));
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

    @Test
    void unknownCommandIsRejected() {
        assertEquals(1, run("explode"));
        assertTrue(errors().contains("Unknown command: explode"));
    }

    @Test
    void futureCommandsSayTheyAreNotReady() {
        assertEquals(1, run("solve", "E1", "fixed it"));
        assertTrue(errors().contains("not available yet"));
    }
}
