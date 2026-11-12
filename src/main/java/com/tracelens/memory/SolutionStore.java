package com.tracelens.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Saves fixes in a plain text file, one fix per line, columns separated by tabs:
 * <pre>
 *   S1  fingerprint  root cause  fix  worked  failed  saved on
 * </pre>
 * Plain text so people can read it, and commit it to git to share fixes with their team.
 */
public class SolutionStore {

    private static final String HEADER =
            "# TraceLens saved fixes. Tab separated: id, fingerprint, root cause, fix, worked, failed, saved on";
    private static final int COLUMNS = 7;

    private final Path file;
    private final List<Solution> solutions = new ArrayList<>();   // keeps file order
    private final Map<String, Solution> byId = new HashMap<>();   // "S3" -> solution in O(1)

    /** Loads the file if it exists; a missing file just means no fixes saved yet. */
    public SolutionStore(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length != COLUMNS) {
                throw new IOException("Line " + (i + 1) + " of " + file + " is damaged: expected "
                        + COLUMNS + " columns, found " + parts.length);
            }
            try {
                add(new Solution(parts[0], parts[1], parts[2], parts[3],
                        Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), parts[6]));
            } catch (NumberFormatException e) {
                throw new IOException("Line " + (i + 1) + " of " + file + " is damaged: counts must be numbers");
            }
        }
    }

    public List<Solution> all() {
        return List.copyOf(solutions);
    }

    /** Ids are not case sensitive: "s1" finds "S1". */
    public Optional<Solution> findById(String id) {
        return Optional.ofNullable(byId.get(id.toUpperCase()));
    }

    /**
     * Saves a fix for an error. If the same fix was already saved for the same error,
     * it is not saved twice; it is counted as having worked once more instead.
     */
    public Solution save(String fingerprint, String rootCause, String fix, LocalDate today) throws IOException {
        String cleanFix = clean(fix);
        for (Solution existing : solutions) {
            if (existing.fingerprint().equals(fingerprint) && existing.fix().equalsIgnoreCase(cleanFix)) {
                existing.markWorked();
                write();
                return existing;
            }
        }
        Solution solution = new Solution("S" + (solutions.size() + 1), clean(fingerprint), clean(rootCause),
                cleanFix, 1, 0, today.toString());
        add(solution);
        write();
        return solution;
    }

    /** Records whether a suggested fix worked this time. */
    public void feedback(Solution solution, boolean worked) throws IOException {
        if (worked) {
            solution.markWorked();
        } else {
            solution.markFailed();
        }
        write();
    }

    private void add(Solution solution) {
        solutions.add(solution);
        byId.put(solution.id().toUpperCase(), solution);
    }

    private void write() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Solution s : solutions) {
            lines.add(String.join("\t", s.id(), s.fingerprint(), s.rootCause(), s.fix(),
                    String.valueOf(s.worked()), String.valueOf(s.failed()), s.savedOn()));
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Tabs and line breaks would break the file format, so they become spaces. */
    private static String clean(String text) {
        return text.replaceAll("[\\t\\r\\n]+", " ").trim();
    }
}
