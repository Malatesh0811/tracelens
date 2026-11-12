package com.tracelens;

import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns parsed errors into the text shown to the user.
 * Returns a String instead of printing, so tests can check the exact output.
 */
public class ReportFormatter {

    /** At most this many of the user's own code lines are shown per error. */
    static final int MAX_YOUR_CODE_LINES = 3;

    private static final String NL = System.lineSeparator();
    private static final String INDENT = "              ";

    public String format(String source, int lineCount, List<ErrorReport> reports, String basePackage) {
        StringBuilder out = new StringBuilder();
        out.append("TraceLens ").append(Main.VERSION)
           .append(" - analyzed ").append(source).append(" (").append(lineCount).append(" lines)").append(NL);

        if (reports.isEmpty()) {
            out.append("No Java errors found.").append(NL);
            return out.toString();
        }
        out.append("Found ").append(reports.size()).append(reports.size() == 1 ? " error." : " errors.").append(NL);

        for (int i = 0; i < reports.size(); i++) {
            out.append(NL);
            appendError(out, "E" + (i + 1), reports.get(i), basePackage);
        }
        return out.toString();
    }

    private void appendError(StringBuilder out, String id, ErrorReport report, String basePackage) {
        out.append("ERROR #").append(id).append(NL);
        out.append("  ROOT CAUSE  ").append(report.rootCause().display()).append(NL);

        List<String> yourLines = uniqueDisplays(report.yourCodeFrames(basePackage));
        if (yourLines.isEmpty()) {
            out.append("  YOUR CODE   (not found - the error is inside libraries;")
               .append(" try --package com.yourapp)").append(NL);
        } else {
            for (int i = 0; i < yourLines.size(); i++) {
                out.append(i == 0 ? "  YOUR CODE   " : INDENT).append(yourLines.get(i)).append(NL);
            }
        }

        if (report.causes().size() > 1) {
            out.append("  CHAIN       ").append(String.join(" -> ", report.causeChain())).append(NL);
        }
    }

    /** Recursive calls repeat the same line; show each line once, up to the limit. */
    private static List<String> uniqueDisplays(List<Frame> frames) {
        List<String> lines = new ArrayList<>();
        for (Frame frame : frames) {
            String text = frame.display();
            if (!lines.contains(text)) {
                lines.add(text);
            }
            if (lines.size() == MAX_YOUR_CODE_LINES) {
                break;
            }
        }
        return lines;
    }
}
