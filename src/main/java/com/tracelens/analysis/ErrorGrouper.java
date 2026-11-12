package com.tracelens.analysis;

import com.tracelens.parser.ErrorReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups copies of the same error together.
 *
 * For each error: build its fingerprint, look it up in a HashMap.
 *   - found     -> add to that group (count + 1)
 *   - not found -> create a new group
 * Total time O(n) for n errors, because each HashMap lookup is O(1) on average.
 */
public class ErrorGrouper {

    /** @return groups, most frequent first; groups with equal counts keep their log order */
    public List<ErrorGroup> group(List<ErrorReport> reports, String basePackage) {
        Map<String, ErrorGroup> byFingerprint = new HashMap<>();
        List<ErrorGroup> groups = new ArrayList<>(); // HashMap has no order, so this list remembers it

        for (ErrorReport report : reports) {
            String fingerprint = Fingerprint.of(report, basePackage);
            ErrorGroup group = byFingerprint.get(fingerprint);
            if (group == null) {
                group = new ErrorGroup(fingerprint, report);
                byFingerprint.put(fingerprint, group);
                groups.add(group);
            } else {
                group.add(report);
            }
        }

        // List.sort is stable: equal counts stay in the order they appeared in the log
        groups.sort(Comparator.comparingInt(ErrorGroup::count).reversed());
        return groups;
    }
}
