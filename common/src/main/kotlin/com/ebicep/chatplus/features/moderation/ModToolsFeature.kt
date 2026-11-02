package com.ebicep.chatplus.features.moderation

import com.ebicep.chatplus.events.EventBus
import com.ebicep.chatplus.features.moderation.ui.Screens
import com.ebicep.chatplus.hud.ChatManager
import com.ebicep.chatplus.hud.ChatScreenMouseClickedEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/** Connects the moderation tools to ChatPlus: the right-click name menu. */
object ModToolsFeature {

    init {
        // A plain right-click on a player's name opens the menu. It runs before line selection and stops it only when
        // a name was hit, so right-clicking elsewhere still selects, and Shift or Ctrl+right-click behave as before.
        var openedMenu = false
        EventBus.register<ChatScreenMouseClickedEvent>({ 1000 }, { openedMenu }) {
            openedMenu = false
            if (it.button != 1 || !ModTools.active()) {
                return@register
            }
            if (Screen.hasShiftDown() || Screen.hasControlDown() || Screen.hasAltDown()) {
                return@register
            }
            val click = ChatManager.globalSelectedTab.getComponentStyleAt(it.mouseX, it.mouseY)?.clickEvent ?: return@register
            val player = ModTools.engine().nameFromClick(click.value) ?: return@register
            openedMenu = true
            it.returnFunction = true
            Minecraft.getInstance().setScreen(Screens.nameMenu(it.screen, player, it.mouseX, it.mouseY))
        }
    }
}
