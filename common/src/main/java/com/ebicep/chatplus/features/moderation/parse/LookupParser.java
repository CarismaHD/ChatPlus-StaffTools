package com.ebicep.chatplus.features.moderation.parse;

import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import com.ebicep.chatplus.features.moderation.profile.Rules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** Parses a player lookup response using the profile's lookup patterns. */
public final class LookupParser implements ResponseParser {
    private final CompiledProfile p;

    public LookupParser(CompiledProfile profile) {
        this.p = profile;
    }

    @Override
    public boolean isStart(String raw) {
        return p.lookupStart.matches(p.clean(raw));
    }

    @Override
    public boolean accepts(String raw) {
        String text = p.clean(raw);
        // Chat from other players can arrive in the middle of the output and must never be taken for part of it.
        if (p.chatLines.matches(text)) {
            return false;
        }
        return p.lookupCount.matches(text) || p.lookupSection.matches(text) || p.lookupEntry.matches(text) || text.startsWith(" ");
    }

    @Override
    public LookupResult parse(List<CapturedLine> lines) {
        String name = "";
        int warnings = 0;
        int notes = 0;
        int bans = 0;
        List<LookupResult.Entry> warningEntries = new ArrayList<>();
        List<LookupResult.Entry> noteEntries = new ArrayList<>();
        Map<String, List<String>> raw = new LinkedHashMap<>();
        List<LookupResult.Entry> section = null;
        List<String> rawSection = null;

        for (CapturedLine line : lines) {
            String text = p.clean(line.text());
            Matcher m = p.lookupStart.match(text);
            if (m != null) {
                String n = Rules.group(m, "name");
                name = n != null ? n : "";
                continue;
            }
            m = p.lookupCount.match(text);
            if (m != null) {
                String kind = Rules.group(m, "kind");
                Integer n = Rules.intGroup(m, "count");
                if (kind != null && n != null) {
                    if (kind.equals(p.lookup.warningsCount)) {
                        warnings = n;
                    } else if (kind.equals(p.lookup.notesCount)) {
                        notes = n;
                    } else if (kind.equals(p.lookup.bansCount)) {
                        bans = n;
                    }
                }
                continue;
            }
            m = p.lookupSection.match(text);
            if (m != null) {
                String header = Rules.group(m, "header");
                header = header != null ? header : text;
                rawSection = null;
                section = null;
                if (header.equals(p.lookup.warningsSection)) {
                    section = warningEntries;
                } else if (header.equals(p.lookup.notesSection)) {
                    section = noteEntries;
                } else {
                    rawSection = raw.computeIfAbsent(header, h -> new ArrayList<>());
                }
                continue;
            }
            if (rawSection != null) {
                rawSection.add(text.strip());
                continue;
            }
            if (section == null) {
                continue;
            }
            m = p.lookupEntry.match(text);
            Integer id = m != null ? Rules.intGroup(m, "id") : null;
            if (id != null) {
                String body = Rules.group(m, "text");
                section.add(new LookupResult.Entry(id, Rules.group(m, "status"), Rules.group(m, "time"), Rules.group(m, "staff"),
                        body != null ? body : ""));
            } else if (!section.isEmpty()) {
                // Any other line continues the previous entry.
                LookupResult.Entry last = section.remove(section.size() - 1);
                section.add(new LookupResult.Entry(last.id(), last.status(), last.timestamp(), last.staff(), last.text() + " " + text.strip()));
            }
        }
        return new LookupResult(name, warnings, notes, bans, warningEntries, noteEntries, raw);
    }
}
