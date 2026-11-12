package com.tracelens.rules;

import com.tracelens.parser.Cause;

import java.util.List;
import java.util.Optional;

/**
 * The list of known errors. Finds the first rule that explains a root cause.
 * Special rules come first, so they win over general ones.
 */
public class Rulebook {

    private final List<Rule> rules;

    public Rulebook(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** @return the matching rule, or an empty Optional when no rule knows this error */
    public Optional<Rule> find(Cause rootCause) {
        for (Rule rule : rules) {
            if (rule.matches(rootCause)) {   // polymorphism: each rule's own matches() runs
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return rules.size();
    }

    /** The 10 built-in rules for errors freshers meet most often. */
    public static Rulebook defaultRules() {
        return new Rulebook(List.of(
                new DatabaseConnectionRule(),
                new PortInUseRule(),
                new SimpleRule(List.of("NullPointerException"),
                        "You used an object that is null (it was never given a value).",
                        List.of("Read the message: Java names the variable that is null",
                                "Find where that variable gets its value (a method returning null, a missing init)",
                                "Add a null check, or return Optional instead of null")),
                new SimpleRule(List.of("ArrayIndexOutOfBoundsException", "IndexOutOfBoundsException",
                                "StringIndexOutOfBoundsException"),
                        "You used an index outside the list or array (valid: 0 to size-1).",
                        List.of("Check loop conditions: use i < size, not i <= size",
                                "Check the list is not empty before get(0)",
                                "Print the size and the index just before the failing line")),
                new SimpleRule(List.of("NumberFormatException"),
                        "Some text could not be turned into a number.",
                        List.of("Look at the input in the message: empty, spaces or letters?",
                                "Use trim() and validate the text before Integer.parseInt",
                                "Catch the exception and show the user a clear message")),
                new SimpleRule(List.of("ClassNotFoundException", "NoClassDefFoundError"),
                        "A class your code needs is missing when the app runs.",
                        List.of("Check the library is a dependency in pom.xml",
                                "Rebuild from scratch: mvnw clean package",
                                "Check the package and class name spelling")),
                new SimpleRule(List.of("ClassCastException"),
                        "An object was cast to a type it is not.",
                        List.of("Check the real type with instanceof before casting",
                                "Use generics (List<String>) so the compiler checks types",
                                "Check what is put into the collection or map")),
                new SimpleRule(List.of("ArithmeticException"),
                        "A math error, usually dividing by zero.",
                        List.of("Check the divisor is not zero before dividing",
                                "Handle empty lists when calculating averages")),
                new SimpleRule(List.of("FileNotFoundException", "NoSuchFileException"),
                        "The file does not exist at that path, or cannot be opened.",
                        List.of("Print the absolute path: relative paths start from the folder you ran the app in",
                                "Check spelling and upper/lower case of the file name",
                                "Check the file is not open in another program and you have permission")),
                new SimpleRule(List.of("OutOfMemoryError"),
                        "The app used all the memory Java was allowed to use.",
                        List.of("Look for lists or maps that keep growing and are never cleared",
                                "Load big files in parts instead of all at once",
                                "If it really needs more, raise the limit: java -Xmx1g ..."))));
    }
}
