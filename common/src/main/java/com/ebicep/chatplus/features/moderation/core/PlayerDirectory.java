package com.ebicep.chatplus.features.moderation.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every player seen online on one server, for the player list. It is the one place the tools keep anything about
 * players on disk: the username, UUID, last known nickname, and when they were first and last seen. Nothing else, and
 * only for servers in the profile.
 */
public final class PlayerDirectory {
    /** @param nick the nickname from the tab list, or null when there is none or it is just the username */
    public record Player(UUID id, String name, String nick, long firstSeen, long lastSeen) {
    }

    /** How a player is stored in the file. Plain fields, so the file stays readable. */
    private static final class Saved {
        String uuid;
        String name;
        String nick;
        long firstSeen;
        long lastSeen;
    }

    private static final Gson GSON = new Gson();
    /**
     * A real Minecraft username. Servers often fill the tab list with fake entries for its layout, named like "~00",
     * and those aren't players.
     */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final Map<UUID, Player> players = new HashMap<>();
    private Set<UUID> online = Set.of();
    private boolean dirty;

    /**
     * Records who is online now. A known UUID with a new name keeps its first-seen time and takes the new name. Entries
     * without a real username are left out.
     */
    public void update(Map<UUID, String> nowOnline, long now) {
        update(nowOnline, Map.of(), now);
    }

    /** As above, with nicknames by UUID. A player without one now keeps the last one known. */
    public void update(Map<UUID, String> nowOnline, Map<UUID, String> nicknames, long now) {
        Set<UUID> ids = new HashSet<>();
        for (Map.Entry<UUID, String> e : nowOnline.entrySet()) {
            if (!isUsername(e.getValue())) {
                continue;
            }
            Player old = players.get(e.getKey());
            long first = old != null ? old.firstSeen() : now;
            String nick = nicknames.get(e.getKey());
            if (nick == null && old != null) {
                nick = old.nick();
            }
            players.put(e.getKey(), new Player(e.getKey(), e.getValue(), nick, first, now));
            ids.add(e.getKey());
        }
        online = Set.copyOf(ids);
        dirty |= !ids.isEmpty();
    }

    /**
     * Takes a last-seen time from the server, such as a /seen reply. It only moves the time forward, since the server
     * may know of a visit this client missed. Returns true when it changed anything.
     */
    public boolean markSeen(String name, long when) {
        Optional<Player> found = byName(name);
        if (found.isEmpty() || when <= found.get().lastSeen()) {
            return false;
        }
        Player p = found.get();
        players.put(p.id(), new Player(p.id(), p.name(), p.nick(), Math.min(p.firstSeen(), when), when));
        dirty = true;
        return true;
    }

    public static boolean isUsername(String name) {
        return name != null && USERNAME.matcher(name).matches();
    }

    /** Forgets everyone, for a server that isn't in the profile. */
    public void clear() {
        players.clear();
        online = Set.of();
        dirty = false;
    }

    /** Nobody counts as online after leaving the server. Last-seen times stay. */
    public void clearOnline() {
        online = Set.of();
    }

    public boolean isOnline(UUID id) {
        return online.contains(id);
    }

    public boolean isOnline(String name) {
        return byName(name).map(p -> online.contains(p.id())).orElse(false);
    }

    /** True once someone has been seen online in this session, so "not online" means something. */
    public boolean knowsWhoIsOnline() {
        return !online.isEmpty();
    }

    public Optional<Player> byName(String name) {
        return players.values().stream().filter(p -> p.name().equalsIgnoreCase(name)).max(Comparator.comparingLong(Player::lastSeen));
    }

    public List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (UUID id : online) {
            Player p = players.get(id);
            if (p != null) {
                names.add(p.name());
            }
        }
        return names;
    }

    public int size() {
        return players.size();
    }

    public int onlineCount() {
        return online.size();
    }

    /** Players whose name or nickname contains the query, ignoring case. Online first, then the most recently seen. */
    public List<Player> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Player> result = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.name().toLowerCase(Locale.ROOT).contains(q) || p.nick() != null && p.nick().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(p);
            }
        }
        result.sort(Comparator.comparing((Player p) -> !online.contains(p.id()))
                .thenComparing(Comparator.comparingLong(Player::lastSeen).reversed())
                .thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    public boolean dirty() {
        return dirty;
    }

    /** Replaces what is known with the file's contents. A missing file means nobody seen yet. */
    public void load(Path file) throws IOException {
        players.clear();
        online = Set.of();
        dirty = false;
        if (!Files.exists(file)) {
            return;
        }
        List<Saved> saved;
        try {
            saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Saved>>() {
            }.getType());
        } catch (JsonParseException e) {
            throw new IOException(file.getFileName() + " could not be read: " + e.getMessage(), e);
        }
        if (saved == null) {
            return;
        }
        for (Saved s : saved) {
            if (s == null || s.uuid == null || !isUsername(s.name)) {
                // Also drops tab-list fillers saved by an older version, so they disappear on the next save.
                dirty = true;
                continue;
            }
            try {
                UUID id = UUID.fromString(s.uuid);
                players.put(id, new Player(id, s.name, s.nick, s.firstSeen, s.lastSeen));
            } catch (IllegalArgumentException ignored) {
                // A hand-edited line with a broken UUID is skipped rather than losing the whole list.
            }
        }
    }

    /** Writes through a temporary file, so a crash while saving never leaves a half-written list. */
    public void save(Path file) throws IOException {
        List<Saved> out = new ArrayList<>();
        for (Player p : sortedByName(players.values())) {
            Saved s = new Saved();
            s.uuid = p.id().toString();
            s.name = p.name();
            s.nick = p.nick();
            s.firstSeen = p.firstSeen();
            s.lastSeen = p.lastSeen();
            out.add(s);
        }
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(out), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        dirty = false;
    }

    private static List<Player> sortedByName(Collection<Player> players) {
        List<Player> list = new ArrayList<>(players);
        list.sort(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return list;
    }

    /** A file name for a server address: lowercase host, no port, only safe characters. */
    public static String fileNameFor(String address) {
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0) {
            host = host.substring(0, colon);
        }
        return host.replaceAll("[^a-z0-9.-]", "_") + ".json";
    }
}
