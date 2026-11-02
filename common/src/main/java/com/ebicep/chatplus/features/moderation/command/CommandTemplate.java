package com.ebicep.chatplus.features.moderation.command;

/**
 * One command in the server profile.
 *
 * @param confirm ask before sending, showing the exact command
 * @param parser  "lookup" for the lookup command, so its output fills the lookup popup; otherwise null
 * @param menu    also offer it in the right-click name menu
 */
public record CommandTemplate(String id, String label, String template, boolean confirm, String parser, boolean menu) {
}
