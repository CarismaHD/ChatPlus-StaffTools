package com.ebicep.chatplus.neoforge.devtest

import com.ebicep.chatplus.MOD_ID
import com.ebicep.chatplus.config.Config
import com.ebicep.chatplus.config.ConfigDirectory
import com.ebicep.chatplus.config.ConfigScreen
import com.ebicep.chatplus.features.SelectChat
import com.ebicep.chatplus.features.TranslateMessage
import com.ebicep.chatplus.features.chattabs.ChatTab
import com.ebicep.chatplus.features.moderation.ModTools
import com.ebicep.chatplus.features.moderation.ui.NameMenu
import com.ebicep.chatplus.features.moderation.ui.Screens
import com.ebicep.chatplus.mixin.IMixinChatScreen
import com.ebicep.chatplus.hud.ChatManager
import com.ebicep.chatplus.translator.LanguageManager
import com.ebicep.chatplus.translator.SenderPrefix
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Offline self-test for development. It only runs with -Dchatplus.stafftools.selftest=true, which only the runSelfTest task
 * sets, and it is excluded from the mod jar. It makes a throwaway singleplayer world, so nothing ever reaches a real
 * server, and uses the made-up example profile from the test resources.
 */
@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object SelfTest {
    private val enabled = System.getProperty("chatplus.stafftools.selftest") == "true"
    private val results = mutableListOf<String>()
    private val steps = ArrayDeque<Pair<Int, () -> Unit>>()
    private var phase = 0
    private var wait = 0
    private var nameX = 0.0
    private var nameY = 0.0
    private var lookupsAnswered = 0

    init {
        if (enabled) {
            installExampleProfile()
        }
    }

    /** Copies the made-up example profile into the run folder's config, as a user would install a real one. */
    private fun installExampleProfile() {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null && !Files.exists(dir.resolve("common/src/test/resources/example-profile.json"))) {
            dir = dir.parent
        }
        val source = dir?.resolve("common/src/test/resources/example-profile.json") ?: error("example profile not found")
        val target = ConfigDirectory.getConfigDirectory().resolve("chatplus/moderation/profile.json")
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun check(name: String, ok: Boolean, detail: String = "") {
        val line = (if (ok) "PASS " else "FAIL ") + name + if (detail.isNotEmpty()) " ($detail)" else ""
        results += line
        println("[SelfTest] $line")
    }

    private fun note(text: String) = println("[SelfTest] NOTE $text")

    private var pendingShot: String? = null

    /** Takes the screenshot after the next full frame, so it shows exactly what a player would see. */
    private fun shot(name: String) {
        pendingShot = name
    }

    @SubscribeEvent
    fun onFrame(event: RenderFrameEvent.Post) {
        val name = pendingShot ?: return
        pendingShot = null
        val mc = Minecraft.getInstance()
        Screenshot.grab(mc.gameDirectory, "selftest-$name.png", mc.mainRenderTarget) {}
    }

    private fun step(waitTicks: Int, action: () -> Unit) {
        steps.addLast(waitTicks to action)
    }

    private fun named(name: String, click: String): MutableComponent =
        Component.literal(name).setStyle(Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, click)))

    private fun receive(component: Component) {
        Minecraft.getInstance().chatListener.handleSystemMessage(component, false)
    }

    private fun receive(text: String) = receive(Component.literal(text))

    private fun tabLines(): List<ChatTab.ChatPlusGuiMessage> = ChatManager.globalSelectedTab.messages

    private fun screenName(): String = Minecraft.getInstance().screen?.javaClass?.simpleName ?: "none"

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled) {
            return
        }
        val mc = Minecraft.getInstance()
        when (phase) {
            0 -> if (mc.screen is TitleScreen && mc.overlay == null) {
                phase = 1
                val settings = LevelSettings("stafftools-selftest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT)
                mc.createWorldOpenFlows().createFreshLevel("stafftools-selftest", settings, WorldOptions(12345L, false, false),
                    WorldPresets::createNormalWorldDimensions, TitleScreen())
            }

            1 -> if (mc.player != null && mc.level != null && mc.screen == null) {
                phase = 2
                wait = 60
                planSteps()
            }

            2 -> {
                if (wait-- > 0) {
                    return
                }
                val next = steps.removeFirstOrNull()
                if (next == null) {
                    finish()
                    return
                }
                try {
                    next.second()
                } catch (e: Throwable) {
                    check("step threw", false, e.toString())
                    e.printStackTrace()
                }
                wait = steps.firstOrNull()?.first ?: 5
            }
        }
    }

    /** Clicks a row of the open name menu. Row 0 is the first command. */
    private fun clickMenuRow(screen: Screen, row: Int) {
        val (x, y) = NameMenu.rowCenter(row)!!
        click(screen, x, y, 0)
    }

    /** True when the open menu covers any part of the clicked line: its whole width, and its full height around the click. */
    private fun menuCoversLine(): Boolean {
        val renderer = ChatManager.globalSelectedTab.chatWindow.renderer
        val lineHeight = kotlin.math.ceil(renderer.lineHeight * renderer.scale).toInt()
        return (0..renderer.backgroundWidthEndX step 2).any { x ->
            (-lineHeight..lineHeight).any { dy -> ChatManager.globalSelectedTab.getComponentStyleAt(x.toDouble(), nameY + dy) != null &&
                    sameLine(nameY + dy) && NameMenu.covers(x.toDouble(), nameY + dy) }
        }
    }

    /** True when screen row y shows the same chat line as the clicked name. */
    private fun sameLine(y: Double): Boolean =
        ChatManager.globalSelectedTab.getHoveredOverMessageLine(nameX, y) == ChatManager.globalSelectedTab.getHoveredOverMessageLine(nameX, nameY)

    /** Finds the decorated name on screen by asking ChatPlus which text sits under each point. Lines move as chat arrives. */
    private fun locateName(screen: Screen): Boolean {
        for (y in screen.height downTo 0 step 2) {
            for (x in 0..screen.width / 2 step 2) {
                val click = ChatManager.globalSelectedTab.getComponentStyleAt(x.toDouble(), y.toDouble())?.clickEvent
                if (click?.value == "/whisper RealBob ") {
                    nameX = x + 1.0
                    nameY = y + 1.0
                    return true
                }
            }
        }
        return false
    }

    private fun openMenuOnName(mc: Minecraft) {
        val screen = mc.screen!!
        locateName(screen)
        click(screen, nameX, nameY, 1)
    }

    /** A full click, press and release. A press without a release starts dragging ChatPlus's movable chat. */
    private fun click(screen: Screen, x: Double, y: Double, button: Int) {
        screen.mouseClicked(x, y, button)
        screen.mouseReleased(x, y, button)
    }

    private fun planSteps() {
        val mc = Minecraft.getInstance()
        val engine = ModTools.engine()
        // Rows for the example profile: Record, Tell, Remark, Strike, Timeout, Rescue, Where?, Inventory, Copy name.

        step(0) {
            check("tools inactive in singleplayer", !ModTools.active())
            // A copied config remembers whichever tab was last open; the test chat goes to the first tab.
            Config.values.chatWindows.first().tabSettings.selectedTabIndex = 0
            // Stand in for being on the example server. The connection is still the local singleplayer one. The saved
            // player list from an earlier run is removed first, so this run starts clean.
            Files.deleteIfExists(ModTools.dir().resolve("players").resolve("example.net.json"))
            ModTools.startSession("example.net")
            check("tools active once the server is in the profile", ModTools.active())
            check("example profile loaded without errors", engine.errors().isEmpty(), engine.errors().toString())
            // Someone seen three days ago, so the player list has an offline player too.
            ModTools.players().update(mapOf(UUID.randomUUID() to "OldTimer"), System.currentTimeMillis() - 3L * 24 * 3600 * 1000)
        }
        step(5) {
            receive(Component.literal("Member | ").append(named("Alice", "/whisper Alice ")).append(" » hello there"))
            receive(Component.literal("Elder | ").append(named("𝕭𝖔𝖇 ☽", "/whisper RealBob ")).append(" » anyone got a dupe glitch"))
            receive("Open tickets (48h)")
            receive("T7 @hub issue: \"my chest vanished\" from Carol [Reply]")
            receive("{Watch} Zed moving 40.5 b/s over 5.0s at 10,64,-20 in nether")
            receive(Component.literal("Member | ").append(named("Carlos", "/whisper Carlos ")).append(" » Hola amigo. ¿Cómo estás? Me gusta este servidor."))
            receive(Component.literal("Member | ").append(named("Jean", "/whisper Jean ")).append(" » Bonjour tout le monde"))
            receive(Component.literal("Member | ").append(named("Jean", "/whisper Jean ")).append(" » Je suis fatigué"))
        }
        step(5) {
            val tabs = Config.values.chatWindows.first().tabSettings.tabs.map { it.name }
            note("tabs from the ChatPlus config: $tabs")
            check("chat shown by ChatPlus", tabLines().any { it.guiMessage.content.string.contains("hello there") })
            check("nothing queued by receiving chat", engine.pendingCommands() == 0)
            mc.setScreen(ChatScreen(""))
        }
        step(10) {
            shot("chat-open")
        }
        step(3) {
            check("name located in rendered chat", locateName(mc.screen!!))
            openMenuOnName(mc)
            check("right-click on a name opens the menu", NameMenu.isOpen(), screenName())
            check("chat stays open behind the menu, at full height", mc.screen is ChatScreen, screenName())
            check("menu does not cover the clicked line", NameMenu.isOpen() && !menuCoversLine())
        }
        step(10) {
            shot("name-menu")
        }
        step(3) {
            val screen = mc.screen!!
            clickMenuRow(screen, 3)
            check("Strike opens the reason popup", screenName() == "ActionScreen", screenName())
            val recent = ModTools.recentMessages("RealBob", 3)
            check("the strike screen shows their last message", recent.size == 1 && recent[0].contains("anyone got a dupe glitch"), recent.toString())
        }
        step(5) {
            "self test only".forEach { mc.screen!!.charTyped(it, 0) }
            val screen = mc.screen!!
            button(screen, "Insert location").onPress()
            button(screen, "Chunk owner").onPress()
            val me = mc.player!!.blockPosition()
            val reason = screen.children().filterIsInstance<EditBox>().first().value
            check("Insert location adds where I am", reason == "self test only at ${me.x} ${me.y} ${me.z} (overworld)", reason)
            check("typing in the reason box isn't taken for chat shortcuts", ChatManager.isTypingElsewhere())
        }
        step(60) {
            shot("strike-preview")
            check("the chunk owner check got its reply", engine.pendingCommands() == 0)
        }
        step(3) {
            check("nothing queued while the popup is open", engine.pendingCommands() == 0)
            mc.screen!!.keyPressed(256, 0, 0)
            check("Escape cancels back to chat", mc.screen is ChatScreen, screenName())
        }
        step(30) {
            check("cancel sent nothing", engine.pendingCommands() == 0)
            // A plain right-click past the name, on the message text, must still select the line as ChatPlus always did.
            // A right-click on the message text, past the name, must be left to ChatPlus. Its line selection follows the
            // real mouse pointer, which this test does not move, so selection itself is checked by hand in game.
            val screen = mc.screen!!
            check("name found again after returning to chat", locateName(screen))
            var x = nameX
            while (x < screen.width / 2 && ChatManager.globalSelectedTab.getComponentStyleAt(x, nameY)?.clickEvent != null) {
                x += 2
            }
            val consumed = screen.mouseClicked(x + 20, nameY, 1)
            screen.mouseReleased(x + 20, nameY, 1)
            check("right-click on message text is left to ChatPlus", !consumed && mc.screen is ChatScreen, screenName())
            SelectChat.getAllSelectedMessages().clear()
        }
        step(5) {
            openMenuOnName(mc)
        }
        step(3) {
            mc.screen!!.keyPressed(256, 0, 0)
            check("Escape closes only the menu, chat stays open", !NameMenu.isOpen() && mc.screen is ChatScreen, screenName())
        }
        step(5) {
            openMenuOnName(mc)
        }
        step(5) {
            val screen = mc.screen!!
            check("menu opens again", NameMenu.isOpen() && mc.screen is ChatScreen, screenName())
            clickMenuRow(screen, 0)
            check("Record opens the lookup popup", screenName() == "LookupScreen", screenName())
        }
        step(2) {
            // The local singleplayer server cannot answer, so feed the reply a real server would print.
            receive("-- Record: RealBob --")
            receive("• Strikes = 2")
            receive("• Remarks = 1")
            receive("• Bans active = 1")
            receive("Member | Alice » chat in the middle")
            receive("Strikes>")
            receive("  12) <ACTIVE> 2026-01-02 10:00 by Mod_A :: Hub :: please clear the lag farm before the weekend")
            receive("  11) 2026-01-01 09:00 by Mod_B :: first part")
            receive("  second part of a wrapped warning")
            receive("Remarks>")
            receive("  5) 2026-01-03 08:00 by Mod_A :: see https://logs.example.org/abc")
        }
        step(50) {
            val l = engine.lookup("RealBob")
            check("lookup reply parsed", l != null && l.warningEntries().size == 2 && l.noteEntries().size == 1 && l.activeBans() == 1,
                l?.toString() ?: "none")
            shot("lookup")
        }
        step(3) {
            mc.setScreen(ChatScreen(""))
        }
        step(5) {
            val english = LanguageManager.findLanguageFromName("English")!!
            // The same split a Ctrl+click makes: the sender stays as it is and only the message goes to Google.
            fun translate(lines: List<ChatTab.ChatPlusGuiMessage>) {
                val parts = lines.map { SenderPrefix.split(it.guiMessage.content.string) }
                TranslateMessage.ClickTranslator(lines, parts.map { it.first }, parts.map { it.second }, english).start()
            }
            translate(listOf(tabLines().first { it.guiMessage.content.string.contains("Hola amigo") }))
            translate(tabLines().filter { it.guiMessage.content.string.contains("Jean") })
        }
        step(100) {
            val texts = tabLines().map { it.guiMessage.content.string }
            val spanish = texts.firstOrNull { it.contains("(Spanish)") }
            check("Spanish line translated", spanish != null, spanish ?: texts.takeLast(3).toString())
            check("translation has no double spaces", spanish != null && !spanish.contains("  "), spanish ?: "")
            check("translation keeps the sender in front", spanish?.startsWith("Member | Carlos » ") == true, spanish ?: "")
            val french = texts.filter { it.contains("(French)") }
            check("two selected French lines give two translations", french.size == 2, french.toString())
            shot("translated")
        }
        step(3) {
            mc.setScreen(ConfigScreen.getConfigScreen(null))
        }
        step(20) {
            check("ChatPlus settings screen opens", mc.screen != null && mc.screen !is ChatScreen, screenName())
            shot("settings")
        }
        step(3) {
            mc.setScreen(null)
        }

        // The player list: /mt players opens it on the next tick.
        step(5) {
            ModTools.openPlayers()
        }
        step(5) {
            check("/mt players opens the player list", screenName() == "PlayerListScreen", screenName())
            val me = mc.player!!.gameProfile.name
            check("the player list has me online", ModTools.players().isOnline(me), ModTools.players().onlineNames().toString())
            check("the player list keeps players who left", ModTools.players().byName("OldTimer").isPresent && !ModTools.players().isOnline("OldTimer"))
        }
        step(10) {
            shot("player-list")
        }
        step(3) {
            val screen = mc.screen!!
            val row = Screens.playerRow(screen, "OldTimer")
            check("an offline player is listed", row != null)
            if (row != null) {
                click(screen, row[0], row[1], 0)
            }
            check("clicking a player opens the name menu", NameMenu.isOpen() && screenName() == "PlayerListScreen", screenName())
        }
        step(10) {
            shot("player-list-menu")
        }
        step(3) {
            val screen = mc.screen!!
            screen.keyPressed(256, 0, 0)
            check("Escape closes only the menu, the list stays", !NameMenu.isOpen() && screenName() == "PlayerListScreen", screenName())
            "zzz".forEach { screen.charTyped(it, 0) }
        }
        step(3) {
            check("search hides players that don't match", Screens.playerRow(mc.screen!!, "OldTimer") == null)
            mc.screen!!.onClose()
            val file = ModTools.dir().resolve("players").resolve("example.net.json")
            check("the player list is saved", Files.exists(file) && Files.readString(file).contains("OldTimer"))
            check("only names, UUIDs and times are saved", Files.exists(file) && !Files.readString(file).contains("dupe glitch"))
        }

        // The missing-slash guard.
        step(5) {
            mc.setScreen(ChatScreen(""))
        }
        step(5) {
            typeAndEnter(mc, "weather clear")
            check("a command typed without its / is held back", mc.screen is ChatScreen && inputText(mc) == "weather clear", screenName())
            check("held back with a note", tabLines().any { it.guiMessage.content.string.contains("Not sent: this looks like a command") })
        }
        step(5) {
            shot("slash-guard")
        }
        step(3) {
            (mc.screen!! as ChatScreen).let { it.keyPressed(257, 0, 0) }
        }
        step(20) {
            check("Enter again sends it as chat", tabLines().any { it.guiMessage.content.string.endsWith("> weather clear") }, tabLines().takeLast(3).map { it.guiMessage.content.string }.toString())
            mc.setScreen(ChatScreen(""))
        }
        step(5) {
            typeAndEnter(mc, "guard strike RealBob test")
            check("a profile guard word is held back", mc.screen is ChatScreen && inputText(mc) == "guard strike RealBob test", screenName())
            (mc.screen as IMixinChatScreen).input.value = ""
            typeAndEnter(mc, "help me please")
        }
        step(20) {
            check("an ordinary sentence is sent", tabLines().any { it.guiMessage.content.string.endsWith("> help me please") }, tabLines().takeLast(3).map { it.guiMessage.content.string }.toString())
            check("nothing queued by the guard", engine.pendingCommands() == 0)
            mc.setScreen(null)
        }
    }

    private fun button(screen: Screen, label: String): Button =
        screen.children().filterIsInstance<Button>().first { it.message.string == label }

    private fun inputText(mc: Minecraft): String = (mc.screen as? IMixinChatScreen)?.input?.value ?: ""

    /** Types into chat and presses Enter, as a player would. */
    private fun typeAndEnter(mc: Minecraft, text: String) {
        val screen = mc.screen as ChatScreen
        (screen as IMixinChatScreen).input.value = text
        screen.keyPressed(257, 0, 0)
    }

    private fun finish() {
        val failed = results.count { it.startsWith("FAIL") }
        val summary = "[SelfTest] ${results.size - failed} passed, $failed failed"
        println(summary)
        val mc = Minecraft.getInstance()
        Files.write(mc.gameDirectory.toPath().resolve("selftest-results.txt"), results + summary.removePrefix("[SelfTest] "))
        phase = 3
        mc.stop()
    }
}
