package com.tracelens.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds saved fixes for a new error.
 *
 * <ol>
 *   <li>Exact match: same fingerprint, so it is the same bug. Similarity 100%.</li>
 *   <li>Similar match: same exception type, and the root cause messages share
 *       enough words (Jaccard similarity of at least 60%).</li>
 * </ol>
 * Results are sorted by similarity, then by how often the fix worked.
 */
public class SimilarityMatcher {

    static final double MIN_SIMILARITY = 0.6;
    static final int MAX_SUGGESTIONS = 3;

    /** Words that appear in many errors and say nothing about this one. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "was", "be", "of", "to", "in", "on", "at", "for", "by",
            "and", "or", "with", "cannot", "invoke", "because",
            "java", "javax", "lang", "com", "org", "net", "io", "util");

    public List<Suggestion> suggest(String fingerprint, String exceptionType, String rootCause,
                                    List<Solution> solutions) {
        Set<String> newWords = words(rootCause);
        List<Suggestion> found = new ArrayList<>();

        for (Solution solution : solutions) {
            if (solution.fingerprint().equals(fingerprint)) {
                found.add(new Suggestion(solution, 1.0));
            } else if (solution.exceptionType().equals(exceptionType)) {
                Set<String> savedWords = words(solution.rootCause());
                // Only the exception name in common is not enough to call two errors similar
                if (newWords.size() >= 2 && savedWords.size() >= 2) {
                    double similarity = jaccard(newWords, savedWords);
                    if (similarity >= MIN_SIMILARITY) {
                        found.add(new Suggestion(solution, similarity));
                    }
                }
            }
        }

        Comparator<Suggestion> bySimilarity = Comparator.comparingDouble(Suggestion::similarity);
        Comparator<Suggestion> byScore = Comparator.comparingInt(s -> s.solution().score());
        found.sort(bySimilarity.thenComparing(byScore).reversed()); // both highest first

        return found.size() > MAX_SUGGESTIONS ? found.subList(0, MAX_SUGGESTIONS) : found;
    }

    /**
     * Splits text into a set of lower-case words, skipping stop words and pure numbers.
     * "java.sql.SQLException: Communications link failure" becomes
     * {sql, sqlexception, communications, link, failure}.
     */
    static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        for (String word : text.toLowerCase().split("[^a-z0-9]+")) {
            if (word.length() >= 2 && !STOP_WORDS.contains(word) && !word.matches("\\d+")) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Jaccard similarity = words in both / words in either.
     * {a, b, c} and {b, c, d}: common {b, c} = 2, all {a, b, c, d} = 4, so 2 / 4 = 0.5.
     */
    static double jaccard(Set<String> first, Set<String> second) {
        Set<String> common = new HashSet<>(first);
        common.retainAll(second);       // intersection
        Set<String> all = new HashSet<>(first);
        all.addAll(second);             // union
        return all.isEmpty() ? 0 : (double) common.size() / all.size();
    }
}
