package com.tracelens.analysis;

import com.tracelens.parser.Cause;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FingerprintTest {

    private static ErrorReport error(String type, String message, Frame where) {
        return new ErrorReport(List.of(new Cause(type, message, List.of(where))));
    }

    private static final Frame PARSE_58 = new Frame("com.shop.CartService", "parseQuantity", "CartService.java", 58);
    private static final Frame PARSE_61 = new Frame("com.shop.CartService", "parseQuantity", "CartService.java", 61);
    private static final Frame CHECKOUT = new Frame("com.shop.CartService", "checkout", "CartService.java", 90);

    @Test
    void differentInputsGiveTheSameFingerprint() {
        String a = Fingerprint.of(error("java.lang.NumberFormatException", "For input string: \"2a\"", PARSE_58), null);
        String b = Fingerprint.of(error("java.lang.NumberFormatException", "For input string: \"abc\"", PARSE_58), null);

        assertEquals(a, b);
    }

    @Test
    void differentIdsGiveTheSameFingerprint() {
        String a = Fingerprint.of(error("com.shop.UserNotFoundException", "No user with id 105", PARSE_58), null);
        String b = Fingerprint.of(error("com.shop.UserNotFoundException", "No user with id 221", PARSE_58), null);

        assertEquals(a, b);
    }

    @Test
    void movedLineNumberGivesTheSameFingerprint() {
        // someone added 3 lines above the method: still the same bug
        String a = Fingerprint.of(error("java.lang.NumberFormatException", "x", PARSE_58), null);
        String b = Fingerprint.of(error("java.lang.NumberFormatException", "x", PARSE_61), null);

        assertEquals(a, b);
    }

    @Test
    void differentExceptionTypeGivesDifferentFingerprint() {
        String a = Fingerprint.of(error("java.lang.NumberFormatException", "x", PARSE_58), null);
        String b = Fingerprint.of(error("java.lang.ArithmeticException", "x", PARSE_58), null);

        assertNotEquals(a, b);
    }

    @Test
    void differentPlaceInYourCodeGivesDifferentFingerprint() {
        String a = Fingerprint.of(error("java.lang.NullPointerException", "", PARSE_58), null);
        String b = Fingerprint.of(error("java.lang.NullPointerException", "", CHECKOUT), null);

        assertNotEquals(a, b);
    }

    @Test
    void normalizeReplacesChangingParts() {
        assertEquals("id # at '#' \"#\" #",Fingerprint.normalize("id 105 at 'x'   \"abc\" 0x1F"));
    }
}
