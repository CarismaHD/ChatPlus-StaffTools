package com.ebicep.chatplus.features.moderation.command;

import com.ebicep.chatplus.features.moderation.parse.ResponseParser;
import java.util.function.Consumer;

/**
 * A rendered command waiting in the queue.
 *
 * @param command full command including the leading slash
 * @param parser  reads the command's output, or null when the output is only shown in chat
 * @param onDone  receives the response when capture closes, on the client thread, or null
 */
public record CommandRequest(String templateId, String command, ResponseParser parser, Consumer<Response> onDone) {
}
