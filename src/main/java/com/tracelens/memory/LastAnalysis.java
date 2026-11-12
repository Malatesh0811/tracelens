package com.tracelens.memory;

import com.tracelens.analysis.ErrorGroup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Remembers which error was E1, E2... in the last analyze run,
 * so "tracelens solve E1 ..." knows which error the fix belongs to.
 * File format: one line per error, "E1 tab fingerprint tab root cause".
 */
public final class LastAnalysis {

    /** One error from the last analysis. */
    public record Entry(String id, String fingerprint, String rootCause) {
    }

    private LastAnalysis() {
    }

    public static void save(Path file, List<ErrorGroup> groups) throws IOException {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            ErrorGroup group = groups.get(i);
            lines.add("E" + (i + 1) + "\t" + group.fingerprint() + "\t"
                    + group.example().rootCause().display().replaceAll("[\\t\\r\\n]+", " "));
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** @return the entry for "E1" (any case), or empty when there is no such error */
    public static Optional<Entry> find(Path file, String id) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(id)) {
                return Optional.of(new Entry(parts[0], parts[1], parts[2]));
            }
        }
        return Optional.empty();
    }
}
