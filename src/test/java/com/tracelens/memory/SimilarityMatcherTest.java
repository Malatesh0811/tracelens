package com.tracelens.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityMatcherTest {

    private final SimilarityMatcher matcher = new SimilarityMatcher();

    private static final String SQL = "java.sql.SQLException";
    private static final String NPE = "java.lang.NullPointerException";

    private static Solution fix(String id, String fingerprint, String rootCause, int worked, int failed) {
        return new Solution(id, fingerprint, rootCause, "fix " + id, worked, failed, "2026-11-12");
    }

    @Test
    void wordsAreLowerCaseWithoutStopWordsOrNumbers() {
        assertEquals(Set.of("sql", "sqlexception", "communications", "link", "failure", "port"),
                SimilarityMatcher.words("java.sql.SQLException: Communications link failure on port 3306"));
    }

    @Test
    void jaccardIsCommonWordsDividedByAllWords() {
        assertEquals(0.5, SimilarityMatcher.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d")));
        assertEquals(1.0, SimilarityMatcher.jaccard(Set.of("a", "b"), Set.of("b", "a")));
        assertEquals(0.0, SimilarityMatcher.jaccard(Set.of("a"), Set.of("b")));
        assertEquals(0.0, SimilarityMatcher.jaccard(Set.of(), Set.of()));
    }

    @Test
    void sameFingerprintIsAnExactMatch() {
        Solution saved = fix("S1", "fp-1", SQL + ": anything", 1, 0);

        List<Suggestion> result = matcher.suggest("fp-1", SQL, SQL + ": something else", List.of(saved));

        assertEquals(1, result.size());
        assertEquals(100, result.get(0).percent());
    }

    @Test
    void similarDatabaseErrorIsSuggested() {
        Solution saved = fix("S1", "fp-old", SQL + ": Communications link failure - Connection refused", 1, 0);

        List<Suggestion> result = matcher.suggest("fp-new", SQL,
                SQL + ": Communications link failure - Connection timed out", List.of(saved));

        // {sql, sqlexception, communications, link, failure, connection} common = 6, all = 9
        assertEquals(1, result.size());
        assertEquals(67, result.get(0).percent());
    }

    @Test
    void nullPointerOnADifferentObjectIsNotSuggested() {
        Solution saved = fix("S1", "fp-user",
                NPE + ": Cannot invoke \"com.shop.model.User.getEmail()\" because \"user\" is null", 1, 0);

        List<Suggestion> result = matcher.suggest("fp-order", NPE,
                NPE + ": Cannot invoke \"com.shop.model.Order.getId()\" because \"order\" is null", List.of(saved));

        assertTrue(result.isEmpty(), "only 50% similar, below the 60% limit");
    }

    @Test
    void differentExceptionTypeIsNeverSuggested() {
        Solution saved = fix("S1", "fp-a", "java.io.IOException: Connection refused by server", 1, 0);

        assertTrue(matcher.suggest("fp-b", SQL, SQL + ": Connection refused by server", List.of(saved)).isEmpty());
    }

    @Test
    void sharingOnlyTheExceptionNameIsNotEnough() {
        Solution saved = fix("S1", "fp-a", NPE, 1, 0);

        assertTrue(matcher.suggest("fp-b", NPE, NPE, List.of(saved)).isEmpty());
    }

    @Test
    void sortedBySimilarityThenByHowOftenItWorked() {
        String message = SQL + ": Communications link failure - Connection refused";
        Solution similarButTrusted = fix("S1", "fp-x", SQL + ": Communications link failure - Connection timed out", 9, 0);
        Solution exactRarely = fix("S2", "fp-new", message, 1, 0);
        Solution exactOften = fix("S3", "fp-new", message, 4, 1);

        List<Suggestion> result = matcher.suggest("fp-new", SQL, message,
                List.of(similarButTrusted, exactRarely, exactOften));

        assertEquals(List.of("S3", "S2", "S1"), result.stream().map(s -> s.solution().id()).toList());
    }

    @Test
    void atMostThreeSuggestions() {
        List<Solution> many = List.of(fix("S1", "fp", SQL, 1, 0), fix("S2", "fp", SQL, 1, 0),
                fix("S3", "fp", SQL, 1, 0), fix("S4", "fp", SQL, 1, 0));

        assertEquals(SimilarityMatcher.MAX_SUGGESTIONS, matcher.suggest("fp", SQL, SQL, many).size());
    }
}
