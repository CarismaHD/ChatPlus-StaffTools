package com.ebicep.chatplus.features.moderation.core;

import java.util.Collection;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * Decides whether a chat message is really a command typed without its slash, such as "staff ban Steve 1d", so it
 * can be held back instead of being posted in public chat.
 */
public final class SlashGuard {
    private SlashGuard() {
    }

    /**
     * @param text         exactly what would be sent, after any tab auto-prefix
     * @param guardWords   first words that are never meant for public chat, from the profile
     * @param isSubcommand whether a first word is a server command and the second word one of its literal sub-commands
     */
    public static boolean shouldHold(String text, Collection<String> guardWords, BiPredicate<String, String> isSubcommand) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) {
            return false;
        }
        String[] words = trimmed.split("\\s+");
        String first = words[0].toLowerCase(Locale.ROOT);
        for (String w : guardWords) {
            if (first.equals(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        // A command with a literal second word, like "staff ban", is almost never a sentence. "help me" or "list of
        // items" pass, because what follows those commands is an argument, not a literal.
        return words.length >= 2 && isSubcommand.test(first, words[1].toLowerCase(Locale.ROOT));
    }
}
