package com.ebicep.chatplus.features.moderation.parse;

import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import com.ebicep.chatplus.features.moderation.profile.Rules;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a command's reply as plain lines, for commands whose output is only shown, such as a chunk owner check. With
 * reply patterns, only lines matching them are taken, so other server messages arriving at the same time are left
 * out. Without, the reply is the first lines after the command that aren't player chat, up to a few lines.
 */
public final class LinesParser implements ResponseParser {
    public static final int MAX_LINES = 10;

    private final CompiledProfile p;
    private final Rules reply;
    private final boolean hasPatterns;

    public LinesParser(CompiledProfile profile, List<String> replyPatterns) {
        this.p = profile;
        List<String> patterns = replyPatterns == null ? List.of() : replyPatterns;
        this.reply = new Rules("reply", patterns, new ArrayList<>());
        this.hasPatterns = !patterns.isEmpty();
    }

    @Override
    public boolean isStart(String raw) {
        return accepts(raw);
    }

    @Override
    public boolean accepts(String raw) {
        String text = p.clean(raw);
        if (hasPatterns) {
            return reply.matches(text);
        }
        return !text.isBlank() && !p.chatLines.matches(text);
    }

    @Override
    public boolean isComplete(List<CapturedLine> lines) {
        // A reply's length isn't known ahead (a team can have any number of members), so it is collected until the
        // wait ends or this many lines have arrived.
        return lines.size() >= MAX_LINES;
    }

    /** The reply's lines, with formatting removed. */
    @Override
    public List<String> parse(List<CapturedLine> lines) {
        List<String> out = new ArrayList<>();
        for (CapturedLine line : lines) {
            out.add(p.clean(line.text()));
        }
        return out;
    }
}
