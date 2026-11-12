package com.tracelens.analysis;

import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;

import java.util.List;

/**
 * Builds a "fingerprint" for an error: a short text that is the same for every
 * copy of the same error, even when details like ids or inputs change.
 *
 * <pre>
 *   NumberFormatException: For input string: "2a"   at CartService.parseQuantity:58
 *   NumberFormatException: For input string: "abc"  at CartService.parseQuantity:61
 *   both become ->
 *   java.lang.NumberFormatException | For input string: "#" | com.shop.CartService.parseQuantity
 * </pre>
 *
 * The fingerprint is used as a HashMap key, so finding duplicates is O(1) per error.
 * It is kept as readable text (not a number) so it can be saved to a file in Phase 3.
 */
public final class Fingerprint {

    private Fingerprint() {
        // utility class: only static methods, no objects
    }

    public static String of(ErrorReport report, String basePackage) {
        String type = report.rootCause().type();
        String message = normalize(report.rootCause().message());

        // Class + method, without line number, so editing the file above it does not change the fingerprint
        List<Frame> yours = report.yourCodeFrames(basePackage);
        String where = yours.isEmpty() ? "-" : yours.get(0).className() + "." + yours.get(0).methodName();

        return type + " | " + message + " | " + where;
    }

    /** Replaces the parts of a message that change between copies of the same error. */
    static String normalize(String message) {
        return message
                .replaceAll("\"[^\"]*\"", "\"#\"")        // "abc"   -> "#"
                .replaceAll("'[^']*'", "'#'")             // 'abc'   -> '#'
                .replaceAll("0x[0-9a-fA-F]+", "#")         // 0x1f3a  -> #
                .replaceAll("\\d+", "#")                   // 105     -> #
                .replaceAll("\\s+", " ")
                .trim();
    }
}
