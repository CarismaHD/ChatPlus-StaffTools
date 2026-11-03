package com.ebicep.chatplus.features.moderation.core;

import com.ebicep.chatplus.features.moderation.parse.Text;
import com.ebicep.chatplus.features.moderation.profile.Rules;
import java.util.Locale;
import java.util.regex.Matcher;

/** Reads a player's nickname from how the tab list shows them, such as "[Member] ✿Ally✿". */
public final class Nicknames {
    private Nicknames() {
    }

    /**
     * The nickname in a tab list name, or null when there is none: no tab name, nothing left after the rank, or just
     * the username again.
     *
     * @param patterns the profile's {@code chat.nickFromTab}; when empty, leading [bracketed] tags are removed
     */
    public static String fromTabName(String tabName, String username, Rules patterns) {
        if (tabName == null || tabName.isBlank()) {
            return null;
        }
        String text = Text.clean(tabName).strip();
        String nick;
        if (patterns != null && !patterns.isEmpty()) {
            Matcher m = patterns.match(text);
            nick = m != null ? Rules.group(m, "nick") : null;
        } else {
            nick = text.replaceFirst("^(?:\\[[^\\]]*]\\s*)+", "");
        }
        if (nick == null) {
            return null;
        }
        nick = nick.strip();
        return nick.isEmpty() || nick.toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT)) ? null : nick;
    }
}
