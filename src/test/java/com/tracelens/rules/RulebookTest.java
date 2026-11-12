package com.tracelens.rules;

import com.tracelens.parser.Cause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulebookTest {

    private final Rulebook rulebook = Rulebook.defaultRules();

    private static Cause cause(String type, String message) {
        return new Cause(type, message, List.of());
    }

    /** One row per built-in rule: exception, message, a word the explanation must contain. */
    @ParameterizedTest(name = "{0} -> \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "java.sql.SQLException                     | Communications link failure      | database",
            "java.net.BindException                    | Address already in use: bind     | port",
            "java.lang.NullPointerException            | ''                               | null",
            "java.lang.ArrayIndexOutOfBoundsException  | Index 5 out of bounds            | index",
            "java.lang.NumberFormatException           | For input string: \"2a\"         | number",
            "java.lang.ClassNotFoundException          | com.mysql.cj.jdbc.Driver         | missing",
            "java.lang.ClassCastException              | String cannot be cast to Integer | cast",
            "java.lang.ArithmeticException             | / by zero                        | zero",
            "java.io.FileNotFoundException             | config.txt                       | file",
            "java.lang.OutOfMemoryError                | Java heap space                  | memory",
    })
    void everyBuiltInRuleExplainsItsError(String type, String message, String expectedWord) {
        Optional<Rule> rule = rulebook.find(cause(type, message));

        assertTrue(rule.isPresent(), "no rule for " + type);
        assertTrue(rule.get().why().toLowerCase().contains(expectedWord), rule.get().why());
        assertFalse(rule.get().fixes().isEmpty());
    }

    @Test
    void hasTenRules() {
        assertEquals(10, rulebook.size());
    }

    @Test
    void sqlErrorThatIsNotAConnectionProblemIsNotExplainedAsOne() {
        assertTrue(rulebook.find(cause("java.sql.SQLSyntaxErrorException", "You have an error in your SQL syntax")).isEmpty());
    }

    @Test
    void portInUseIsFoundByMessageFromAnyLibrary() {
        Cause springError = cause("org.springframework.boot.web.server.PortInUseException", "Port 8080 is already in use");

        assertTrue(rulebook.find(springError).get() instanceof PortInUseRule);
    }

    @Test
    void unknownErrorHasNoRule() {
        assertTrue(rulebook.find(cause("com.shop.OrderRejectedException", "out of stock")).isEmpty());
    }

    @Test
    void firstMatchingRuleWins() {
        Rule first = new SimpleRule(List.of("IllegalStateException"), "first", List.of("a"));
        Rule second = new SimpleRule(List.of("IllegalStateException"), "second", List.of("b"));
        Rulebook book = new Rulebook(List.of(first, second));

        assertEquals("first", book.find(cause("java.lang.IllegalStateException", "")).get().why());
    }
}
