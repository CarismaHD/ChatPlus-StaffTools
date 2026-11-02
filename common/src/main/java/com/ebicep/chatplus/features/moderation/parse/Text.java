package com.ebicep.chatplus.features.moderation.parse;

import java.util.regex.Pattern;

/** Generic text cleanup. Server-specific stripping lives in the profile. */
public final class Text {
    private static final Pattern LEGACY_FORMAT = Pattern.compile("§.");
    private static final Pattern LEADING_BULLET = Pattern.compile("^\\s*[•·�]\\s*");

    private Text() {
    }

    /** Removes format codes and a leading bullet. Indentation stays, since parsers read it. */
    public static String clean(String raw) {
        String s = LEGACY_FORMAT.matcher(raw).replaceAll("");
        s = LEADING_BULLET.matcher(s).replaceFirst("");
        return s.stripTrailing();
    }

    /** True when the raw line starts with a bullet, the mark of a detail line under a command header. */
    public static boolean hasBullet(String raw) {
        return LEADING_BULLET.matcher(raw).find();
    }
}
