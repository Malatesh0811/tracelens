package com.tracelens.memory;

/**
 * A fix the user saved for an error, plus how often it worked.
 *
 * The counts can only change through markWorked() / markFailed(), which only
 * SolutionStore (same package) can call. Outside code can read them, not set them.
 */
public class Solution {

    private final String id;           // "S1"
    private final String fingerprint;  // same format as analysis.Fingerprint
    private final String rootCause;    // "java.sql.SQLException: Communications link failure"
    private final String fix;          // what the user did
    private final String savedOn;      // "2026-11-12"
    private int worked;
    private int failed;

    public Solution(String id, String fingerprint, String rootCause, String fix,
                    int worked, int failed, String savedOn) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.rootCause = rootCause;
        this.fix = fix;
        this.worked = worked;
        this.failed = failed;
        this.savedOn = savedOn;
    }

    void markWorked() {
        worked++;
    }

    void markFailed() {
        failed++;
    }

    /** Higher is more trusted: each "worked" adds one, each "failed" takes one away. */
    public int score() {
        return worked - failed;
    }

    /** "java.sql.SQLException: msg" becomes "java.sql.SQLException". */
    public String exceptionType() {
        int colon = rootCause.indexOf(": ");
        return colon < 0 ? rootCause : rootCause.substring(0, colon);
    }

    public String id() {
        return id;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String rootCause() {
        return rootCause;
    }

    public String fix() {
        return fix;
    }

    public int worked() {
        return worked;
    }

    public int failed() {
        return failed;
    }

    public String savedOn() {
        return savedOn;
    }
}
