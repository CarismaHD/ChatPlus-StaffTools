package com.ebicep.chatplus.features.moderation.command;

import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the output of the command in flight. Commands carry no correlation ID, so this relies on the queue sending
 * one at a time, and on the parser's start marker: nothing is collected until that marker arrives.
 */
final class ResponseCapture {
    private final CommandRequest request;
    private final List<CapturedLine> lines = new ArrayList<>();
    private boolean started;

    ResponseCapture(CommandRequest request) {
        this.request = request;
    }

    /** Returns true when the line belongs to this command. The line is still shown in chat either way. */
    boolean offer(CapturedLine line) {
        if (!started) {
            if (request.parser().isStart(line.text())) {
                started = true;
                lines.add(line);
                return true;
            }
            return false;
        }
        if (request.parser().accepts(line.text())) {
            lines.add(line);
            return true;
        }
        return false;
    }

    boolean isComplete() {
        return started && request.parser().isComplete(lines);
    }

    Response finish() {
        Object parsed = null;
        if (started) {
            try {
                parsed = request.parser().parse(lines);
            } catch (RuntimeException e) {
                // A parser bug must not lose the output. It is still in chat.
                parsed = null;
            }
        }
        return new Response(request, List.copyOf(lines), parsed, !lines.isEmpty());
    }
}
