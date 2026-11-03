package com.ebicep.chatplus.features.moderation.command;

import java.util.List;

/**
 * One command in the server profile.
 *
 * @param confirm ask before sending, showing the exact command
 * @param parser  "lookup" for the lookup command, so its output fills the lookup popup; "lines" to show the reply as
 *                it is; otherwise null
 * @param menu    also offer it in the right-click name menu
 * @param reply   for "lines": patterns for the lines that make up the reply, so other server messages arriving at the
 *                same time aren't taken for it. Optional.
 */
public record CommandTemplate(String id, String label, String template, boolean confirm, String parser, boolean menu, List<String> reply) {
}
