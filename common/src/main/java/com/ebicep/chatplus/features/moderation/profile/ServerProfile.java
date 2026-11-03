package com.ebicep.chatplus.features.moderation.profile;

import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything specific to one server: where the tools are active, its commands, and how its lookup output looks.
 * Nothing here is built into the mod. The bundled default is empty, so the tools stay inactive
 * until a profile is installed.
 *
 * <p>Patterns are regexes matched against a whole line, using named groups for the data. An empty list turns that
 * feature off.
 */
public final class ServerProfile {
    /** Hosts where the tools are active. A host also matches its subdomains. */
    public List<String> servers = new ArrayList<>();
    /** Removed from the start of a line before matching, for icons a server puts in front of text. */
    public List<String> stripPrefixes = new ArrayList<>();
    public Chat chat = new Chat();
    public Lookup lookup = new Lookup();
    public List<CommandTemplate> commands = new ArrayList<>();
    /** Put in place of {@code {silent}} when Silent is ticked, such as a command's quiet flag. Empty hides Silent. */
    public String silentFlag = "";
    /**
     * First words of commands that must never go to public chat if typed without their slash, such as a staff-chat
     * command. A chat message starting with one is held back.
     */
    public List<String> slashGuard = new ArrayList<>();
    /** Read but not used in this version. Set tabs up in ChatPlus itself. */
    public List<Tab> tabs = new ArrayList<>();

    public static final class Chat {
        /** Applied to the click value on a player's name, such as a private message command. Group {@code name}. */
        public List<String> nameFromClick = new ArrayList<>();
        /** Normal chat lines, so they are never mistaken for lookup output. */
        public List<String> chatLines = new ArrayList<>();
        /**
         * Applied to a player's name in the tab list to find their nickname, group {@code nick}, such as a pattern that
         * skips the rank. Without one, leading [bracketed] tags are removed and the rest is the nickname.
         */
        public List<String> nickFromTab = new ArrayList<>();
    }

    public static final class Lookup {
        /** Group {@code name}. */
        public List<String> start = new ArrayList<>();
        /** Groups {@code kind}, {@code count}. */
        public List<String> count = new ArrayList<>();
        /** Groups {@code id}, {@code time}, {@code staff}, {@code text}, and optionally {@code status}. */
        public List<String> entry = new ArrayList<>();
        /** Group {@code header}. */
        public List<String> section = new ArrayList<>();
        /** Values of the {@code kind} group. */
        public String warningsCount = "";
        public String notesCount = "";
        public String bansCount = "";
        /** Values of the {@code header} group. */
        public String warningsSection = "";
        public String notesSection = "";
    }

    /** A ChatPlus tab, with a regex its lines must match. */
    public static final class Tab {
        public String name = "";
        public String pattern = "";
    }
}
