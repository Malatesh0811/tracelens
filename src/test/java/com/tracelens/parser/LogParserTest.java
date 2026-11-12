package com.tracelens.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {

    private final LogParser parser = new LogParser();

    private List<ErrorReport> parse(String log) {
        return parser.parse(log.lines().toList());
    }

    @Test
    void deepestCausedByIsTheRootCause() {
        List<ErrorReport> reports = parse("""
                java.lang.IllegalStateException: Failed to start
                \tat com.shop.App.start(App.java:27)
                Caused by: org.hibernate.JDBCConnectionException: Unable to open connection
                \tat org.hibernate.Session.connect(Session.java:512)
                Caused by: java.sql.SQLException: Communications link failure
                \tat com.shop.config.DbConfig.connect(DbConfig.java:18)
                """);

        assertEquals(1, reports.size());
        Cause root = reports.get(0).rootCause();
        assertEquals("java.sql.SQLException", root.type());
        assertEquals("Communications link failure", root.message());
    }

    @Test
    void causeChainListsExceptionsFromTopToRoot() {
        ErrorReport report = parse("""
                java.lang.IllegalStateException: Failed to start
                \tat com.shop.App.start(App.java:27)
                Caused by: java.sql.SQLException: link failure
                \tat com.shop.DbConfig.connect(DbConfig.java:18)
                """).get(0);

        assertEquals(List.of("IllegalStateException", "SQLException"), report.causeChain());
        assertEquals("java.lang.IllegalStateException", report.topError().type());
    }

    @Test
    void readsExceptionInThreadMainHeader() {
        ErrorReport report = parse("""
                Exception in thread "main" java.lang.NullPointerException
                \tat com.shop.UserService.getUserEmail(UserService.java:42)
                """).get(0);

        assertEquals("java.lang.NullPointerException", report.rootCause().type());
        assertEquals("", report.rootCause().message());
    }

    @Test
    void readsFrameDetailsAndRemovesModulePrefix() {
        ErrorReport report = parse("""
                java.lang.RuntimeException: boom
                \tat java.base/java.lang.Thread.run(Thread.java:1583)
                \tat com.shop.UserService.getUserEmail(UserService.java:42)
                """).get(0);

        List<Frame> frames = report.rootCause().frames();
        assertEquals(new Frame("java.lang.Thread", "run", "Thread.java", 1583), frames.get(0));
        assertEquals(new Frame("com.shop.UserService", "getUserEmail", "UserService.java", 42), frames.get(1));
    }

    @Test
    void framesWithoutLineNumbersAreKept() {
        List<Frame> frames = parse("""
                java.lang.RuntimeException: boom
                \tat sun.misc.Unsafe.park(Native Method)
                \tat com.shop.Gen.call(Unknown Source)
                \tat com.shop.Old.run(Old.java)
                """).get(0).rootCause().frames();

        assertEquals(3, frames.size());
        assertNull(frames.get(0).fileName());
        assertEquals(-1, frames.get(0).lineNumber());
        assertNull(frames.get(1).fileName());
        assertEquals("Old.java", frames.get(2).fileName());
        assertEquals(-1, frames.get(2).lineNumber());
    }

    @Test
    void skippedFrameLinesDoNotEndTheError() {
        ErrorReport report = parse("""
                java.lang.IllegalStateException: Failed
                \tat com.shop.App.start(App.java:27)
                \t... 80 more
                Caused by: java.io.IOException: disk full
                \tat com.shop.Files.save(Files.java:9)
                \t... 42 common frames omitted
                """).get(0);

        assertEquals("java.io.IOException", report.rootCause().type());
    }

    @Test
    void findsSeveralErrorsSeparatedByNormalLogLines() {
        List<ErrorReport> reports = parse("""
                10:00:01 INFO  Starting
                java.lang.NullPointerException: user is null
                \tat com.shop.UserService.find(UserService.java:42)
                10:00:02 INFO  Retrying
                java.lang.ArithmeticException: / by zero
                \tat com.shop.Cart.average(Cart.java:7)
                10:00:03 INFO  Done
                """);

        assertEquals(2, reports.size());
        assertEquals("java.lang.NullPointerException", reports.get(0).rootCause().type());
        assertEquals("java.lang.ArithmeticException", reports.get(1).rootCause().type());
    }

    @Test
    void ignoresExceptionNamesWithoutAStackTrace() {
        List<ErrorReport> reports = parse("""
                10:00:01 WARN  Retrying after java.io.IOException: timeout
                java.io.IOException: timeout
                10:00:02 INFO  OK
                """);

        assertTrue(reports.isEmpty());
    }

    @Test
    void emptyLogHasNoErrors() {
        assertTrue(parse("").isEmpty());
    }
}
