package com.ebicep.chatplus.features.moderation

import com.ebicep.chatplus.features.moderation.ui.Screens
import com.ebicep.chatplus.features.textbarelements.TextBarElement
import com.ebicep.chatplus.mixin.IMixinScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

/** The "P" button on ChatPlus's chat bar, which opens the player list. Only added on servers in the profile. */
class PlayerListTextBarElement(private val chatPlusScreen: ChatScreen) : TextBarElement {

    override fun getWidth(): Int {
        return Minecraft.getInstance().font.width("P")
    }

    override fun getText(): String {
        return "P"
    }

    override fun onClick(button: Int) {
        if (button != 0) {
            return
        }
        Minecraft.getInstance().setScreen(Screens.players(chatPlusScreen))
    }

    override fun onHover(guiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int) {
        guiGraphics.renderTooltip((chatPlusScreen as IMixinScreen).font, Component.translatable("chatPlus.moderation.playerList.tooltip"), pMouseX, pMouseY)
    }

    override fun onRender(guiGraphics: GuiGraphics, currentX: Int, currentY: Int, mouseX: Int, mouseY: Int, partialTick: Float) {
        fill(guiGraphics, currentX, currentY)
        drawCenteredString(guiGraphics, currentX, currentY, -1)
    }

}
