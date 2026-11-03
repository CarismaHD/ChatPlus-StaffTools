package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.core.PlayerDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDirectoryTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path dir;

    @Test
    void remembersPlayersAfterTheyLeave() {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice", BOB, "Bob"), 1_000);
        d.update(Map.of(ALICE, "Alice"), 5_000);
        assertTrue(d.isOnline("alice"));
        assertFalse(d.isOnline("Bob"));
        assertEquals(1_000, d.byName("bob").orElseThrow().lastSeen());
        assertEquals(5_000, d.byName("Alice").orElseThrow().lastSeen());
        assertEquals(1_000, d.byName("Alice").orElseThrow().firstSeen());
        assertEquals(2, d.size());
        assertEquals(List.of("Alice"), d.onlineNames());
    }

    @Test
    void aNameChangeKeepsTheSamePlayer() {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(BOB, "Bob"), 1_000);
        d.update(Map.of(BOB, "Bobby"), 2_000);
        assertEquals(1, d.size());
        assertTrue(d.byName("Bob").isEmpty());
        assertEquals(1_000, d.byName("Bobby").orElseThrow().firstSeen());
    }

    @Test
    void searchListsOnlineFirstThenMostRecentlySeen() {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(CAROL, "Carol"), 1_000);
        d.update(Map.of(BOB, "Bob"), 3_000);
        d.update(Map.of(ALICE, "Alice"), 5_000);
        d.update(Map.of(CAROL, "Carol"), 6_000);
        assertEquals(List.of("Carol", "Alice", "Bob"), d.search("").stream().map(PlayerDirectory.Player::name).toList());
        assertEquals(List.of("Alice"), d.search("LIC").stream().map(PlayerDirectory.Player::name).toList());
        assertTrue(d.search("zzz").isEmpty());
    }

    @Test
    void leavingTheServerMarksEveryoneOffline() {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice"), 1_000);
        assertTrue(d.knowsWhoIsOnline());
        d.clearOnline();
        assertFalse(d.isOnline("Alice"));
        assertFalse(d.knowsWhoIsOnline());
        assertEquals(1, d.size());
    }

    @Test
    void savesAndLoadsTheSamePlayers() throws Exception {
        Path file = dir.resolve("players").resolve("play.example.net.json");
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice", BOB, "Bob"), 1_000);
        assertTrue(d.dirty());
        d.save(file);
        assertFalse(d.dirty());
        assertFalse(Files.exists(file.resolveSibling("play.example.net.json.tmp")));

        PlayerDirectory loaded = new PlayerDirectory();
        loaded.load(file);
        assertEquals(2, loaded.size());
        assertEquals(1_000, loaded.byName("Bob").orElseThrow().firstSeen());
        assertFalse(loaded.isOnline("Alice"), "nobody is online until the tab list says so");
    }

    @Test
    void aMissingFileIsAnEmptyList() throws Exception {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice"), 1_000);
        d.load(dir.resolve("nothing-here.json"));
        assertEquals(0, d.size());
    }

    @Test
    void aBrokenEntryIsSkippedNotTheWholeFile() throws Exception {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "[{\"uuid\":\"not-a-uuid\",\"name\":\"X\"},{\"uuid\":\"" + ALICE + "\",\"name\":\"Alice\",\"firstSeen\":1,\"lastSeen\":2}]");
        PlayerDirectory d = new PlayerDirectory();
        d.load(file);
        assertEquals(1, d.size());
        assertTrue(d.byName("Alice").isPresent());
    }

    @Test
    void tabListFillersAreNotPlayers() {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice", BOB, "~00", CAROL, "~01"), 1_000);
        assertEquals(1, d.size());
        assertEquals(1, d.onlineCount());
        assertEquals(List.of("Alice"), d.onlineNames());
    }

    @Test
    void fillersSavedByAnOlderVersionAreDropped() throws Exception {
        Path file = dir.resolve("old.json");
        Files.writeString(file, "[{\"uuid\":\"" + ALICE + "\",\"name\":\"Alice\",\"firstSeen\":1,\"lastSeen\":2},"
                + "{\"uuid\":\"" + BOB + "\",\"name\":\"~02\",\"firstSeen\":1,\"lastSeen\":2}]");
        PlayerDirectory d = new PlayerDirectory();
        d.load(file);
        assertEquals(1, d.size());
        assertTrue(d.dirty(), "saved again so the file loses them");
        d.save(file);
        assertFalse(Files.readString(file).contains("~02"));
    }

    @Test
    void fileNamesComeFromTheHostOnly() {
        assertEquals("play.example.net.json", PlayerDirectory.fileNameFor("Play.Example.net:25565"));
        assertEquals("a_b.example.net.json", PlayerDirectory.fileNameFor("a b.example.net"));
    }
}
