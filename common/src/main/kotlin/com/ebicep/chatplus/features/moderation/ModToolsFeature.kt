package com.ebicep.chatplus.features.moderation

import com.ebicep.chatplus.events.EventBus
import com.ebicep.chatplus.features.moderation.ui.NameMenu
import com.ebicep.chatplus.hud.ChatManager
import com.ebicep.chatplus.hud.ChatScreenCloseEvent
import com.ebicep.chatplus.hud.ChatScreenKeyPressedEvent
import com.ebicep.chatplus.hud.ChatScreenMouseClickedEvent
import com.ebicep.chatplus.hud.ChatScreenMouseReleasedEvent
import com.ebicep.chatplus.hud.ChatScreenMouseScrolledEvent
import com.ebicep.chatplus.hud.ChatScreenRenderEvent
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/**
 * Connects the moderation tools to ChatPlus: the right-click name menu. The menu is drawn inside the open chat screen
 * rather than as a screen of its own, so chat stays open at full height, with its tabs and input box.
 */
object ModToolsFeature {

    init {
        // While the menu is open it takes every click. Otherwise a plain right-click on a player's name opens it. Both run
        // before line selection and stop it only when used, so right-clicking elsewhere still selects, and Shift or
        // Ctrl+right-click behave as before.
        var usedClick = false
        EventBus.register<ChatScreenMouseClickedEvent>({ 1000 }, { usedClick }) {
            usedClick = false
            if (NameMenu.isOpen()) {
                NameMenu.click(it.mouseX, it.mouseY, it.button)
                usedClick = true
                it.returnFunction = true
                return@register
            }
            if (it.button != 1 || !ModTools.active()) {
                return@register
            }
            if (Screen.hasShiftDown() || Screen.hasControlDown() || Screen.hasAltDown()) {
                return@register
            }
            val click = ChatManager.globalSelectedTab.getComponentStyleAt(it.mouseX, it.mouseY)?.clickEvent ?: return@register
            val player = ModTools.engine().nameFromClick(click.value) ?: return@register
            val renderer = ChatManager.globalSelectedTab.chatWindow.renderer
            NameMenu.show(it.screen, player, it.mouseX, it.mouseY, renderer.internalX, renderer.backgroundWidthEndX)
            usedClick = true
            it.returnFunction = true
        }
        // The release of a click the menu used belongs to the menu too, so it never ends a chat drag or selection.
        var usedRelease = false
        EventBus.register<ChatScreenMouseReleasedEvent>({ 1000 }, { usedRelease }) {
            usedRelease = usedClick
            usedClick = false
            if (usedRelease) {
                it.returnFunction = true
            }
        }
        // Escape closes only the menu, not chat.
        var usedKey = false
        EventBus.register<ChatScreenKeyPressedEvent>({ 1000 }, { usedKey }) {
            usedKey = NameMenu.isOpen() && it.keyCode == GLFW.GLFW_KEY_ESCAPE
            if (usedKey) {
                NameMenu.close()
                it.returnFunction = true
            }
        }
        // Scrolling moves the lines, so the menu would no longer point at the clicked name.
        EventBus.register<ChatScreenMouseScrolledEvent> {
            NameMenu.close()
        }
        EventBus.register<ChatScreenRenderEvent> {
            NameMenu.render(it.guiGraphics, it.mouseX, it.mouseY)
        }
        EventBus.register<ChatScreenCloseEvent> {
            NameMenu.close()
        }
    }
}
