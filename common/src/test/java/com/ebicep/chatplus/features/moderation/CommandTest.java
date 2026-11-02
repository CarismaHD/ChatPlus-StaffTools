package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.command.CommandQueue;
import com.ebicep.chatplus.features.moderation.command.CommandRequest;
import com.ebicep.chatplus.features.moderation.command.Response;
import com.ebicep.chatplus.features.moderation.command.TemplateRenderer;
import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import com.ebicep.chatplus.features.moderation.parse.LookupParser;
import com.ebicep.chatplus.features.moderation.parse.LookupResult;
import com.ebicep.chatplus.features.moderation.parse.ResponseParser;
import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandTest {
    private static final CompiledProfile P = TestFiles.exampleProfile();

    // ---- Templates ----

    @Test
    void templatesRenderWithOptionalSilentFlag() {
        assertEquals("/guard strike Alice Be nice", TemplateRenderer.render("/guard strike {player} {reason}", Map.of("player", "Alice", "reason", "Be nice")).command());
        String t = "/guard timeout {player} {duration} {silent} {reason}";
        assertEquals("/guard timeout A 1d --quiet r", TemplateRenderer.render(t, Map.of("player", "A", "duration", "1d", "silent", "--quiet", "reason", "r")).command());
        assertEquals("/guard timeout A 1d r", TemplateRenderer.render(t, Map.of("player", "A", "duration", "1d", "reason", "r")).command());
    }

    @Test
    void unsafeValuesAreRefused() {
        assertEquals(List.of("reason is required"), TemplateRenderer.render("/x {player} {reason}", Map.of("player", "A")).problems());
        assertFalse(TemplateRenderer.render("/x {player} {reason}", Map.of("player", "Some Nick", "reason", "r")).ok(),
                "a space in a name would shift the arguments");
        assertFalse(TemplateRenderer.render("/x {player} {warningId}", Map.of("player", "A", "warningId", "12a")).ok());
        assertEquals("/x A hic there", TemplateRenderer.render("/x {player} {message}", Map.of("player", "A", "message", "hi§c\nthere\u0007")).command());
        assertEquals("/x A {player} $1", TemplateRenderer.render("/x {player} {message}", Map.of("player", "A", "message", "{player} $1")).command());
    }

    // ---- Queue ----

    private static final class Fixture {
        long now = 1000;
        final List<String> sent = new ArrayList<>();
        final List<Response> done = new ArrayList<>();
        boolean connected = true;
        final CommandQueue queue = new CommandQueue(c -> connected && sent.add(c), () -> now, new CommandQueue.Limits() {
            public long minGapMs() {
                return 1500;
            }

            public long captureWindowMs() {
                return 1500;
            }

            public int maxQueued() {
                return 2;
            }
        });

        CommandRequest request(String command, ResponseParser parser) {
            return new CommandRequest("t", command, parser, done::add);
        }
    }

    private static CapturedLine line(String text) {
        return CapturedLine.ofText(text);
    }

    @Test
    void sendsOneAtATimeWithRateLimitAndSize() {
        Fixture f = new Fixture();
        assertTrue(f.queue.submit(f.request("/a", null)));
        assertTrue(f.queue.submit(f.request("/b", null)));
        assertFalse(f.queue.submit(f.request("/c", null)), "queue holds two here");
        assertTrue(f.sent.isEmpty(), "nothing is sent before a tick");
        f.queue.tick();
        f.queue.tick();
        assertEquals(List.of("a"), f.sent);
        f.now += 1500;
        f.queue.tick();
        assertEquals(List.of("a", "b"), f.sent);
    }

    @Test
    void commandsWithoutAParserDoNotWaitForOutput() {
        Fixture f = new Fixture();
        f.queue.submit(f.request("/a", null));
        f.queue.tick();
        assertEquals(0, f.queue.pending());
        assertEquals(1, f.done.size());
        assertFalse(f.queue.offer(line("anything")), "no line is claimed without a parser");
    }

    @Test
    void clearAndDisconnected() {
        Fixture f = new Fixture();
        f.queue.submit(f.request("/a", null));
        f.queue.tick();
        f.queue.submit(f.request("/b", null));
        f.queue.clear();
        f.now += 10000;
        f.queue.tick();
        assertEquals(List.of("a"), f.sent);

        Fixture g = new Fixture();
        g.connected = false;
        g.queue.submit(g.request("/a", new LookupParser(P)));
        g.queue.tick();
        assertFalse(g.done.get(0).captured());
    }

    @Test
    void lookupCaptureIgnoresChatInBetween() {
        Fixture f = new Fixture();
        f.queue.submit(f.request("/guard record Alice", new LookupParser(P)));
        f.queue.tick();
        assertFalse(f.queue.offer(line("Member | Bob » before the marker")));
        assertTrue(f.queue.offer(line("-- Record: Alice --")));
        assertTrue(f.queue.offer(line("• Strikes = 1")));
        assertFalse(f.queue.offer(line("Member | Bob » hi alice")));
        assertFalse(f.queue.offer(line("(Staff) Mod_A » checking")));
        assertFalse(f.queue.offer(line("Saved screenshot as a.png")));
        assertTrue(f.queue.offer(line("Strikes>")));
        assertTrue(f.queue.offer(line("  3) 2026-01-01 09:00 by Mod_A :: text")));
        f.now += 1500;
        f.queue.tick();
        LookupResult r = (LookupResult) f.done.get(0).parsed();
        assertEquals("Alice", r.name());
        assertEquals(1, r.warningEntries().size());
        assertEquals(4, f.done.get(0).lines().size());
    }

    @Test
    void noMarkerMeansNoResult() {
        Fixture f = new Fixture();
        f.queue.submit(f.request("/guard record A", new LookupParser(P)));
        f.queue.tick();
        f.queue.offer(line("Unknown command"));
        f.now += 1500;
        f.queue.tick();
        assertFalse(f.done.get(0).captured());
        assertNull(f.done.get(0).parsed());
    }

    @Test
    void parserFailureKeepsLines() {
        Fixture f = new Fixture();
        ResponseParser broken = new ResponseParser() {
            public boolean isStart(String raw) {
                return true;
            }

            public boolean accepts(String raw) {
                return true;
            }

            public Object parse(List<CapturedLine> lines) {
                throw new IllegalStateException("boom");
            }
        };
        f.queue.submit(f.request("/x", broken));
        f.queue.tick();
        f.queue.offer(line("line"));
        f.now += 1500;
        f.queue.tick();
        assertNull(f.done.get(0).parsed());
        assertEquals(1, f.done.get(0).lines().size());
    }
}
