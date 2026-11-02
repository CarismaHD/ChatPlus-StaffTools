package com.ebicep.chatplus.features.moderation.parse;

import java.time.Instant;

/** A received chat line reduced to plain text, so nothing past this point depends on Minecraft classes. */
public record CapturedLine(Instant time, String text) {
    public static CapturedLine ofText(String text) {
        return new CapturedLine(Instant.EPOCH, text);
    }
}
