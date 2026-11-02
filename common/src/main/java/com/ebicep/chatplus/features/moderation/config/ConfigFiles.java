package com.ebicep.chatplus.features.moderation.config;

import com.ebicep.chatplus.features.moderation.profile.ServerProfile;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the server profile, writing the empty bundled default on first run. A file that cannot be read is moved aside,
 * never overwritten, and reported.
 */
public final class ConfigFiles {
    public static final String PROFILE = "profile.json";
    private static final Gson GSON = new Gson();

    private final Path dir;
    private final List<String> errors = new ArrayList<>();

    public ConfigFiles(Path dir) {
        this.dir = dir;
    }

    public List<String> errors() {
        return errors;
    }

    public ServerProfile profile() {
        Path file = dir.resolve(PROFILE);
        String fallback = bundledProfile();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dir);
                Files.writeString(file, fallback, StandardCharsets.UTF_8);
                return GSON.fromJson(fallback, ServerProfile.class);
            }
            ServerProfile parsed = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerProfile.class);
            if (parsed == null) {
                throw new JsonParseException("file is empty");
            }
            return parsed;
        } catch (JsonParseException e) {
            try {
                Path aside = file.resolveSibling(PROFILE + ".broken-" + System.currentTimeMillis());
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
                errors.add(PROFILE + " could not be read and was moved to " + aside.getFileName() + ": " + e.getMessage());
            } catch (IOException io) {
                errors.add(PROFILE + " could not be read or moved: " + io.getMessage());
            }
        } catch (IOException e) {
            errors.add(PROFILE + " could not be accessed: " + e.getMessage());
        }
        return GSON.fromJson(fallback, ServerProfile.class);
    }

    private static String bundledProfile() {
        try (InputStream in = ConfigFiles.class.getResourceAsStream("/assets/chatplus/moderation/profile.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled profile");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled profile", e);
        }
    }
}
