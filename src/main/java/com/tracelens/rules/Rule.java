package com.tracelens.rules;

import com.tracelens.parser.Cause;

import java.util.List;

/**
 * Explains one kind of error in plain English.
 *
 * Each rule decides for itself whether it fits an error (matches) and what to say.
 * The Rulebook loops over a List of Rule without knowing which class each one is:
 * that is polymorphism.
 */
public interface Rule {

    /** Does this rule explain the given root cause? */
    boolean matches(Cause rootCause);

    /** One sentence: what went wrong. */
    String why();

    /** Things to try, most likely fix first. */
    List<String> fixes();
}
