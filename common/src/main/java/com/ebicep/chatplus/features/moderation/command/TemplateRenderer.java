package com.ebicep.chatplus.features.moderation.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills a template and refuses anything that could send the wrong command. */
public final class TemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    /** May render empty. Everything else is required. */
    private static final Set<String> OPTIONAL = Set.of("silent");
    /** A space here would shift later arguments and could change who or what the command applies to. */
    private static final Set<String> SINGLE_TOKEN = Set.of("player", "duration", "window", "team", "teamId");
    private static final Set<String> DIGITS_ONLY = Set.of("warningId", "noteId", "id");

    /** Either a command ready to send, or the reasons it was refused. */
    public record Result(String command, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private TemplateRenderer() {
    }

    public static Result render(String template, Map<String, String> values) {
        List<String> problems = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = sanitize(values.getOrDefault(key, ""));
            if (value.isEmpty() && !OPTIONAL.contains(key)) {
                problems.add(key + " is required");
            } else if (SINGLE_TOKEN.contains(key) && value.contains(" ")) {
                problems.add(key + " cannot contain spaces");
            } else if (DIGITS_ONLY.contains(key) && !value.isEmpty() && !value.chars().allMatch(Character::isDigit)) {
                problems.add(key + " must be a number");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        String command = out.toString().replaceAll("\\s+", " ").strip();
        return new Result(problems.isEmpty() ? command : null, problems);
    }

    /** Servers can disconnect a client for section signs or control characters, so they never get sent. */
    static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                out.append(' ');
            } else if (cp != '§' && !Character.isISOControl(cp)) {
                out.appendCodePoint(cp);
            }
        });
        return out.toString().strip();
    }
}
