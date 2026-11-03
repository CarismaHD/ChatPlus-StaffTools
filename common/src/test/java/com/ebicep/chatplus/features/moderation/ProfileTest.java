package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.config.ConfigFiles;
import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import com.ebicep.chatplus.features.moderation.profile.Rules;
import com.ebicep.chatplus.features.moderation.profile.ServerProfile;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileTest {
    @Test
    void bundledProfileIsEmptyInactiveAndHasNoServerDetails() {
        String bundled = TestFiles.resource("/assets/chatplus/moderation/profile.json");
        CompiledProfile p = CompiledProfile.compile(new Gson().fromJson(bundled, ServerProfile.class));
        assertTrue(p.isEmpty());
        assertTrue(p.commands.isEmpty());
        assertTrue(p.tabs.isEmpty());
        assertTrue(p.errors.isEmpty());
        assertFalse(p.matchesServer("example.net"));
        assertFalse(bundled.contains("/"), "the jar must not carry command syntax: " + bundled);
    }

    @Test
    void exampleProfileCompilesCleanly() {
        CompiledProfile p = TestFiles.exampleProfile();
        assertEquals(List.of(), p.errors);
        assertEquals(2, p.tabs.size());
        assertEquals(14, p.commands.size());
    }

    @Test
    void serverMatchingIgnoresPortCaseAndSubdomains() {
        CompiledProfile p = TestFiles.exampleProfile();
        assertTrue(p.matchesServer("example.net"));
        assertTrue(p.matchesServer("Play.Example.NET:25565"));
        assertTrue(p.matchesServer("eu.play.example.net."));
        assertFalse(p.matchesServer("notexample.net"));
        assertFalse(p.matchesServer("example.net.evil.org"));
        assertFalse(p.matchesServer(null));
        assertFalse(p.matchesServer(""));
    }

    @Test
    void invalidPatternsAndTabsAreReportedAndSkipped() {
        ServerProfile sp = new ServerProfile();
        sp.lookup.start = List.of("([broken", "^-- (?<name>\\S+) --$");
        ServerProfile.Tab bad = new ServerProfile.Tab();
        bad.name = "Bad";
        bad.pattern = "([";
        ServerProfile.Tab blank = new ServerProfile.Tab();
        sp.tabs = List.of(bad, blank);
        CompiledProfile p = CompiledProfile.compile(sp);
        assertEquals(2, p.errors.size(), p.errors.toString());
        assertTrue(p.lookupStart.matches("-- Alice --"));
        assertTrue(p.tabs.isEmpty());
    }

    @Test
    void missingAndNullSectionsAreTolerated() {
        ServerProfile sp = new Gson().fromJson("{\"servers\":[\"a.org\",null,\" \"],\"chat\":null,\"lookup\":null,\"commands\":[null,{\"id\":\"x\"}],\"tabs\":null}",
                ServerProfile.class);
        CompiledProfile p = CompiledProfile.compile(sp);
        assertTrue(p.errors.isEmpty());
        assertEquals(List.of("a.org"), p.servers);
        assertTrue(p.commands.isEmpty(), "a command without a template is dropped");
    }

    @Test
    void namedGroupsThatDoNotExistReadAsNull() {
        var m = new Rules("t", List.of("^(?<a>\\w+)$"), new ArrayList<>()).match("hello");
        assertEquals("hello", Rules.group(m, "a"));
        assertNull(Rules.group(m, "missing"));
        assertNull(Rules.intGroup(m, "a"));
    }

    @Test
    void firstRunWritesTheEmptyProfile(@TempDir Path dir) {
        ConfigFiles files = new ConfigFiles(dir);
        assertTrue(files.profile().servers.isEmpty());
        assertTrue(Files.exists(dir.resolve("profile.json")));
        assertTrue(files.errors().isEmpty());
    }

    @Test
    void brokenProfileIsMovedAsideNotOverwritten(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("profile.json"), "{ not json");
        ConfigFiles files = new ConfigFiles(dir);
        assertTrue(files.profile().servers.isEmpty());
        assertEquals(1, files.errors().size());
        try (var s = Files.list(dir)) {
            assertTrue(s.anyMatch(f -> f.getFileName().toString().startsWith("profile.json.broken-")));
        }
    }

    @Test
    void slashGuardWordsAreSingleLowercaseWordsWithoutSlash() {
        assertEquals(List.of("guard", "peek"), TestFiles.exampleProfile().slashGuard);
        ServerProfile p = new Gson().fromJson("{\"slashGuard\": [\"STAFF\", \"two words\", \"\"]}", ServerProfile.class);
        CompiledProfile c = CompiledProfile.compile(p);
        assertEquals(List.of("staff"), c.slashGuard);
        assertTrue(c.errors.stream().anyMatch(e -> e.contains("two words")), c.errors.toString());
    }
}
