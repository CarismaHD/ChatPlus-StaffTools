package com.ebicep.chatplus.features.moderation.parse;

import java.util.List;

/** Recognizes and parses the chat output of one command. */
public interface ResponseParser {
    /** True when the line is this command's start marker. */
    boolean isStart(String raw);

    /** True when a line after the start marker belongs to this response. */
    boolean accepts(String raw);

    /** True when no more lines can belong to the response, so capture can close early. */
    default boolean isComplete(List<CapturedLine> lines) {
        return false;
    }

    /** Parses the claimed lines, including the start marker. */
    Object parse(List<CapturedLine> lines);
}