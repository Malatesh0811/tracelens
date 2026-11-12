package com.tracelens.ai;

import com.tracelens.analysis.ErrorGroup;
import com.tracelens.memory.Suggestion;
import com.tracelens.parser.ErrorReport;
import com.tracelens.parser.Frame;

import java.util.List;

/**
 * Optional AI step: asks an LLM to explain one error in plain English.
 *
 * It sends only what TraceLens already found (root cause, chain, your code lines,
 * your past fixes), never the whole log, and masks secrets first.
 */
public class AiExplainer {

    /** How many stack trace lines of the root cause are sent as context. */
    static final int MAX_STACK_LINES = 5;

    static final String INSTRUCTIONS = """
            You help junior Java developers understand why their program crashed.
            Using only the details given, explain the most likely reason for this error and how to fix it.
            If one of the developer's saved fixes fits, say so first.
            Keep it under 120 words: two or three plain sentences, then at most 3 numbered steps.
            If the details are not enough to be sure, say what to check. Do not use markdown headings.""";

    /** The AI's answer, and how many secrets were hidden from it. */
    public record Result(String answer, int secretsMasked) {
    }

    private final LlmClient llm;
    private final SecretMasker masker = new SecretMasker();

    public AiExplainer(LlmClient llm) {
        this.llm = llm;
    }

    public Result explain(ErrorGroup group, List<Suggestion> pastFixes, String basePackage) throws LlmException {
        SecretMasker.Masked safe = masker.mask(question(group, pastFixes, basePackage));
        String answer = llm.ask(INSTRUCTIONS, safe.text());
        return new Result(answer.trim(), safe.count());
    }

    /** The question in plain text. Package-private so tests can check exactly what is sent. */
    static String question(ErrorGroup group, List<Suggestion> pastFixes, String basePackage) {
        ErrorReport report = group.example();
        StringBuilder q = new StringBuilder("A Java program crashed. TraceLens found this in the log:\n\n");

        q.append("Root cause: ").append(report.rootCause().display()).append('\n');
        if (report.causes().size() > 1) {
            q.append("Exception chain (top to root): ").append(String.join(" -> ", report.causeChain())).append('\n');
        }
        q.append("Happened ").append(group.count()).append(group.count() == 1 ? " time" : " times")
         .append(" in this log.\n");

        q.append("\nThe developer's own code involved:\n");
        List<Frame> yours = report.yourCodeFrames(basePackage);
        if (yours.isEmpty()) {
            q.append("  (none found; the error happened inside libraries)\n");
        }
        for (int i = 0; i < yours.size() && i < MAX_STACK_LINES; i++) {
            q.append("  ").append(yours.get(i).className()).append(" - ").append(yours.get(i).display()).append('\n');
        }

        q.append("\nTop of the root cause stack trace:\n");
        List<Frame> frames = report.rootCause().frames();
        for (int i = 0; i < frames.size() && i < MAX_STACK_LINES; i++) {
            q.append("  at ").append(frames.get(i).className()).append('.').append(frames.get(i).methodName())
             .append(" (").append(frames.get(i).display()).append(")\n");
        }

        q.append("\nFixes this developer saved for this or similar errors:\n");
        if (pastFixes.isEmpty()) {
            q.append("  none yet\n");
        }
        for (Suggestion s : pastFixes) {
            q.append("  - \"").append(s.solution().fix()).append("\" (").append(s.percent())
             .append("% match, worked ").append(s.solution().worked()).append(" times, failed ")
             .append(s.solution().failed()).append(")\n");
        }
        return q.toString();
    }
}
