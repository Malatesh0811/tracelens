package com.tracelens.ai;

/**
 * Something that can answer a question: the real AI service, or a fake one in tests.
 *
 * The rest of TraceLens only knows this interface, so the AI provider can be swapped
 * (or faked) without changing any other class.
 */
public interface LlmClient {

    /**
     * @param instructions how the AI should behave (the "system prompt")
     * @param question     what we ask, already free of secrets
     * @return the AI's answer as plain text
     * @throws LlmException with a message that can be shown to the user
     */
    String ask(String instructions, String question) throws LlmException;
}
