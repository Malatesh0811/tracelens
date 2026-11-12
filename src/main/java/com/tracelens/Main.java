package com.tracelens;

/**
 * Entry point of TraceLens.
 *
 * Phase 0: only prints the version and usage, to prove the project builds and runs.
 * Real commands (analyze, solve, history) are added in later phases.
 */
public class Main {

    public static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        System.out.println(usage());
    }

    /** Kept separate from main() so it can be unit tested without capturing console output. */
    static String usage() {
        return "TraceLens " + VERSION + System.lineSeparator()
                + "Usage:" + System.lineSeparator()
                + "  tracelens analyze <logfile>     find the root cause of a crash" + System.lineSeparator()
                + "  tracelens solve <id> \"<fix>\"    remember how you fixed an error" + System.lineSeparator()
                + "  tracelens history               list errors you solved before";
    }
}
