package com.ebicep.chatplus.features.moderation.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** A compiled list of patterns from the profile, each matched against the whole text. */
public final class Rules {
    private final List<Pattern> patterns = new ArrayList<>();

    /**
     * @param where  name used in error messages, such as {@code chat.join}
     * @param errors receives one message per pattern that does not compile
     */
    public Rules(String where, List<String> sources, List<String> errors) {
        if (sources == null) {
            return;
        }
        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            if (source == null || source.isEmpty()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(source));
            } catch (PatternSyntaxException e) {
                errors.add(where + "[" + i + "]: " + e.getDescription());
            }
        }
    }

    /** Returns a matcher for the first pattern matching the whole text, or null. */
    public Matcher match(String text) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.matches()) {
                return m;
            }
        }
        return null;
    }

    public boolean matches(String text) {
        return match(text) != null;
    }

    /** Removes the first pattern found at the start of the text. */
    public String stripStart(String text) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.lookingAt()) {
                return text.substring(m.end());
            }
        }
        return text;
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    /** A named group's value, or null when the pattern has no such group or it did not take part. */
    public static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A named group as a number, or null. */
    public static Integer intGroup(Matcher m, String name) {
        String v = group(m, name);
        try {
            return v != null ? Integer.valueOf(v.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double doubleGroup(Matcher m, String name) {
        String v = group(m, name);
        try {
            return v != null ? Double.valueOf(v.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
