package com.ebicep.chatplus.neoforge

import com.ebicep.chatplus.MOD_ID
import com.ebicep.chatplus.features.moderation.ModTools
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Feeds game events to the moderation tools. */
@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object ModToolsEvents {

    /** Lowest priority and receiveCanceled, so lookup replies other mods hide are still read. Never cancels or edits. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    fun onChat(event: ClientChatReceivedEvent) {
        if (event is ClientChatReceivedEvent.System && event.isOverlay) {
            return
        }
        ModTools.onChat(event.message)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        ModTools.onTick()
    }

    @SubscribeEvent
    fun onLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        ModTools.onLogin()
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        ModTools.onLogout()
    }
}
