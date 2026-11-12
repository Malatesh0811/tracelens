package com.tracelens.parser;

import java.util.List;

/**
 * One "at ..." line of a stack trace, e.g.
 * {@code at com.shop.config.DbConfig.connect(DbConfig.java:18)}.
 *
 * A record is an immutable data class: its fields are private and final,
 * and Java generates the constructor, getters, equals, hashCode and toString.
 *
 * @param className  fully qualified class, e.g. "com.shop.config.DbConfig"
 * @param methodName e.g. "connect"
 * @param fileName   e.g. "DbConfig.java", or null when unknown ("Native Method")
 * @param lineNumber e.g. 18, or -1 when unknown
 */
public record Frame(String className, String methodName, String fileName, int lineNumber) {

    /** Packages that belong to Java itself or to common libraries, not to the user. */
    private static final List<String> LIBRARY_PREFIXES = List.of(
            "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.",
            "org.", "kotlin.", "scala.", "io.netty.", "reactor.", "net.bytebuddy.",
            "ch.qos.", "junit.", "com.google.", "com.fasterxml.", "com.mysql.",
            "com.zaxxer.", "com.mongodb.", "com.intellij.");

    /**
     * Is this frame from the user's own code?
     *
     * @param basePackage the user's package (e.g. "com.shop"); when null,
     *                    any class that is not from a known library counts
     */
    public boolean isYourCode(String basePackage) {
        if (basePackage != null) {
            return className.startsWith(basePackage);
        }
        for (String prefix : LIBRARY_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /** Short form for printing, e.g. "DbConfig.java:18  connect()". */
    public String display() {
        if (fileName == null) {
            return className + "." + methodName + "()  (line unknown)";
        }
        String location = lineNumber >= 0 ? fileName + ":" + lineNumber : fileName;
        return location + "  " + methodName + "()";
    }
}
