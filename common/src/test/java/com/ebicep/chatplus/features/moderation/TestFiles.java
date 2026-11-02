package com.ebicep.chatplus.features.moderation;

import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import com.ebicep.chatplus.features.moderation.profile.ServerProfile;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared fixtures. The example profile describes a made-up server, so no real server details live in this repository. */
public final class TestFiles {
    private TestFiles() {
    }

    public static String resource(String path) {
        try (InputStream in = TestFiles.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String exampleProfileJson() {
        return resource("/example-profile.json");
    }

    public static CompiledProfile exampleProfile() {
        return CompiledProfile.compile(new Gson().fromJson(exampleProfileJson(), ServerProfile.class));
    }

    public static CompiledProfile profileFromFile(Path file) throws IOException {
        return CompiledProfile.compile(new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerProfile.class));
    }
}
