package com.ebicep.chatplus.features.moderation.command;

/** The one place a command leaves the mod. Tests use a fake, so test runs can never reach a server. */
public interface CommandSender {
    /** Sends a command without its leading slash. Returns false when not connected. */
    boolean send(String commandWithoutSlash);
}
