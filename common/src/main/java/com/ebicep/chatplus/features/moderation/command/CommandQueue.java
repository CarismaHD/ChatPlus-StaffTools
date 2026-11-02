package com.ebicep.chatplus.features.moderation.command;

import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

/** Sends one command at a time, at a limited rate, and routes output to the command that asked for it. */
public final class CommandQueue {
    /** Timing and size limits, read on every use so settings changes apply at once. */
    public interface Limits {
        long minGapMs();

        long captureWindowMs();

        int maxQueued();
    }

    private final Deque<CommandRequest> queue = new ArrayDeque<>();
    private final CommandSender sender;
    private final LongSupplier clock;
    private final Limits limits;
    private ResponseCapture capture;
    private long sentAt = Long.MIN_VALUE / 2;

    public CommandQueue(CommandSender sender, LongSupplier clock, Limits limits) {
        this.sender = sender;
        this.clock = clock;
        this.limits = limits;
    }

    /** Returns false when the queue is full, so the caller can say so rather than lose it silently. */
    public boolean submit(CommandRequest request) {
        if (queue.size() >= limits.maxQueued()) {
            return false;
        }
        queue.addLast(request);
        return true;
    }

    /** Call once per client tick. */
    public void tick() {
        long now = clock.getAsLong();
        if (capture != null && (capture.isComplete() || now - sentAt >= limits.captureWindowMs())) {
            ResponseCapture done = capture;
            capture = null;
            Response response = done.finish();
            if (response.request().onDone() != null) {
                response.request().onDone().accept(response);
            }
        }
        if (capture == null && !queue.isEmpty() && now - sentAt >= limits.minGapMs()) {
            CommandRequest next = queue.removeFirst();
            String command = next.command().startsWith("/") ? next.command().substring(1) : next.command();
            sentAt = now;
            if (!sender.send(command)) {
                if (next.onDone() != null) {
                    next.onDone().accept(new Response(next, List.of(), null, false));
                }
            } else if (next.parser() != null) {
                // Only commands whose output is read wait for it. The rest just respect the rate limit.
                capture = new ResponseCapture(next);
            } else if (next.onDone() != null) {
                next.onDone().accept(new Response(next, List.of(), null, false));
            }
        }
    }

    /** Returns true when the line belongs to the command in flight. */
    public boolean offer(CapturedLine line) {
        return capture != null && capture.offer(line);
    }

    /** Drops everything, so nothing queued can reach a different server after a disconnect. */
    public void clear() {
        queue.clear();
        capture = null;
    }

    public int pending() {
        return queue.size() + (capture != null ? 1 : 0);
    }
}
