package com.tracelens.rules;

import com.tracelens.parser.Cause;

import java.util.List;

/**
 * A rule that matches by exception name only, e.g. NullPointerException.
 * Most rules only differ in their text, so one class with different data covers them all.
 */
public class SimpleRule implements Rule {

    private final List<String> exceptionNames;
    private final String why;
    private final List<String> fixes;

    public SimpleRule(List<String> exceptionNames, String why, List<String> fixes) {
        this.exceptionNames = List.copyOf(exceptionNames);
        this.why = why;
        this.fixes = List.copyOf(fixes);
    }

    @Override
    public boolean matches(Cause rootCause) {
        return exceptionNames.contains(rootCause.simpleType());
    }

    @Override
    public String why() {
        return why;
    }

    @Override
    public List<String> fixes() {
        return fixes;
    }
}
