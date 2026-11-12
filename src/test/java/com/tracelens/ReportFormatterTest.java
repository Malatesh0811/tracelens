package com.tracelens;

import com.tracelens.analysis.ErrorGroup;
import com.tracelens.analysis.ErrorGrouper;
import com.tracelens.parser.Cause;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;
import com.tracelens.rules.Rulebook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    private final ReportFormatter formatter = new ReportFormatter(Rulebook.defaultRules());

    private static final Frame MINE = new Frame("com.shop.DbConfig", "connect", "DbConfig.java", 18);
    private static final Frame LIBRARY = new Frame("org.hibernate.Session", "connect", "Session.java", 512);

    private static List<ErrorGroup> groups(ErrorReport... reports) {
        return new ErrorGrouper().group(List.of(reports), null);
    }

    private static ErrorReport dbError(String time) {
        return new ErrorReport(List.of(
                new Cause("java.lang.IllegalStateException", "Failed to start", List.of(LIBRARY)),
                new Cause("java.sql.SQLException", "Communications link failure", List.of(LIBRARY, MINE))), time);
    }

    @Test
    void showsRootCauseYourCodeAndChain() {
        String text = formatter.format("app.log", 300, groups(dbError(null)), null);

        assertTrue(text.contains("Found 1 error."));
        assertTrue(text.contains("ROOT CAUSE  java.sql.SQLException: Communications link failure"));
        assertTrue(text.contains("YOUR CODE   DbConfig.java:18  connect()"));
        assertTrue(text.contains("CHAIN       IllegalStateException -> SQLException"));
    }

    @Test
    void showsHowOftenAndWhenAnErrorHappened() {
        String text = formatter.format("app.log", 300,
                groups(dbError("10:02:11"), dbError("10:09:00"), dbError("10:15:40")), null);

        assertTrue(text.contains("Found 3 errors (1 unique)."));
        assertTrue(text.contains("ERROR #E1  (occurred 3 times, 10:02:11 -> 10:15:40)"));
        assertFalse(text.contains("#E2"));
    }

    @Test
    void singleErrorShowsItsTime() {
        String text = formatter.format("app.log", 30, groups(dbError("10:02:11")), null);

        assertTrue(text.contains("(occurred once, at 10:02:11)"));
    }

    @Test
    void showsWhyAndNumberedFixesFromTheRulebook() {
        String text = formatter.format("app.log", 300, groups(dbError(null)), null);

        assertTrue(text.contains("WHY         Your app cannot reach the database server."));
        assertTrue(text.contains("TRY         1. Check the database is running"));
        assertTrue(text.contains("2. Check host, port and database name"));
    }

    @Test
    void unknownErrorSaysThereIsNoExplanationYet() {
        ErrorReport custom = new ErrorReport(List.of(
                new Cause("com.shop.OrderRejectedException", "out of stock", List.of(MINE))));

        String text = formatter.format("app.log", 10, groups(custom), null);

        assertTrue(text.contains("No built-in explanation yet"));
        assertFalse(text.contains("TRY"));
    }

    @Test
    void showsHintWhenYourCodeIsNotFound() {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.sql.SQLException", "", List.of(LIBRARY))));

        String text = formatter.format("app.log", 10, groups(report), null);

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

        String text = formatter.format("app.log", 10, groups(report), null);

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
