package com.ebicep.chatplus.features.moderation.parse;

import java.util.List;
import java.util.Map;

/**
 * Parsed output of the server's lookup command. Some servers print only a count of active bans, without details.
 * Sections with an unknown header are kept as raw lines under that header.
 */
public record LookupResult(String name, int warnings, int notes, int activeBans, List<Entry> warningEntries,
                           List<Entry> noteEntries, Map<String, List<String>> rawSections) {
    /** One warning or note. Status is null for notes. */
    public record Entry(int id, String status, String timestamp, String staff, String text) {
    }
}