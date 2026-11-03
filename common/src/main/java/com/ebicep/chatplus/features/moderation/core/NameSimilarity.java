package com.ebicep.chatplus.features.moderation.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Finds usernames that are easy to confuse with another, so a punishment isn't sent to the wrong player. It only warns;
 * it never blocks anything.
 */
public final class NameSimilarity {
    private NameSimilarity() {
    }

    /**
     * True when two different names look alike: the same apart from case, underscores, or 0/O, 1/l/I and 5/S, or one
     * character apart. Very short names are left out, since almost everything is one character from them.
     */
    public static boolean confusable(String a, String b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        if (canonical(a).equals(canonical(b))) {
            return true;
        }
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        return Math.min(x.length(), y.length()) >= 4 && oneEditApart(x, y);
    }

    /** The names from others that look like name, in the order given. */
    public static List<String> lookAlikes(String name, Collection<String> others) {
        List<String> result = new ArrayList<>();
        for (String other : others) {
            if (confusable(name, other) && !result.contains(other)) {
                result.add(other);
            }
        }
        return result;
    }

    private static String canonical(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case '_' -> {
                }
                case '0' -> sb.append('o');
                case '1', 'i' -> sb.append('l');
                case '5' -> sb.append('s');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean oneEditApart(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        if (a.length() == b.length()) {
            int diff = 0;
            for (int i = 0; i < a.length(); i++) {
                if (a.charAt(i) != b.charAt(i) && ++diff > 1) {
                    return false;
                }
            }
            return diff == 1;
        }
        String longer = a.length() > b.length() ? a : b;
        String shorter = a.length() > b.length() ? b : a;
        int i = 0;
        int j = 0;
        boolean skipped = false;
        while (i < longer.length() && j < shorter.length()) {
            if (longer.charAt(i) == shorter.charAt(j)) {
                i++;
                j++;
            } else if (skipped) {
                return false;
            } else {
                skipped = true;
                i++;
            }
        }
        return true;
    }
}
