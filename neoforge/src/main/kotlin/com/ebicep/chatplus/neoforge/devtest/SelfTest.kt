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
import com.ebicep.chatplus.hud.ChatManager
import com.ebicep.chatplus.translator.LanguageManager
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.neoforged.neoforge.client.event.RenderFrameEvent
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
            // Stand in for being on the example server. The connection is still the local singleplayer one.
            engine.setServer("example.net")
            check("tools active once the server is in the profile", ModTools.active())
            check("example profile loaded without errors", engine.errors().isEmpty(), engine.errors().toString())
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
        }
        step(5) {
            "self test only".forEach { mc.screen!!.charTyped(it, 0) }
        }
        step(20) {
            shot("strike-preview")
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
            val spanish = tabLines().first { it.guiMessage.content.string.contains("Hola amigo") }
            TranslateMessage.ClickTranslator(listOf(spanish), "Hola amigo. ¿Cómo estás? Me gusta este servidor.", english).start()
            val french = tabLines().filter { it.guiMessage.content.string.contains("Jean") }
            TranslateMessage.ClickTranslator(french, french.joinToString("§") { it.guiMessage.content.string }, english).start()
        }
        step(100) {
            val texts = tabLines().map { it.guiMessage.content.string }
            val spanish = texts.firstOrNull { it.contains("(Spanish)") }
            check("Spanish line translated", spanish != null, spanish ?: texts.takeLast(3).toString())
            check("translation has no double spaces", spanish != null && !spanish.contains("  "), spanish ?: "")
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
