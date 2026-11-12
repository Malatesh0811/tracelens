package com.tracelens;

import com.tracelens.analysis.ErrorGroup;
import com.tracelens.analysis.ErrorGrouper;
import com.tracelens.memory.LastAnalysis;
import com.tracelens.memory.Solution;
import com.tracelens.memory.SolutionStore;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.LogParser;
import com.tracelens.rules.Rulebook;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Entry point of TraceLens: reads the command line and runs the right command.
 *
 * Commands: analyze, solve, feedback, history.
 * Saved data lives in a ".tracelens" folder in the folder where you run the tool.
 */
public class Main {

    public static final String VERSION = "0.3.0";

    static final String SOLUTIONS_FILE = "solutions.txt";
    static final String LAST_ANALYSIS_FILE = "last-analysis.txt";

    public static void main(String[] args) {
        int exitCode = run(args, System.in, System.out, System.err, Path.of(".tracelens"));
        System.exit(exitCode);
    }

    /**
     * Does all the work of main() but takes input, output and the data folder as parameters,
     * so tests can call it with fake streams and a temporary folder.
     *
     * @return exit code: 0 = success, 1 = user error (bad command, missing file...)
     */
    static int run(String[] args, InputStream in, PrintStream out, PrintStream err, Path dataDir) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            out.println(usage());
            return 0;
        }
        try {
            switch (args[0]) {
                case "analyze":
                    return analyze(args, in, out, err, dataDir);
                case "solve":
                    return solve(args, out, err, dataDir);
                case "feedback":
                    return feedback(args, out, err, dataDir);
                case "history":
                    return history(out, dataDir);
                default:
                    err.println("Unknown command: " + args[0]);
                    err.println(usage());
                    return 1;
            }
        } catch (IOException e) {
            err.println("Could not read or write TraceLens data in " + dataDir + ": " + e.getMessage());
            return 1;
        }
    }

    // ---------------------------------------------------------------- analyze

    private static int analyze(String[] args, InputStream in, PrintStream out, PrintStream err, Path dataDir)
            throws IOException {
        String file = null;
        String basePackage = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--package")) {
                if (i + 1 >= args.length) {
                    err.println("--package needs a value, e.g. --package com.shop");
                    return 1;
                }
                basePackage = args[++i];
            } else if (file == null) {
                file = args[i];
            } else {
                err.println("Unexpected argument: " + args[i]);
                return 1;
            }
        }
        if (file == null) {
            err.println("Please give a log file, e.g.: tracelens analyze app.log  (use - to read piped input)");
            return 1;
        }

        String text;
        try {
            text = file.equals("-") ? decode(in.readAllBytes()) : decode(Files.readAllBytes(Path.of(file)));
        } catch (NoSuchFileException e) {
            err.println("File not found: " + file);
            return 1;
        } catch (IOException e) {
            err.println("Could not read " + file + ": " + e.getMessage());
            return 1;
        }

        List<String> lines = text.lines().toList();
        List<ErrorReport> reports = new LogParser().parse(lines);
        List<ErrorGroup> groups = new ErrorGrouper().group(reports, basePackage);
        SolutionStore store = new SolutionStore(dataDir.resolve(SOLUTIONS_FILE));

        String source = file.equals("-") ? "piped input" : file;
        ReportFormatter formatter = new ReportFormatter(Rulebook.defaultRules(), store.all());
        out.print(formatter.format(source, lines.size(), groups, basePackage));

        if (!groups.isEmpty()) {
            LastAnalysis.save(dataDir.resolve(LAST_ANALYSIS_FILE), groups); // so "solve E1" knows what E1 is
        }
        return 0;
    }

    // ---------------------------------------------------------------- solve

    /** tracelens solve E1 "what you did" */
    private static int solve(String[] args, PrintStream out, PrintStream err, Path dataDir) throws IOException {
        if (args.length < 3) {
            err.println("Usage: tracelens solve <error id> \"what you did\"   e.g. tracelens solve E1 \"started MySQL\"");
            return 1;
        }
        String errorId = args[1];
        String fix = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (fix.isEmpty()) {
            err.println("Please describe the fix, e.g. tracelens solve E1 \"started MySQL\"");
            return 1;
        }

        Path lastAnalysis = dataDir.resolve(LAST_ANALYSIS_FILE);
        if (!Files.exists(lastAnalysis)) {
            err.println("No analysis found. First run: tracelens analyze <logfile>");
            return 1;
        }
        Optional<LastAnalysis.Entry> entry = LastAnalysis.find(lastAnalysis, errorId);
        if (entry.isEmpty()) {
            err.println("No error " + errorId + " in the last analysis. Use an id shown there, like E1.");
            return 1;
        }

        SolutionStore store = new SolutionStore(dataDir.resolve(SOLUTIONS_FILE));
        int before = store.all().size();
        Solution saved = store.save(entry.get().fingerprint(), entry.get().rootCause(), fix, LocalDate.now());

        if (store.all().size() > before) {
            out.println("Saved as " + saved.id() + " for " + entry.get().id().toUpperCase()
                    + " (" + entry.get().rootCause() + ").");
            out.println("TraceLens will suggest it when this error, or a similar one, appears again.");
        } else {
            out.println("This fix was already saved as " + saved.id() + ". Counted as worked again ("
                    + times(saved.worked()) + ").");
        }
        return 0;
    }

    // ---------------------------------------------------------------- feedback

    /** tracelens feedback S1 yes|no */
    private static int feedback(String[] args, PrintStream out, PrintStream err, Path dataDir) throws IOException {
        if (args.length != 3 || !(args[2].equalsIgnoreCase("yes") || args[2].equalsIgnoreCase("no"))) {
            err.println("Usage: tracelens feedback <fix id> yes|no   e.g. tracelens feedback S1 yes");
            return 1;
        }
        SolutionStore store = new SolutionStore(dataDir.resolve(SOLUTIONS_FILE));
        Optional<Solution> solution = store.findById(args[1]);
        if (solution.isEmpty()) {
            err.println("No saved fix " + args[1] + ". See all fixes with: tracelens history");
            return 1;
        }
        boolean worked = args[2].equalsIgnoreCase("yes");
        store.feedback(solution.get(), worked);

        Solution s = solution.get();
        if (worked) {
            out.println("Great! " + s.id() + " has now worked " + times(s.worked()) + ". It will rank higher.");
        } else {
            out.println("Noted. " + s.id() + " has failed " + times(s.failed()) + ". It will rank lower.");
        }
        return 0;
    }

    /** 1 becomes "1 time", 3 becomes "3 times". */
    private static String times(int count) {
        return count == 1 ? "1 time" : count + " times";
    }

    // ---------------------------------------------------------------- history

    private static int history(PrintStream out, Path dataDir) throws IOException {
        Path file = dataDir.resolve(SOLUTIONS_FILE);
        List<Solution> fixes = new ArrayList<>(new SolutionStore(file).all());
        if (fixes.isEmpty()) {
            out.println("No saved fixes yet. After fixing an error, run: tracelens solve E1 \"what you did\"");
            return 0;
        }
        // Most trusted first; equal scores keep the order they were saved in
        fixes.sort(Comparator.comparingInt(Solution::score).reversed());

        out.println("TraceLens history - " + fixes.size() + (fixes.size() == 1 ? " saved fix" : " saved fixes")
                + " (" + file + ")");
        for (Solution s : fixes) {
            out.println();
            out.println("  " + s.id() + "  " + s.rootCause());
            out.println("      fix:    " + s.fix());
            out.println("      worked " + s.worked() + ", failed " + s.failed() + ", saved " + s.savedOn());
        }
        return 0;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Reads bytes as UTF-8 (invalid bytes become a replacement character instead of crashing)
     * and removes the invisible byte order mark (BOM) that PowerShell pipes and Notepad add
     * at the start. Without this, the first line would not be recognised as an exception.
     */
    static String decode(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    static String usage() {
        String nl = System.lineSeparator();
        return "TraceLens " + VERSION + " - explains Java crash logs and remembers your fixes" + nl
                + "Usage:" + nl
                + "  tracelens analyze <logfile> [--package <your.package>]   find the root cause of each error" + nl
                + "  tracelens analyze -                                      read piped input, e.g. mvn test | ..." + nl
                + "  tracelens solve <error id> \"<what you did>\"              save your fix, e.g. solve E1 \"...\"" + nl
                + "  tracelens feedback <fix id> yes|no                       did a suggested fix work?" + nl
                + "  tracelens history                                        list all saved fixes";
    }
}
