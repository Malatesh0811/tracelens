package com.tracelens.ai;

/**
 * The AI step failed. The message is written for the user ("The API key was rejected...").
 * Checked exception: callers must decide what to do, because the AI step is optional.
 */
public class LlmException extends Exception {

    public LlmException(String message) {
        super(message);
    }
}
