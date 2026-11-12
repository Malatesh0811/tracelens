package com.tracelens.parser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorReportTest {

    private static final Frame LIBRARY = new Frame("org.hibernate.Session", "connect", "Session.java", 512);
    private static final Frame JDK = new Frame("java.sql.DriverManager", "getConnection", "DriverManager.java", 681);
    private static final Frame MINE = new Frame("com.shop.DbConfig", "connect", "DbConfig.java", 18);

    @Test
    void yourCodeSkipsLibraryFrames() {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.sql.SQLException", "link failure", List.of(LIBRARY, JDK, MINE))));

        assertEquals(List.of(MINE), report.yourCodeFrames(null));
    }

    @Test
    void basePackageDecidesWhatIsYourCode() {
        Frame otherCompany = new Frame("com.vendor.Client", "send", "Client.java", 3);
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.io.IOException", "", List.of(otherCompany, MINE))));

        assertEquals(List.of(otherCompany, MINE), report.yourCodeFrames(null));
        assertEquals(List.of(MINE), report.yourCodeFrames("com.shop"));
    }

    @Test
    void looksInUpperCausesWhenRootCauseIsOnlyLibraryCode() {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.lang.IllegalStateException", "", List.of(MINE)),
                new Cause("java.sql.SQLException", "", List.of(LIBRARY, JDK))));

        assertEquals(List.of(MINE), report.yourCodeFrames(null));
    }

    @Test
    void noYourCodeGivesEmptyList() {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.sql.SQLException", "", List.of(LIBRARY, JDK))));

        assertTrue(report.yourCodeFrames(null).isEmpty());
    }

    @Test
    void reportCannotBeChangedFromOutside() {
        List<Frame> frames = new ArrayList<>(List.of(MINE));
        Cause cause = new Cause("java.lang.RuntimeException", null, frames);
        frames.add(LIBRARY); // changing the original list must not affect the cause

        assertEquals(1, cause.frames().size());
        assertEquals("", cause.message());
    }

    @Test
    void reportNeedsAtLeastOneCause() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorReport(List.of()));
    }

    @Test
    void framePrintsShortLocation() {
        assertEquals("DbConfig.java:18  connect()", MINE.display());
        assertEquals("com.shop.Gen.call()  (line unknown)",
                new Frame("com.shop.Gen", "call", null, -1).display());
    }
}
