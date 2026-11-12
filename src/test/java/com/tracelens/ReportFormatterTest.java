package com.tracelens;

import com.tracelens.parser.Cause;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    private final ReportFormatter formatter = new ReportFormatter();

    private static final Frame MINE = new Frame("com.shop.DbConfig", "connect", "DbConfig.java", 18);
    private static final Frame LIBRARY = new Frame("org.hibernate.Session", "connect", "Session.java", 512);

    @Test
    void showsRootCauseYourCodeAndChain() {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.lang.IllegalStateException", "Failed to start", List.of(LIBRARY)),
                new Cause("java.sql.SQLException", "link failure", List.of(LIBRARY, MINE))));

        String text = formatter.format("app.log", 300, List.of(report), null);

        assertTrue(text.contains("Found 1 error."));
        assertTrue(text.contains("ROOT CAUSE  java.sql.SQLException: link failure"));
        assertTrue(text.contains("YOUR CODE   DbConfig.java:18  connect()"));
        assertTrue(text.contains("CHAIN       IllegalStateException -> SQLException"));
    }

    @Test
    void showsHintWhenYourCodeIsNotFound() {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.sql.SQLException", "", List.of(LIBRARY))));

        String text = formatter.format("app.log", 10, List.of(report), null);

        assertTrue(text.contains("try --package"));
        assertFalse(text.contains("CHAIN"), "a single exception has no chain to show");
    }

    @Test
    void repeatedLinesFromRecursionAreShownOnceAndLimited() {
        Frame a = new Frame("com.shop.Tree", "walk", "Tree.java", 5);
        Frame b = new Frame("com.shop.Tree", "walk", "Tree.java", 9);
        Frame c = new Frame("com.shop.Tree", "start", "Tree.java", 2);
        Frame d = new Frame("com.shop.App", "main", "App.java", 1);
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.lang.StackOverflowError", "", List.of(a, a, a, b, c, d))));

        String text = formatter.format("app.log", 10, List.of(report), null);

        assertTrue(text.indexOf("Tree.java:5") == text.lastIndexOf("Tree.java:5"));
        assertTrue(text.contains("Tree.java:2"));
        assertFalse(text.contains("App.java:1"), "only " + ReportFormatter.MAX_YOUR_CODE_LINES + " lines are shown");
    }

    @Test
    void saysSoWhenThereAreNoErrors() {
        String text = formatter.format("app.log", 5, List.of(), null);

        assertTrue(text.contains("No Java errors found."));
    }
}
