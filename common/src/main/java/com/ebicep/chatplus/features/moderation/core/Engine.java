package com.ebicep.chatplus.features.moderation.core;

import com.ebicep.chatplus.features.moderation.command.CommandQueue;
import com.ebicep.chatplus.features.moderation.command.CommandRequest;
import com.ebicep.chatplus.features.moderation.command.CommandSender;
import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.command.Response;
import com.ebicep.chatplus.features.moderation.command.TemplateRenderer;
import com.ebicep.chatplus.features.moderation.config.ConfigFiles;
import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import com.ebicep.chatplus.features.moderation.parse.LookupParser;
import com.ebicep.chatplus.features.moderation.parse.LookupResult;
import com.ebicep.chatplus.features.moderation.profile.CompiledProfile;
import com.ebicep.chatplus.features.moderation.profile.Rules;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The moderation tools without any Minecraft classes, so it can be tested and moved to other game versions as is.
 * Everything server-specific comes from the profile, and nothing is sent unless connected to a server the profile
 * lists. Receiving chat never sends anything: only {@link #submit}, which the UI calls from a click, reaches the queue.
 * Client thread only.
 */
public final class Engine {
    /** What happened when a command was requested. */
    public record Submission(boolean queued, List<String> problems) {
    }

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final Path dir;
    private final LongSupplier clock;
    private final CommandQueue queue;
    private final List<String> errors = new ArrayList<>();
    private final Map<String, LookupResult> lookups = new HashMap<>();
    private final Map<String, Long> lookupTimes = new HashMap<>();
    private CompiledProfile profile = CompiledProfile.compile(null);
    private String serverAddress;

    public Engine(Path dir, CommandSender sender, LongSupplier clock, CommandQueue.Limits limits) {
        this.dir = dir;
        this.clock = clock;
        this.queue = new CommandQueue(sender, clock, limits);
        reloadConfig();
    }

    /** Reads profile.json. Runs at startup and on reload. */
    public void reloadConfig() {
        ConfigFiles files = new ConfigFiles(dir);
        profile = CompiledProfile.compile(files.profile());
        errors.clear();
        errors.addAll(files.errors());
        profile.errors.forEach(e -> errors.add("profile.json " + e));
    }

    public CompiledProfile profile() {
        return profile;
    }

    public List<String> errors() {
        return errors;
    }

    public Path dir() {
        return dir;
    }

    // ---- Server ----

    /** Sets the address of the current server, or null when disconnected. A change drops anything still queued. */
    public void setServer(String address) {
        if (address == null || !address.equals(serverAddress)) {
            queue.clear();
        }
        serverAddress = address;
    }

    /** True only while connected to a server the profile lists. */
    public boolean isActive() {
        return serverAddress != null && profile.matchesServer(serverAddress);
    }

    public String inactiveReason() {
        if (profile.isEmpty()) {
            return "no server profile is installed";
        }
        if (serverAddress == null) {
            return "not connected to a server";
        }
        return "this server is not in the profile";
    }

    public void onDisconnect() {
        setServer(null);
    }

    // ---- Chat ----

    /** A received line. Only the command in flight may claim it; nothing is ever sent from here. */
    public void onLine(CapturedLine line) {
        if (isActive()) {
            queue.offer(line);
        }
    }

    /** The username in the click value on a player's name, using the profile, or null. */
    public String nameFromClick(String clickValue) {
        if (clickValue == null) {
            return null;
        }
        Matcher m = profile.nameFromClick.match(clickValue);
        String name = m != null ? Rules.group(m, "name") : null;
        return name != null && USERNAME.matcher(name).matches() ? name : null;
    }

    /** Call once per client tick. */
    public void tick() {
        queue.tick();
    }

    // ---- Commands ----

    public boolean hasCommand(String templateId) {
        return profile.command(templateId) != null;
    }

    public String label(String templateId) {
        CommandTemplate t = profile.command(templateId);
        return t != null && t.label() != null && !t.label().isBlank() ? t.label() : templateId;
    }

    /** True when the command must be confirmed. A command the profile does not know is never sent. */
    public boolean needsConfirm(String templateId) {
        CommandTemplate t = profile.command(templateId);
        return t == null || t.confirm();
    }

    /** Extra commands the profile offers in the right-click menu. */
    public List<CommandTemplate> menuCommands() {
        return profile.commands.stream().filter(CommandTemplate::menu).toList();
    }

    /** Renders a template. Sends nothing. */
    public TemplateRenderer.Result render(String templateId, Map<String, String> values) {
        CommandTemplate t = profile.command(templateId);
        if (t == null) {
            return new TemplateRenderer.Result(null, List.of("the server profile has no \"" + templateId + "\" command"));
        }
        return TemplateRenderer.render(t.template(), values);
    }

    /** Queues a command. Only the UI calls this, from a click and after any confirmation. */
    public Submission submit(String templateId, Map<String, String> values, Consumer<Response> onDone) {
        if (!isActive()) {
            return new Submission(false, List.of("moderation tools are inactive: " + inactiveReason()));
        }
        TemplateRenderer.Result r = render(templateId, values);
        if (!r.ok()) {
            return new Submission(false, r.problems());
        }
        CommandTemplate t = profile.command(templateId);
        boolean isLookup = "lookup".equals(t.parser());
        CommandRequest request = new CommandRequest(templateId, r.command(), isLookup ? new LookupParser(profile) : null, response -> {
            if (response.parsed() instanceof LookupResult l) {
                String key = (l.name().isEmpty() ? values.getOrDefault("player", "") : l.name()).toLowerCase(Locale.ROOT);
                lookups.put(key, l);
                lookupTimes.put(key, clock.getAsLong());
            }
            if (onDone != null) {
                onDone.accept(response);
            }
        });
        if (!queue.submit(request)) {
            return new Submission(false, List.of("too many commands waiting, try again in a moment"));
        }
        return new Submission(true, List.of());
    }

    public LookupResult lookup(String name) {
        return lookups.get(name.toLowerCase(Locale.ROOT));
    }

    /** When the last lookup for this name arrived, or 0. */
    public long lookupTime(String name) {
        return lookupTimes.getOrDefault(name.toLowerCase(Locale.ROOT), 0L);
    }

    public int pendingCommands() {
        return queue.pending();
    }
}
