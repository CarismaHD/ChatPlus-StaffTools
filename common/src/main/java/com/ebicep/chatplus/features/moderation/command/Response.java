package com.ebicep.chatplus.features.moderation.command;

import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import java.util.List;

/**
 * Output captured for one command.
 *
 * @param parsed   parser result, or null when nothing was recognized
 * @param captured false when the wait ended with nothing attributed to the command
 */
public record Response(CommandRequest request, List<CapturedLine> lines, Object parsed, boolean captured) {
}
