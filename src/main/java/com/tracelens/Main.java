package com.tracelens;

import com.tracelens.analysis.ErrorGroup;
import com.tracelens.analysis.ErrorGrouper;
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
import java.util.List;

/**
 * Entry point of TraceLens: reads the command line and runs the right command.
 *
 * Commands available now: analyze. Commands solve and history come in Phase 3.
 */
public class Main {

    public static final String VERSION = "0.2.0";

    public static void main(String[] args) {
        int exitCode = run(args, System.in, System.out, System.err);
        System.exit(exitCode);
    }

    /**
     * Does all the work of main() but takes its input and output as parameters,
     * so tests can call it and check the result without a real console.
     *
     * @return exit code: 0 = success, 1 = user error (bad command, missing file)
     */
    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            out.println(usage());
            return 0;
        }
        switch (args[0]) {
            case "analyze":
                return analyze(args, in, out, err);
            case "solve":
            case "history":
                err.println("'" + args[0] + "' is not available yet. It is planned for a later version.");
                return 1;
            default:
                err.println("Unknown command: " + args[0]);
                err.println(usage());
                return 1;
        }
    }

    private static int analyze(String[] args, InputStream in, PrintStream out, PrintStream err) {
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
            text = file.equals("-") ? readAll(in) : readFile(Path.of(file));
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
        String source = file.equals("-") ? "piped input" : file;
        ReportFormatter formatter = new ReportFormatter(Rulebook.defaultRules());
        out.print(formatter.format(source, lines.size(), groups, basePackage));
        return 0;
    }

    private static String readFile(Path path) throws IOException {
        return decode(Files.readAllBytes(path));
    }

    private static String readAll(InputStream in) throws IOException {
        return decode(in.readAllBytes());
    }

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
        return "TraceLens " + VERSION + System.lineSeparator()
                + "Usage:" + System.lineSeparator()
                + "  tracelens analyze <logfile> [--package <your.package>]   find the root cause of a crash" + System.lineSeparator()
                + "  tracelens analyze -                                      read piped input, e.g. mvn test | ..." + System.lineSeparator()
                + "  tracelens solve <id> \"<fix>\"    remember how you fixed an error (coming soon)" + System.lineSeparator()
                + "  tracelens history               list errors you solved before (coming soon)";
    }
}
