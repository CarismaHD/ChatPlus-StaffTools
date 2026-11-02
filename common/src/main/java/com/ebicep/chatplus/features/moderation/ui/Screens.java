package com.ebicep.chatplus.features.moderation.ui;

import java.util.Map;
import net.minecraft.client.gui.screens.Screen;

/** Public ways into the moderation screens, for code outside this package. */
public final class Screens {
    private Screens() {
    }

    /** The menu for a player's name, at the mouse position. */
    public static Screen nameMenu(Screen parent, String player, double mouseX, double mouseY) {
        return new NameMenuScreen(parent, player, mouseX, mouseY);
    }

    /** A player's lookup. With request true, the lookup command is sent if there is no result yet. */
    public static Screen lookup(Screen parent, String player, boolean request) {
        return new LookupScreen(parent, player, request);
    }

    /** The confirm and input popup for one command about a player. */
    public static Screen action(Screen parent, String templateId, String player) {
        return new ActionScreen(parent, templateId, player, Map.of());
    }
}
