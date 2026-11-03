package com.ebicep.chatplus.features.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.ebicep.chatplus.features.moderation.core.NameSimilarity;
import com.ebicep.chatplus.features.moderation.core.SlashGuard;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/** The missing-slash guard and the look-alike name check. */
class GuardTest {
    /** A made-up server: "guard" has literal sub-commands; "tp" and "help" only take arguments. */
    private static final Map<String, Set<String>> COMMANDS = Map.of(
            "guard", Set.of("strike", "timeout", "record"),
            "tp", Set.of(),
            "help", Set.of());
    private static final BiPredicate<String, String> SUBCOMMAND = (first, second) -> COMMANDS.getOrDefault(first, Set.of()).contains(second);

    private static boolean held(String text, List<String> guardWords) {
        return SlashGuard.shouldHold(text, guardWords, SUBCOMMAND);
    }

    @Test
    void holdsACommandWithASubcommand() {
        assertTrue(held("guard timeout Steve 1d griefing", List.of()));
        assertTrue(held("GUARD Strike Steve spam", List.of()));
    }

    @Test
    void letsOrdinarySentencesThrough() {
        assertFalse(held("help me please", List.of()));
        assertFalse(held("tp Steve", List.of()));
        assertFalse(held("guard the base tonight", List.of()), "'the' isn't a sub-command");
        assertFalse(held("hello", List.of()));
        assertFalse(held("   ", List.of()));
    }

    @Test
    void neverTouchesRealCommands() {
        assertFalse(held("/guard timeout Steve 1d", List.of("guard")));
        assertFalse(held("  /staff hi", List.of("staff")));
    }

    @Test
    void holdsProfileGuardWordsEvenAlone() {
        assertTrue(held("staff did anyone see that", List.of("staff")));
        assertTrue(held("STAFF", List.of("staff")));
        assertTrue(held("tp Steve", List.of("tp")));
        assertFalse(held("staffing the shop", List.of("staff")), "only the whole first word counts");
    }

    @Test
    void looksAlikeAfterCaseUnderscoresAndDigits() {
        assertTrue(NameSimilarity.confusable("Steve", "steve_"));
        assertTrue(NameSimilarity.confusable("Bob1", "Bobl"));
        assertTrue(NameSimilarity.confusable("Sn0wy", "Snowy"));
        assertTrue(NameSimilarity.confusable("Mi1ler", "MiIler"));
        assertTrue(NameSimilarity.confusable("Lag5", "LagS"));
    }

    @Test
    void looksAlikeOneCharacterApart() {
        assertTrue(NameSimilarity.confusable("Steve", "Steeve"));
        assertTrue(NameSimilarity.confusable("Steve", "Stove"));
        assertTrue(NameSimilarity.confusable("Steve", "Stev"));
    }

    @Test
    void differentNamesDontLookAlike() {
        assertFalse(NameSimilarity.confusable("Steve", "Steve"), "the same player");
        assertFalse(NameSimilarity.confusable("Steve", "Alex"));
        assertFalse(NameSimilarity.confusable("Bob", "Rob"), "too short to judge one character apart");
        assertFalse(NameSimilarity.confusable("Steve", "Stevenson"));
    }

    @Test
    void listsLookAlikesInOrder() {
        assertEquals(List.of("steve_", "Steeve"), NameSimilarity.lookAlikes("Steve", List.of("Alex", "steve_", "Steve", "Steeve", "steve_")));
    }
}
