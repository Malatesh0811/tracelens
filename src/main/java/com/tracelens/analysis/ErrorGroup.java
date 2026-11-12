package com.tracelens.analysis;

import com.tracelens.parser.ErrorReport;

/**
 * All copies of one error: an example, how many times it happened, and when.
 *
 * Fields are private and only {@link ErrorGrouper} (same package) can add to a group,
 * so the count can never be set to a wrong value from outside. That is encapsulation.
 */
public class ErrorGroup {

    private final String fingerprint;
    private final ErrorReport example;
    private int count;
    private String firstSeen;
    private String lastSeen;

    ErrorGroup(String fingerprint, ErrorReport first) {
        this.fingerprint = fingerprint;
        this.example = first;
        this.count = 1;
        this.firstSeen = first.timestamp();
        this.lastSeen = first.timestamp();
    }

    /** Records one more copy of this error. */
    void add(ErrorReport report) {
        count++;
        if (report.timestamp() != null) {
            if (firstSeen == null) {
                firstSeen = report.timestamp();
            }
            lastSeen = report.timestamp();
        }
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** The first copy found in the log; all copies have the same root cause. */
    public ErrorReport example() {
        return example;
    }

    public int count() {
        return count;
    }

    /** Time of the first copy, or null when the log has no timestamps. */
    public String firstSeen() {
        return firstSeen;
    }

    /** Time of the last copy, or null when the log has no timestamps. */
    public String lastSeen() {
        return lastSeen;
    }
}
