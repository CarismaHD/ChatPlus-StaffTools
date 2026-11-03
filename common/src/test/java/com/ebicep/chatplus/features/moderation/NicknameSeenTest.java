package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.core.Nicknames;
import com.ebicep.chatplus.features.moderation.core.PlayerDirectory;
import com.ebicep.chatplus.features.moderation.core.SeenTime;
import com.ebicep.chatplus.features.moderation.profile.Rules;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Nicknames from the tab list, and last-seen times from the server. */
class NicknameSeenTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Rules NONE = new Rules("none", List.of(), new ArrayList<>());

    @TempDir
    Path dir;

    @Test
    void nicknameIsTheTabNameWithoutRankTags() {
        assertEquals("✿Ally✿", Nicknames.fromTabName("[Member] ✿Ally✿", "Alice", NONE));
        assertEquals("Queen Ally", Nicknames.fromTabName("[Hall of Fame] [VIP] Queen Ally", "Alice", NONE));
        assertEquals("Ally", Nicknames.fromTabName("§a[Member] §bAlly", "Alice", NONE), "formatting is removed");
    }

    @Test
    void noNicknameWhenItIsJustTheUsername() {
        assertNull(Nicknames.fromTabName("[Member] alice", "Alice", NONE));
        assertNull(Nicknames.fromTabName(null, "Alice", NONE));
        assertNull(Nicknames.fromTabName("[Member]", "Alice", NONE));
    }

    @Test
    void aProfilePatternCanSkipMore() {
        Rules rankWord = new Rules("nick", List.of("^(?:\\[[^\\]]*\\]\\s*)*(?:[A-Z]{3,}\\s+(?=\\S))?(?<nick>.+)$"), new ArrayList<>());
        assertEquals("ᴀʟʟʏ", Nicknames.fromTabName("ADMIN ᴀʟʟʏ", "Alice", rankWord));
        assertNull(Nicknames.fromTabName("[Member] ALICE", "Alice", rankWord), "a name in capitals is still the username");
    }

    @Test
    void seenTimesInCommonFormats() {
        long utc = Instant.parse("2026-01-02T09:15:30Z").toEpochMilli();
        assertEquals(utc, SeenTime.parse("2026-01-02 09:15:30 UTC"));
        assertEquals(utc, SeenTime.parse("2026-01-02 09:15:30"), "no zone means UTC");
        assertEquals(utc, SeenTime.parse("2026-01-02T09:15:30Z"));
        assertEquals(utc, SeenTime.parse("2026-01-02 11:15:30 +02:00"));
        assertEquals(Instant.parse("2026-01-02T09:15:00Z").toEpochMilli(), SeenTime.parse("2026-01-02 09:15"));
        assertNull(SeenTime.parse("3 days ago"));
        assertNull(SeenTime.parse(null));
    }

    @Test
    void theServersLastSeenOnlyMovesForward() {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice"), 5_000);
        assertFalse(d.markSeen("alice", 4_000), "an older time changes nothing");
        assertTrue(d.markSeen("Alice", 9_000));
        assertEquals(9_000, d.byName("Alice").orElseThrow().lastSeen());
        assertFalse(d.markSeen("Nobody", 9_000));
    }

    @Test
    void nicknamesAreKeptSearchedAndSaved() throws Exception {
        PlayerDirectory d = new PlayerDirectory();
        d.update(Map.of(ALICE, "Alice"), Map.of(ALICE, "✿Ally✿"), 1_000);
        d.update(Map.of(ALICE, "Alice"), Map.of(), 2_000);
        assertEquals("✿Ally✿", d.byName("Alice").orElseThrow().nick(), "kept when the tab list shows none");
        assertEquals("Alice", d.search("ally").get(0).name(), "found by nickname");
        Path file = dir.resolve("s.json");
        d.save(file);
        PlayerDirectory loaded = new PlayerDirectory();
        loaded.load(file);
        assertEquals("✿Ally✿", loaded.byName("Alice").orElseThrow().nick());
    }
}
