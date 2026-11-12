package com.tracelens.ai;

import com.tracelens.analysis.ErrorGroup;
import com.tracelens.analysis.ErrorGrouper;
import com.tracelens.memory.Solution;
import com.tracelens.memory.Suggestion;
import com.tracelens.parser.Cause;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiExplainerTest {

    /** A fake AI: remembers what it was asked and returns a fixed answer. */
    static class FakeLlm implements LlmClient {
        String instructions;
        String question;

        @Override
        public String ask(String instructions, String question) {
            this.instructions = instructions;
            this.question = question;
            return "  Start the database.  ";
        }
    }

    private static final Frame LIBRARY = new Frame("com.mysql.cj.jdbc.ConnectionImpl", "createNewIO", "ConnectionImpl.java", 836);
    private static final Frame MINE = new Frame("com.shop.config.DbConfig", "connect", "DbConfig.java", 18);

    private static ErrorGroup dbError(String message) {
        ErrorReport report = new ErrorReport(List.of(
                new Cause("java.lang.IllegalStateException", "Failed to start", List.of(MINE)),
                new Cause("java.sql.SQLException", message, List.of(LIBRARY, MINE))));
        return new ErrorGrouper().group(List.of(report, report), null).get(0);
    }

    @Test
    void sendsWhatTraceLensFoundAndReturnsTheAnswer() throws LlmException {
        FakeLlm fake = new FakeLlm();
        Solution fix = new Solution("S1", "fp", "java.sql.SQLException: x", "net start mysql80", 2, 0, "2026-11-12");

        AiExplainer.Result result = new AiExplainer(fake)
                .explain(dbError("Communications link failure"), List.of(new Suggestion(fix, 0.67)), null);

        assertEquals("Start the database.", result.answer());
        assertEquals(AiExplainer.INSTRUCTIONS, fake.instructions);
        assertTrue(fake.question.contains("Root cause: java.sql.SQLException: Communications link failure"));
        assertTrue(fake.question.contains("IllegalStateException -> SQLException"));
        assertTrue(fake.question.contains("Happened 2 times"));
        assertTrue(fake.question.contains("com.shop.config.DbConfig - DbConfig.java:18  connect()"));
        assertTrue(fake.question.contains("at com.mysql.cj.jdbc.ConnectionImpl.createNewIO"));
        assertTrue(fake.question.contains("\"net start mysql80\" (67% match, worked 2 times"));
    }

    @Test
    void secretsNeverReachTheAi() throws LlmException {
        FakeLlm fake = new FakeLlm();

        AiExplainer.Result result = new AiExplainer(fake)
                .explain(dbError("Access denied for jdbc:mysql://prod-db:3306/shop password=hunter2"), List.of(), null);

        assertEquals(2, result.secretsMasked());
        assertFalse(fake.question.contains("hunter2"));
        assertFalse(fake.question.contains("prod-db"));
    }

    @Test
    void saysWhenThereAreNoSavedFixes() throws LlmException {
        FakeLlm fake = new FakeLlm();

        new AiExplainer(fake).explain(dbError("x"), List.of(), null);

        assertTrue(fake.question.contains("none yet"));
    }

    @Test
    void aiFailurePassesThrough() {
        LlmClient broken = (instructions, question) -> {
            throw new LlmException("The API key was rejected.");
        };

        assertThrows(LlmException.class, () -> new AiExplainer(broken).explain(dbError("x"), List.of(), null));
    }
}
