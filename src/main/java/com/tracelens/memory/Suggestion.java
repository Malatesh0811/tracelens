package com.tracelens.memory;

/**
 * A saved fix offered for a new error.
 *
 * @param solution   the saved fix
 * @param similarity 1.0 = exactly the same error, 0.6 to 0.99 = similar error
 */
public record Suggestion(Solution solution, double similarity) {

    /** 0.666 becomes 67. */
    public int percent() {
        return (int) Math.round(similarity * 100);
    }
}
