package com.tracelens;

import com.tracelens.analysis.ErrorGroup;
import com.tracelens.memory.SimilarityMatcher;
import com.tracelens.memory.Solution;
import com.tracelens.memory.Suggestion;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;
import com.tracelens.rules.Rule;
import com.tracelens.rules.Rulebook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns grouped errors into the text shown to the user.
 * Returns a String instead of printing, so tests can check the exact output.
 */
public class ReportFormatter {

    /** At most this many of the user's own code lines are shown per error. */
    static final int MAX_YOUR_CODE_LINES = 3;

    private static final String NL = System.lineSeparator();
    private static final String INDENT = "              ";

    private final Rulebook rulebook;
    private final List<Solution> pastFixes;
    private final SimilarityMatcher matcher = new SimilarityMatcher();

    /**
     * The rulebook and saved fixes are passed in (not loaded here), so tests can use their own.
     */
    public ReportFormatter(Rulebook rulebook, List<Solution> pastFixes) {
        this.rulebook = rulebook;
        this.pastFixes = List.copyOf(pastFixes);
    }

    /** For when there are no saved fixes. */
    public ReportFormatter(Rulebook rulebook) {
        this(rulebook, List.of());
    }

    public String format(String source, int lineCount, List<ErrorGroup> groups, String basePackage) {
        StringBuilder out = new StringBuilder();
        out.append("TraceLens ").append(Main.VERSION)
           .append(" - analyzed ").append(source).append(" (").append(lineCount).append(" lines)").append(NL);

        if (groups.isEmpty()) {
            out.append("No Java errors found.").append(NL);
            return out.toString();
        }
        int total = 0;
        for (ErrorGroup group : groups) {
            total += group.count();
        }
        out.append("Found ").append(total).append(total == 1 ? " error" : " errors");
        if (total != groups.size()) {
            out.append(" (").append(groups.size()).append(" unique)");
        }
        out.append('.').append(NL);

        for (int i = 0; i < groups.size(); i++) {
            out.append(NL);
            appendGroup(out, "E" + (i + 1), groups.get(i), basePackage);
        }
        out.append(NL).append("Fixed an error? Save the fix:  tracelens solve E1 \"what you did\"").append(NL);
        return out.toString();
    }

    private void appendGroup(StringBuilder out, String id, ErrorGroup group, String basePackage) {
        ErrorReport report = group.example();
        out.append("ERROR #").append(id).append("  (").append(occurrences(group)).append(')').append(NL);
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

        // Your own past fixes come first: they are the most trusted advice
        List<Suggestion> suggestions = matcher.suggest(group.fingerprint(), report.rootCause().type(),
                report.rootCause().display(), pastFixes);
        if (!suggestions.isEmpty()) {
            out.append("  SOLVED BEFORE").append(NL);
            for (Suggestion suggestion : suggestions) {
                Solution fix = suggestion.solution();
                out.append("    ").append(fix.id()).append("  ")
                   .append(suggestion.percent() == 100 ? "same error" : suggestion.percent() + "% similar")
                   .append(" - worked ").append(fix.worked()).append(fix.worked() == 1 ? " time" : " times");
                if (fix.failed() > 0) {
                    out.append(", failed ").append(fix.failed());
                }
                out.append(" - saved ").append(fix.savedOn()).append(NL);
                out.append("        ").append(fix.fix()).append(NL);
            }
            out.append("    Did it work?  tracelens feedback ").append(suggestions.get(0).solution().id())
               .append(" yes   (or no)").append(NL);
        }

        Optional<Rule> rule = rulebook.find(report.rootCause());
        if (rule.isPresent()) {
            out.append("  WHY         ").append(rule.get().why()).append(NL);
            List<String> fixes = rule.get().fixes();
            for (int i = 0; i < fixes.size(); i++) {
                out.append(i == 0 ? "  TRY         " : INDENT)
                   .append(i + 1).append(". ").append(fixes.get(i)).append(NL);
            }
        } else {
            out.append("  WHY         No built-in explanation yet. Search the ROOT CAUSE line above.").append(NL);
        }
    }

    /** "occurred 3 times, 10:02:11 -> 10:15:40" or "occurred once, at 10:02:11". */
    private static String occurrences(ErrorGroup group) {
        String text = group.count() == 1 ? "occurred once" : "occurred " + group.count() + " times";
        if (group.firstSeen() == null) {
            return text;
        }
        if (group.count() == 1 || group.firstSeen().equals(group.lastSeen())) {
            return text + ", at " + group.firstSeen();
        }
        return text + ", " + group.firstSeen() + " -> " + group.lastSeen();
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
