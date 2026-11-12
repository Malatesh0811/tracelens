package com.tracelens.analysis;

import com.tracelens.parser.Cause;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorGrouperTest {

    private final ErrorGrouper grouper = new ErrorGrouper();

    private static final Frame CART = new Frame("com.shop.CartService", "parseQuantity", "CartService.java", 58);
    private static final Frame USER = new Frame("com.shop.UserService", "getUserEmail", "UserService.java", 42);

    private static ErrorReport nfe(String input, String time) {
        return new ErrorReport(List.of(new Cause("java.lang.NumberFormatException",
                "For input string: \"" + input + "\"", List.of(CART))), time);
    }

    private static ErrorReport npe(String time) {
        return new ErrorReport(List.of(new Cause("java.lang.NullPointerException", "", List.of(USER))), time);
    }

    @Test
    void copiesOfTheSameErrorBecomeOneGroup() {
        List<ErrorGroup> groups = grouper.group(List.of(nfe("2a", null), nfe("abc", null), nfe(" 3", null)), null);

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).count());
    }

    @Test
    void mostFrequentErrorComesFirst() {
        List<ErrorGroup> groups = grouper.group(List.of(npe(null), nfe("1a", null), nfe("2a", null)), null);

        assertEquals(2, groups.size());
        assertEquals("java.lang.NumberFormatException", groups.get(0).example().rootCause().type());
        assertEquals(2, groups.get(0).count());
        assertEquals(1, groups.get(1).count());
    }

    @Test
    void equalCountsKeepLogOrder() {
        List<ErrorGroup> groups = grouper.group(List.of(npe(null), nfe("1a", null)), null);

        assertEquals("java.lang.NullPointerException", groups.get(0).example().rootCause().type());
    }

    @Test
    void remembersFirstAndLastTime() {
        ErrorReport first = nfe("2a", "09:05:13");
        List<ErrorGroup> groups = grouper.group(
                List.of(first, nfe("abc", "09:12:02"), nfe(" 3", "09:31:18")), null);

        ErrorGroup group = groups.get(0);
        assertSame(first, group.example());
        assertEquals("09:05:13", group.firstSeen());
        assertEquals("09:31:18", group.lastSeen());
    }

    @Test
    void timesAreNullWhenLogHasNone() {
        ErrorGroup group = grouper.group(List.of(npe(null), npe(null)), null).get(0);

        assertNull(group.firstSeen());
        assertNull(group.lastSeen());
    }

    @Test
    void noErrorsGiveNoGroups() {
        assertTrue(grouper.group(List.of(), null).isEmpty());
    }
}
