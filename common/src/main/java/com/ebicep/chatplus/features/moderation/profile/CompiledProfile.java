package com.ebicep.chatplus.features.moderation.profile;

import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.parse.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** A server profile with its patterns compiled. Invalid patterns are skipped and listed in {@link #errors}. */
public final class CompiledProfile {
    public final List<String> errors = new ArrayList<>();
    public final List<String> servers;
    public final List<CommandTemplate> commands;
    public final List<ServerProfile.Tab> tabs;
    public final String silentFlag;
    /** Lowercase, without a leading slash. */
    public final List<String> slashGuard;
    public final Rules stripPrefixes;
    public final Rules nameFromClick;
    public final Rules chatLines;
    public final Rules nickFromTab;
    public final Rules lookupStart;
    public final Rules lookupCount;
    public final Rules lookupEntry;
    public final Rules lookupSection;
    public final ServerProfile.Lookup lookup;

    private CompiledProfile(ServerProfile p) {
        ServerProfile.Chat chat = p.chat != null ? p.chat : new ServerProfile.Chat();
        ServerProfile.Lookup lk = p.lookup != null ? p.lookup : new ServerProfile.Lookup();
        servers = p.servers != null ? p.servers.stream().filter(s -> s != null && !s.isBlank()).toList() : List.of();
        commands = p.commands != null ? p.commands.stream().filter(c -> c != null && c.id() != null && c.template() != null).toList() : List.of();
        for (CommandTemplate c : commands) {
            if (c.reply() != null) {
                // Only to report bad patterns through /mt; the parser compiles its own copy.
                new Rules("command \"" + c.id() + "\" reply", c.reply(), errors);
            }
        }
        List<ServerProfile.Tab> validTabs = new ArrayList<>();
        if (p.tabs != null) {
            for (ServerProfile.Tab t : p.tabs) {
                if (t == null || t.name == null || t.name.isBlank() || t.pattern == null || t.pattern.isBlank()) {
                    continue;
                }
                try {
                    Pattern.compile(t.pattern);
                    validTabs.add(t);
                } catch (PatternSyntaxException e) {
                    errors.add("tabs \"" + t.name + "\": " + e.getDescription());
                }
            }
        }
        tabs = List.copyOf(validTabs);
        silentFlag = p.silentFlag == null ? "" : p.silentFlag.trim();
        List<String> guard = new ArrayList<>();
        if (p.slashGuard != null) {
            for (String w : p.slashGuard) {
                String word = w == null ? "" : w.trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
                if (!word.isEmpty() && !word.contains(" ")) {
                    guard.add(word);
                } else if (w != null && !w.isBlank()) {
                    errors.add("slashGuard \"" + w + "\": must be a single word");
                }
            }
        }
        slashGuard = List.copyOf(guard);
        stripPrefixes = new Rules("stripPrefixes", p.stripPrefixes, errors);
        nameFromClick = new Rules("chat.nameFromClick", chat.nameFromClick, errors);
        chatLines = new Rules("chat.chatLines", chat.chatLines, errors);
        nickFromTab = new Rules("chat.nickFromTab", chat.nickFromTab, errors);
        lookupStart = new Rules("lookup.start", lk.start, errors);
        lookupCount = new Rules("lookup.count", lk.count, errors);
        lookupEntry = new Rules("lookup.entry", lk.entry, errors);
        lookupSection = new Rules("lookup.section", lk.section, errors);
        lookup = lk;
    }

    public static CompiledProfile compile(ServerProfile profile) {
        return new CompiledProfile(profile != null ? profile : new ServerProfile());
    }

    /** True when no server is listed, which means the tools are inactive everywhere. */
    public boolean isEmpty() {
        return servers.isEmpty();
    }

    /** True when the address is one of the profile's servers or a subdomain of one. A port and letter case are ignored. */
    public boolean matchesServer(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String s : servers) {
            String server = s.trim().toLowerCase(Locale.ROOT);
            if (host.equals(server) || host.endsWith("." + server)) {
                return true;
            }
        }
        return false;
    }

    /** Generic cleanup, then the profile's own prefix stripping. */
    public String clean(String raw) {
        return stripPrefixes.stripStart(Text.clean(raw));
    }

    public CommandTemplate command(String id) {
        for (CommandTemplate t : commands) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }
}
