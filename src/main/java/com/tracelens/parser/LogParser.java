package com.tracelens.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads log lines and finds every Java stack trace in them.
 *
 * It reads the log once, top to bottom, and remembers whether it is
 * currently inside a stack trace or not. Each line is one of:
 * <pre>
 *   java.lang.IllegalStateException: msg      exception header  -> start a new error
 *   Caused by: java.sql.SQLException: msg     cause header      -> add a cause to the error
 *       at com.shop.DbConfig.connect(...)     frame             -> add to the current cause
 *       ... 80 more                           skipped frames    -> ignore
 *   anything else                             normal log line   -> the error (if any) is finished
 * </pre>
 */
public class LogParser {

    // A fully qualified class name ending in Exception, Error or Throwable, then an optional ": message"
    private static final Pattern HEADER = Pattern.compile(
            "^([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+(?:Exception|Error|Throwable))(?::\\s?(.*))?$");

    // "at [module/]package.Class.method(File.java:12)"
    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at\\s+(?:\\S+/)?([\\w$.]+)\\.([\\w$<>-]+)\\((.*)\\)\\s*$");

    // "... 80 more" or "... 42 common frames omitted"
    private static final Pattern SKIPPED = Pattern.compile("^\\s*\\.\\.\\. \\d+ .*$");

    private static final String CAUSED_BY = "Caused by: ";
    private static final Pattern THREAD_PREFIX = Pattern.compile("^Exception in thread \"[^\"]*\" ");

    // Parsing state for the error currently being read
    private List<Cause> causes;
    private String type;
    private String message;
    private List<Frame> frames;

    /** Finds all errors in the given log lines. Errors without any "at" frame are ignored. */
    public List<ErrorReport> parse(List<String> lines) {
        List<ErrorReport> reports = new ArrayList<>();
        causes = null;

        for (String line : lines) {
            String trimmed = line.trim();

            if (causes != null && FRAME.matcher(line).matches()) {
                frames.add(parseFrame(line));
            } else if (causes != null && SKIPPED.matcher(line).matches()) {
                // frames already printed above; nothing new
            } else if (causes != null && trimmed.startsWith(CAUSED_BY)) {
                Matcher header = HEADER.matcher(trimmed.substring(CAUSED_BY.length()));
                if (header.matches()) {
                    finishCause();
                    startCause(header);
                } else {
                    finishError(reports);
                }
            } else {
                // Not part of the current error: close it, then check if a new one starts here
                finishError(reports);
                Matcher header = HEADER.matcher(THREAD_PREFIX.matcher(trimmed).replaceFirst(""));
                if (header.matches()) {
                    causes = new ArrayList<>();
                    startCause(header);
                }
            }
        }
        finishError(reports);
        return reports;
    }

    private void startCause(Matcher header) {
        type = header.group(1);
        message = header.group(2) == null ? "" : header.group(2).trim();
        frames = new ArrayList<>();
    }

    private void finishCause() {
        causes.add(new Cause(type, message, frames));
    }

    private void finishError(List<ErrorReport> reports) {
        if (causes == null) {
            return;
        }
        finishCause();
        boolean hasFrames = false;
        for (Cause cause : causes) {
            if (!cause.frames().isEmpty()) {
                hasFrames = true;
            }
        }
        if (hasFrames) {
            reports.add(new ErrorReport(causes));
        }
        causes = null;
    }

    /** Turns "  at com.shop.DbConfig.connect(DbConfig.java:18)" into a Frame. */
    private static Frame parseFrame(String line) {
        Matcher m = FRAME.matcher(line);
        m.matches();
        String className = m.group(1);
        String methodName = m.group(2);
        String location = m.group(3); // "DbConfig.java:18", "DbConfig.java", "Native Method", "Unknown Source"

        if (!location.contains(".java") && !location.contains(".kt")) {
            return new Frame(className, methodName, null, -1);
        }
        int colon = location.lastIndexOf(':');
        if (colon < 0) {
            return new Frame(className, methodName, location, -1);
        }
        String fileName = location.substring(0, colon);
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(location.substring(colon + 1));
        } catch (NumberFormatException e) {
            lineNumber = -1;
        }
        return new Frame(className, methodName, fileName, lineNumber);
    }
}
