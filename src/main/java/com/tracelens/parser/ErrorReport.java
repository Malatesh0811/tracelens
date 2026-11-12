package com.tracelens.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * One complete error found in a log: the top exception plus every "Caused by".
 *
 * @param causes    in the order they appear in the log: top error first, root cause last
 * @param timestamp time of the log line just before the error (e.g. "2026-11-12 10:02:11"),
 *                  or null when the log has no timestamps
 */
public record ErrorReport(List<Cause> causes, String timestamp) {

    public ErrorReport {
        if (causes == null || causes.isEmpty()) {
            throw new IllegalArgumentException("An error report needs at least one cause");
        }
        causes = List.copyOf(causes);
    }

    /** For errors without a timestamp. */
    public ErrorReport(List<Cause> causes) {
        this(causes, null);
    }

    /** The first exception printed, usually a generic wrapper. */
    public Cause topError() {
        return causes.get(0);
    }

    /** The deepest "Caused by", which is the real reason for the crash. */
    public Cause rootCause() {
        return causes.get(causes.size() - 1);
    }

    /** Short names from top to root, e.g. [IllegalStateException, SQLException]. */
    public List<String> causeChain() {
        List<String> chain = new ArrayList<>();
        for (Cause cause : causes) {
            chain.add(cause.simpleType());
        }
        return chain;
    }

    /**
     * Frames from the user's own code, closest to the root cause first.
     * Looks in the root cause; if it has none (the crash happened deep inside a
     * library), moves up the chain until it finds the user's code.
     */
    public List<Frame> yourCodeFrames(String basePackage) {
        for (int i = causes.size() - 1; i >= 0; i--) {
            List<Frame> yours = new ArrayList<>();
            for (Frame frame : causes.get(i).frames()) {
                if (frame.isYourCode(basePackage)) {
                    yours.add(frame);
                }
            }
            if (!yours.isEmpty()) {
                return yours;
            }
        }
        return List.of();
    }
}
