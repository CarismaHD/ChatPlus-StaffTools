package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import com.ebicep.chatplus.features.moderation.parse.LinesParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reply of a command that is only shown, such as the chunk owner check. */
class LinesParserTest {
    private final LinesParser parser = new LinesParser(TestFiles.exampleProfile(), null);

    @Test
    void takesServerLinesButNotPlayerChat() {
        assertTrue(parser.isStart("§eClaimed by §6Team Carol"));
        assertFalse(parser.isStart("Member | Alice » hello there"), "player chat is never part of a reply");
        assertFalse(parser.isStart("   "));
    }

    @Test
    void returnsTheLinesWithoutFormatting() {
        List<CapturedLine> lines = List.of(CapturedLine.ofText("§eClaimed by §6Team Carol"), CapturedLine.ofText("Force loaded: no"));
        assertEquals(List.of("Claimed by Team Carol", "Force loaded: no"), parser.parse(lines));
    }

    @Test
    void stopsAfterAFewLines() {
        List<CapturedLine> lines = new ArrayList<>();
        for (int i = 0; i < LinesParser.MAX_LINES - 1; i++) {
            lines.add(CapturedLine.ofText("line " + i));
        }
        assertFalse(parser.isComplete(lines));
        lines.add(CapturedLine.ofText("last"));
        assertTrue(parser.isComplete(lines));
    }

    @Test
    void withReplyPatternsOnlyThoseLinesAreTaken() {
        LinesParser chunk = new LinesParser(TestFiles.exampleProfile(), List.of(".*Claim: .+", "\\s*Owner: .+", "\\s*Loaded: .+"));
        assertFalse(chunk.isStart("Server restart in 5 minutes."), "other server messages are left out");
        assertTrue(chunk.isStart("# Claim: overworld [12, 40]"));
        assertTrue(chunk.accepts("  Owner: Team Carol"));
        assertFalse(chunk.accepts("Server restart in 5 minutes."));
        List<CapturedLine> lines = new ArrayList<>(List.of(CapturedLine.ofText("# Claim: overworld"), CapturedLine.ofText("Owner: Team Carol")));
        lines.add(CapturedLine.ofText("Loaded: 0"));
        assertFalse(chunk.isComplete(lines), "the length isn't known ahead, so it waits for more lines");
    }
}
