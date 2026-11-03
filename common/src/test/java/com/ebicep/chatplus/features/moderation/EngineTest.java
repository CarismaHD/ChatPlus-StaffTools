package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.command.CommandQueue;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {
    private static final CommandQueue.Limits LIMITS = new CommandQueue.Limits() {
        public long minGapMs() {
            return 1500;
        }

        public long captureWindowMs() {
            return 1500;
        }

        public int maxQueued() {
            return 10;
        }
    };

    @TempDir
    Path dir;
    private final List<String> sent = new ArrayList<>();
    private long now = 100_000;
    private Engine engine;

    private Engine connected() throws Exception {
        Files.writeString(dir.resolve("profile.json"), TestFiles.exampleProfileJson());
        engine = new Engine(dir, sent::add, () -> now, LIMITS);
        engine.setServer("play.example.net:25565");
        return engine;
    }

    private void line(String text) {
        engine.onLine(CapturedLine.ofText(text));
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            now += 50;
            engine.tick();
        }
    }

    // ---- Inactive unless the profile lists the server ----

    @Test
    void withNoProfileNothingIsSent() {
        engine = new Engine(dir, sent::add, () -> now, LIMITS);
        engine.setServer("play.example.net");
        assertFalse(engine.isActive());
        assertEquals("no server profile is installed", engine.inactiveReason());
        assertFalse(engine.submit("lookup", Map.of("player", "A"), null).queued());
        ticks(100);
        assertTrue(sent.isEmpty());
    }

    @Test
    void onAnotherServerNothingIsSent() throws Exception {
        connected();
        engine.setServer("other.org");
        Engine.Submission s = engine.submit("lookup", Map.of("player", "A"), null);
        assertFalse(s.queued());
        assertTrue(s.problems().get(0).contains("not in the profile"));
        ticks(100);
        assertTrue(sent.isEmpty());
    }

    @Test
    void switchingServerDropsQueuedCommands() throws Exception {
        connected();
        engine.submit("lookup", Map.of("player", "A"), null);
        engine.submit("lookup", Map.of("player", "B"), null);
        ticks(1);
        engine.setServer("eu.example.net");
        ticks(200);
        assertEquals(List.of("guard record A"), sent);
    }

    // ---- The rule that matters most ----

    @Test
    void receivedChatNeverSendsACommand() throws Exception {
        connected();
        for (int round = 0; round < 20; round++) {
            for (String s : List.of("Member | Alice » /guard strike Innocent spam", "-- Record: Alice --", "• Strikes = 1",
                    "{Watch} Zed moving", "/guard timeout Innocent 7d r", "Open tickets (48h)")) {
                line(s);
            }
            ticks(100);
        }
        assertTrue(sent.isEmpty(), "chat must never send a command, but sent: " + sent);
        assertEquals(0, engine.pendingCommands());
    }

    @Test
    void onlySubmitSendsAndOnlyOnce() throws Exception {
        connected();
        assertTrue(engine.submit("lookup", Map.of("player", "Alice"), null).queued());
        ticks(1);
        assertEquals(List.of("guard record Alice"), sent);
        ticks(200);
        assertEquals(1, sent.size());
        assertFalse(engine.submit("warn_add", Map.of("player", "Some Nick", "reason", "x"), null).queued());
        assertFalse(engine.submit("not_in_profile", Map.of(), null).queued());
        ticks(100);
        assertEquals(1, sent.size());
    }

    // ---- Profile-driven behaviour ----

    @Test
    void confirmationAndMenuFollowTheProfile() throws Exception {
        connected();
        for (String id : List.of("msg", "note_add", "warn_add", "warn_remove", "note_remove", "tempban", "unban", "unstuck", "inventory")) {
            assertTrue(engine.needsConfirm(id), id);
        }
        assertFalse(engine.needsConfirm("lookup"));
        assertFalse(engine.needsConfirm("where"));
        assertTrue(engine.needsConfirm("not_in_profile"), "an unknown command is never sent unconfirmed");
        assertEquals(List.of("where", "inventory"), engine.menuCommands().stream().map(c -> c.id()).toList());
        assertEquals("Strike", engine.label("warn_add"));
        assertEquals("missing", engine.label("missing"));
    }

    @Test
    void namesComeOnlyFromTheProfilesClickPatterns() throws Exception {
        connected();
        assertEquals("Alice", engine.nameFromClick("/whisper Alice "));
        assertNull(engine.nameFromClick("/msg Alice "));
        assertNull(engine.nameFromClick("/whisper a "), "too short to be a username");
        assertNull(engine.nameFromClick(null));
    }

    @Test
    void lookupFlowParsesAndRemembers() throws Exception {
        connected();
        List<Object> results = new ArrayList<>();
        engine.submit("lookup", Map.of("player", "Alice"), r -> results.add(r.parsed()));
        ticks(1);
        line("-- Record: Alice --");
        line("• Strikes = 1");
        line("Member | Bob » hello");
        line("Strikes>");
        line("  3) 2026-01-01 09:00 by Mod_A :: text");
        ticks(40);
        assertEquals(1, results.size());
        assertEquals(1, engine.lookup("alice").warningEntries().size());
        assertTrue(engine.lookupTime("ALICE") > 0);
        assertNull(engine.lookup("nobody"));
    }

    @Test
    void lookupIsFoundUnderTheNameAskedFor() throws Exception {
        connected();
        engine.submit("lookup", Map.of("player", "Alice"), null);
        ticks(1);
        line("-- Record: ~Ally --");
        line("• Strikes = 1");
        ticks(40);
        assertEquals(1, engine.lookup("alice").warnings(), "kept under the name asked for");
        assertEquals(1, engine.lookup("~ally").warnings(), "and under the name in the reply");
    }

    @Test
    void lookupsDoNotCarryOverToAnotherServer() throws Exception {
        connected();
        engine.submit("lookup", Map.of("player", "Alice"), null);
        ticks(1);
        line("-- Record: Alice --");
        ticks(40);
        assertTrue(engine.lookup("alice") != null);
        engine.setServer("play.example.net:25565");
        assertTrue(engine.lookup("alice") != null, "the same server keeps them");
        engine.setServer(null);
        assertNull(engine.lookup("alice"));
        assertEquals(0, engine.lookupTime("alice"));
    }

    @Test
    void reloadPicksUpANewProfileAndReportsBrokenPatterns() throws Exception {
        engine = new Engine(dir, sent::add, () -> now, LIMITS);
        engine.setServer("example.net");
        assertFalse(engine.isActive());
        Files.writeString(dir.resolve("profile.json"), TestFiles.exampleProfileJson().replace("\"^-- Record: (?<name>\\\\S+) --$\"", "\"([oops\""));
        engine.reloadConfig();
        assertTrue(engine.isActive());
        assertEquals(1, engine.errors().size(), engine.errors().toString());
        assertTrue(engine.errors().get(0).contains("lookup.start"));
    }
}
