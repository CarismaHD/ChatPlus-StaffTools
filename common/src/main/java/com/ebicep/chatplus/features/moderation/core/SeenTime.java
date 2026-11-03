package com.ebicep.chatplus.features.moderation.core;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a date and time from a server reply, such as the {@code seen} group of a /seen reply. Accepts
 * "2026-01-02 09:15:30", with or without seconds, a "T" between date and time, and an optional zone after it ("UTC",
 * "+02:00", "Europe/Berlin"). Without a zone, the time is taken as UTC.
 */
public final class SeenTime {
    private static final Pattern DATE_TIME = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{1,2}:\\d{2}(?::\\d{2})?)(?:\\.\\d+)?\\s*([A-Za-z][\\w/+-]*|[+-]\\d{2}:?\\d{2}|Z)?\\.?$");

    private SeenTime() {
    }

    /** The time in milliseconds since 1970, or null when the text isn't a date and time this understands. */
    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = DATE_TIME.matcher(text.strip());
        if (!m.matches()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(m.group(1));
            String t = m.group(2);
            LocalTime time = LocalTime.parse(t.length() == 4 || t.length() == 7 ? "0" + t : t);
            String zone = m.group(3);
            ZoneId id = zone == null || zone.equals("Z") ? ZoneOffset.UTC : ZoneId.of(zone.equals("UTC") || zone.equals("GMT") ? "Z" : zone);
            return date.atTime(time).atZone(id).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            return null;
        }
    }
}
