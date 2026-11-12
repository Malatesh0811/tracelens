package com.tracelens.parser;

import java.util.List;

/**
 * One exception in a stack trace: its type, its message and its frames.
 * A crash usually has several of these linked by "Caused by:".
 *
 * @param type    fully qualified exception class, e.g. "java.sql.SQLException"
 * @param message the text after the colon, or "" when there is none
 * @param frames  the "at ..." lines under this exception, top first
 */
public record Cause(String type, String message, List<Frame> frames) {

    /** Compact constructor: runs before the fields are set, used for validation. */
    public Cause {
        message = message == null ? "" : message;
        frames = List.copyOf(frames); // defensive copy: nobody can change our list later
    }

    /** "java.sql.SQLException" becomes "SQLException". */
    public String simpleType() {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    /** "java.sql.SQLException: Communications link failure". */
    public String display() {
        return message.isEmpty() ? type : type + ": " + message;
    }
}
