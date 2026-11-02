package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.command.TemplateRenderer;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import com.ebicep.chatplus.features.moderation.parse.LookupParser;
import com.ebicep.chatplus.features.moderation.parse.LookupResult;
import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import com.ebicep.chatplus.features.moderation.profile.ServerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks a profile against a samples file of lines and the results they should give. The example pair lives in this
 * repository. A real server's pair stays outside it and runs only when passed in with -Pstafftools.profile and -Pstafftools.samples.
 */
class SamplesTest {
    @TempDir
    Path dir;

    @Test
    void exampleProfileMatchesExampleSamples() throws Exception {
        int checked = check(TestFiles.exampleProfileJson(), TestFiles.resource("/example-samples.json"));
        assertTrue(checked >= 15, "only " + checked + " checks ran");
    }

    @Test
    void realProfileMatchesRealSamples() throws Exception {
        String profile = System.getProperty("stafftools.profile");
        String samples = System.getProperty("stafftools.samples");
        assumeTrue(profile != null && samples != null, "no real profile given");
        int checked = check(Files.readString(Path.of(profile), StandardCharsets.UTF_8), Files.readString(Path.of(samples), StandardCharsets.UTF_8));
        System.out.println("Real profile: " + checked + " checks passed");
    }

    private int check(String profileJson, String samplesJson) throws Exception {
        Files.writeString(dir.resolve("profile.json"), profileJson);
        Engine engine = new Engine(dir, c -> true, () -> 0L, null);
        assertEquals(List.of(), engine.errors(), "profile patterns must all compile");
        CompiledProfile p = engine.profile();
        JsonObject s = JsonParser.parseString(samplesJson).getAsJsonObject();
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (JsonElement e : array(s, "lookups")) {
            JsonObject o = e.getAsJsonObject();
            List<CapturedLine> lines = new ArrayList<>();
            array(o, "lines").forEach(x -> lines.add(CapturedLine.ofText(x.getAsString())));
            String label = "lookup " + str(o, "name");
            checked++;
            LookupParser parser = new LookupParser(p);
            if (!parser.isStart(lines.get(0).text())) {
                failures.add(label + ": first line is not recognized as the start");
            }
            for (CapturedLine l : lines.subList(1, lines.size())) {
                if (!parser.accepts(l.text())) {
                    failures.add(label + ": line not accepted: " + l.text());
                }
            }
            for (JsonElement reject : array(o, "rejects")) {
                if (parser.accepts(reject.getAsString())) {
                    failures.add(label + ": must not claim: " + reject.getAsString());
                }
            }
            LookupResult r = parser.parse(lines);
            expect(failures, label, "name", str(o, "name"), r.name());
            expect(failures, label, "warnings", str(o, "warnings"), String.valueOf(r.warnings()));
            expect(failures, label, "notes", str(o, "notes"), String.valueOf(r.notes()));
            expect(failures, label, "bans", str(o, "bans"), String.valueOf(r.activeBans()));
            expect(failures, label, "warningIds", list(o, "warningIds"), r.warningEntries().stream().map(x -> String.valueOf(x.id())).toList().toString());
            expect(failures, label, "noteIds", list(o, "noteIds"), r.noteEntries().stream().map(x -> String.valueOf(x.id())).toList().toString());
            if (o.has("firstWarningText")) {
                expect(failures, label, "firstWarningText", str(o, "firstWarningText"), r.warningEntries().isEmpty() ? null : r.warningEntries().get(0).text());
            }
            if (o.has("firstWarningStaff")) {
                expect(failures, label, "firstWarningStaff", str(o, "firstWarningStaff"), r.warningEntries().isEmpty() ? null : r.warningEntries().get(0).staff());
            }
        }

        for (JsonElement e : array(s, "clicks")) {
            JsonObject o = e.getAsJsonObject();
            checked++;
            expect(failures, "click " + str(o, "value"), "name", str(o, "name"), engine.nameFromClick(str(o, "value")));
        }

        for (JsonElement e : array(s, "commands")) {
            JsonObject o = e.getAsJsonObject();
            checked++;
            CommandTemplate t = p.command(str(o, "id"));
            if (t == null) {
                failures.add("command " + str(o, "id") + " is missing from the profile");
                continue;
            }
            Map<String, String> values = new HashMap<>();
            if (o.has("values")) {
                o.getAsJsonObject("values").entrySet().forEach(v -> values.put(v.getKey(), v.getValue().getAsString()));
            }
            expect(failures, "command " + t.id(), "rendered", str(o, "expect"), TemplateRenderer.render(t.template(), values).command());
            if (o.has("confirm")) {
                expect(failures, "command " + t.id(), "confirm", str(o, "confirm"), String.valueOf(t.confirm()));
            }
        }

        // ChatPlus puts a line in a tab when the tab's regex matches the whole unformatted line.
        for (JsonElement e : array(s, "tabs")) {
            JsonObject o = e.getAsJsonObject();
            checked++;
            String text = str(o, "text");
            List<String> matched = new ArrayList<>();
            for (ServerProfile.Tab t : p.tabs) {
                if (Pattern.compile(t.pattern).matcher(text).matches()) {
                    matched.add(t.name);
                }
            }
            expect(failures, "tab " + text, "tabs", list(o, "tabs"), matched.toString());
        }

        for (JsonElement e : array(s, "servers")) {
            JsonObject o = e.getAsJsonObject();
            checked++;
            expect(failures, str(o, "address"), "active", str(o, "active"), String.valueOf(p.matchesServer(str(o, "address"))));
        }

        if (!failures.isEmpty()) {
            fail(failures.size() + " sample checks failed:\n" + String.join("\n", failures));
        }
        return checked;
    }

    private static void expect(List<String> failures, String subject, String field, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures.add(subject + "\n    " + field + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static String list(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        array(o, key).forEach(x -> out.add(x.isJsonNull() ? "null" : x.getAsString()));
        return out.toString();
    }
}
