package com.tracelens.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides secrets before any text leaves the computer.
 *
 * Logs often contain passwords, API keys and database addresses. This runs every
 * text sent to the AI through a list of patterns and replaces what they find with ****.
 */
public class SecretMasker {

    /** The text with secrets replaced, and how many were replaced. */
    public record Masked(String text, int count) {
    }

    /** One kind of secret: what it looks like, and what to put in its place. */
    private record SecretPattern(Pattern pattern, String replacement) {
    }

    /** Order matters: a whole JDBC URL is hidden first, so its parts are not counted again below. */
    private static final List<SecretPattern> PATTERNS = List.of(
            // jdbc:mysql://prod-db.company.com:3306/shop?password=x -> jdbc:mysql://****
            new SecretPattern(Pattern.compile("(jdbc:[a-z0-9]+:(?://)?)[^\\s\"',;]+"), "$1****"),
            // password=abc, "token": "abc", api_key: abc  -> keep the name, hide the value
            new SecretPattern(Pattern.compile(
                    "(?i)((?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret)"
                            + "\"?\\s*[=:]\\s*\"?)[^\\s\"',;&]+"), "$1****"),
            // Authorization: Bearer eyJhbGci...
            new SecretPattern(Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1****"),
            // user:password@host in any URL
            new SecretPattern(Pattern.compile("([a-zA-Z][a-zA-Z0-9+.-]*://)[^\\s/:@]+:[^\\s/@]+@"), "$1****:****@"),
            // API keys that look like sk-ant-..., sk-...
            new SecretPattern(Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}"), "sk-****"),
            // AWS access key ids
            new SecretPattern(Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"), "AKIA****"),
            // email addresses
            new SecretPattern(Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"), "****@****"));

    public Masked mask(String text) {
        String result = text;
        int count = 0;
        for (SecretPattern secret : PATTERNS) {
            Matcher matcher = secret.pattern().matcher(result);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                count++;
                matcher.appendReplacement(out, secret.replacement());
            }
            matcher.appendTail(out);
            result = out.toString();
        }
        return new Masked(result, count);
    }
}
