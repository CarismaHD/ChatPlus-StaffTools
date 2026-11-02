# ChatPlus Staff Tools

A fork of [ChatPlus](https://github.com/ebicep/ChatPlus) by **ebicep**, licensed GPL-3.0. It is ChatPlus 2.7.0 for Minecraft 1.21.1 on NeoForge, unchanged in look and behaviour, with a few small helpers for server staff and some translate fixes.

It is client-only and **replaces** ChatPlus: it uses the same mod ID and the same settings file, so your tabs and settings carry over. Don't install both.

The mod knows nothing about any server. Everything server-specific comes from a profile file you write or are given. With no profile, the staff helpers stay off and the mod behaves exactly like ChatPlus 2.7.0.

## Changes from ChatPlus 2.7.0

This is the full list of what differs from the original ChatPlus 2.7.0 (upstream commit `ce581e6`). Anything not listed here is unchanged.

**Added**
- Name menu: right-click a player's name in chat for Lookup, Message, Note, Warn, Temp-ban, Unstuck, extra profile commands, and Copy name.
- Lookup screen: the player's warnings, notes and active bans, with Remove and Unban buttons.
- Confirm screen: every command that changes something shows the exact command before sending. Send is disabled for the first half second.
- `/mt` and `/modtools` commands: lookup, reload, and status.
- A Moderation section in ChatPlus settings: an on/off switch, the delay between commands, and how long to wait for a lookup reply.
- Server profiles: all server-specific behaviour comes from `config/chatplus/moderation/profile.json`. The bundled profile is empty, so without one the mod behaves exactly like ChatPlus.

**Fixed**
- Translate: no more double spaces between translated sentences.
- Translate: when several lines are selected, each translation now goes with the right line. If the pieces don't line up, it shows one combined translation instead of pairing text with the wrong line.
- Translate: translated text is added to chat on the game thread, not the network thread.
- Translate: an API error pauses translation for 5 minutes instead of for the rest of the session.
- Translate: requests time out instead of hanging, and auto-translate is skipped when no translate regex is set.
- Settings screen: uses upstream's later fix for opening the settings from the NeoForge 21.1 mod list (from ChatPlus commit `0740a1d`).

**Build**
- NeoForge only. The Fabric build is turned off.
- Built against Minecraft 1.21.1, NeoForge 21.1.265 and Architectury 13.0.11 (ChatPlus 2.7.0 used Minecraft 1.21, NeoForge 21.0 and Architectury 13.0.1).
- Renamed to ChatPlus Staff Tools, version `2.7.0-st.1`, jar `chatplus-stafftools-neoforge-<version>.jar`. The mod ID stays `chatplus`, and the settings file stays `chatplus-v2.7.0.json`.
- Issues link to this repository instead of upstream.
- New: unit tests for the staff tools and translate parsing, an offline self-test client (`runSelfTest`), and a release check for private terms (`tools/check-private-terms.py`).

## Features

| Feature | What it does |
|---|---|
| Name menu | Right-click a player's name in chat. A small menu, drawn like a normal tooltip, lists Lookup, Message, Note, Warn, Temp-ban, Unstuck, any extra commands the profile adds, and Copy name. |
| Lookup screen | Shows the player's warnings, notes and active bans from the server's own lookup command. Each warning and note has a Remove button, and active bans have Unban. |
| Confirm screen | Anything that changes something opens a screen showing the exact command that will be sent, with Cancel and Send. Send can't be clicked for the first half second, so a double-click never sends by accident. |
| `/mt` | `/mt <player>` opens a lookup, `/mt reload` reloads the profile, and `/mt` alone says whether the tools are active. `/modtools` works too. |
| Settings | ChatPlus settings has a Moderation section: an on/off switch, the delay between commands, and how long to wait for a lookup reply. |
| Translate fixes | No double spaces, selected lines always get the right translation, translations are added on the game thread, an API error pauses translate for 5 minutes instead of for the session, and requests time out instead of hanging. |

Everything else is ChatPlus 2.7.0 as released.

## Safety

- Nothing is ever sent without a click from you.
- Nothing is sent unless you're connected to a server listed in the profile.
- Commands go one at a time with a delay between them. Leaving or changing server clears anything still waiting.
- The player in the menu always comes from the server's own click data on their name (for example a `/msg <name>` suggestion), never from a display name, nickname or rank.
- Right-clicks with Shift, Ctrl or Alt held, and right-clicks on anything that isn't a name, go to ChatPlus as usual.

## Install

Needs Minecraft 1.21.1, NeoForge 21.1, Architectury API 13.0.11 or later, Cloth Config 15, and Kotlin for Forge 5.3 or later. Most 1.21.1 modpacks already have them.

1. Close the game.
2. Remove the ChatPlus jar from `mods` and put `chatplus-stafftools-neoforge-<version>.jar` in.
3. If your server's staff gave you a profile, save it as `config/chatplus/moderation/profile.json`. Without one, the mod is just ChatPlus.
4. Start the game, join the server, and type `/mt` to check the tools are active.

To go back to plain ChatPlus, swap the jars again. The settings file is shared, so nothing is lost.

## Using it

1. Open chat and right-click a player's name.
2. Pick an action:
   - **Lookup** sends the profile's lookup command and shows the reply on the lookup screen.
   - Actions ending in **...** open a small screen for the reason, message or duration. Check the command under "Will send:" and press Send, or press Cancel or Escape to send nothing.
   - **Copy name** copies the username to the clipboard.
3. On the lookup screen, **Remove** and **Unban** also go through the confirm screen.

If something doesn't work, try `/mt` first. It says whether a profile is loaded, whether this server is listed, and what is wrong with the profile, if anything.

## Writing a profile

A profile is a JSON file at `config/chatplus/moderation/profile.json`. A complete working example for a made-up server is in [`example-profile.json`](common/src/test/resources/example-profile.json), and [`example-samples.json`](common/src/test/resources/example-samples.json) beside it shows what each line should produce. After editing, run `/mt reload` in game. A profile that fails to load is moved aside as `profile.json.broken-<time>`, and an empty one takes its place.

Patterns are Java regular expressions, matched against a whole line of chat with formatting removed. Named groups such as `(?<name>...)` carry the data.

| Key | What it holds |
|---|---|
| `servers` | Hosts where the tools are active, such as `"play.example.net"`. A host also matches its subdomains, and the port is ignored. |
| `stripPrefixes` | Patterns removed from the start of a line before anything else is matched, for icons a server puts in front of text. |
| `chat.nameFromClick` | Patterns applied to the click value on a player's name, with a `name` group. For example `^/msg (?<name>[A-Za-z0-9_]{3,16})(?:\s.*)?$`. Only valid Minecraft usernames are accepted. |
| `chat.chatLines` | Patterns for normal player chat, so it is never mistaken for lookup output. |
| `lookup.start` | The first line of a lookup reply, with a `name` group. |
| `lookup.count` | Count lines, with `kind` and `count` groups. |
| `lookup.entry` | One warning or note, with `id`, `time`, `staff` and `text` groups, and optionally `status`. A following line that matches nothing else is joined to the entry, for long entries the server wraps. |
| `lookup.section` | Section headers, with a `header` group. |
| `lookup.warningsCount`, `notesCount`, `bansCount` | The `kind` values that mean warnings, notes and active bans. |
| `lookup.warningsSection`, `notesSection` | The `header` values that start the warnings and notes lists. |
| `silentFlag` | What `{silent}` becomes when Silent is ticked, such as a command's quiet flag. Leave it empty to hide the Silent checkbox. |
| `commands` | The commands, described below. |
| `tabs` | Read but not used in this version. Set up tabs in ChatPlus itself. |

**Commands.** Each entry has:

| Field | Meaning |
|---|---|
| `id` | What the command is for. The menu and lookup screen use the IDs below. |
| `label` | The text shown in the menu. |
| `template` | The command, with placeholders in braces. |
| `confirm` | `true` to always show the confirm screen. Commands with a reason, message or duration show it anyway. |
| `parser` | `"lookup"` on the lookup command, so its reply is read into the lookup screen. |
| `menu` | `true` to add an extra command to the name menu. |

| ID | Used for |
|---|---|
| `lookup` | Lookup in the menu, `/mt <player>`, and Refresh |
| `msg`, `note_add`, `warn_add`, `tempban`, `unstuck` | The name menu, in that order |
| `warn_remove`, `note_remove`, `unban` | The lookup screen's buttons |

| Placeholder | Filled with |
|---|---|
| `{player}` | The username from the click |
| `{reason}`, `{message}` | Text typed on the confirm screen |
| `{duration}` | One word, such as `1d` |
| `{silent}` | `silentFlag` when Silent is ticked, otherwise nothing |
| `{warningId}`, `{noteId}` | The number of the entry being removed |

Values are cleaned before sending: formatting codes and control characters are removed, and `{player}`, `{duration}` and the IDs must be a single word.

**Checking a profile against real chat.** Keep your profile and a samples file (same format as `example-samples.json`) outside this repository, and run:

```bash
./gradlew :common:test -Pstafftools.profile=/path/to/profile.json -Pstafftools.samples=/path/to/samples.json
```

## Building

You need JDK 21.

| Command | What it does |
|---|---|
| `./gradlew :neoforge:remapJar` | Builds the jar into `neoforge/build/libs/`. |
| `./gradlew :common:test` | Runs the unit tests. |
| `./gradlew :neoforge:runSelfTest` | Starts an offline dev client in a throwaway singleplayer world, clicks through the menu, screens and translate, and saves screenshots and `selftest-results.txt` to `neoforge/run-selftest/`. It never connects to a server. Add `-Pstafftools.chatConfig=<your chatplus-v2.7.0.json>` and `-Pstafftools.guiScale=<n>` to make it look like your own game. |

The staff code is in `common/src/main/java/com/ebicep/chatplus/features/moderation`. Its `core`, `command`, `parse` and `profile` packages use no Minecraft classes, so porting to another Minecraft version only touches `ui`, `ModTools`, `ModToolsFeature` and `ModToolsNeoForge`.

Releasing is covered in [RELEASING.md](RELEASING.md). The release notes for each version are in [docs/changelogs](docs/changelogs).

## Everything from ChatPlus

All of the original ChatPlus features are here, unchanged. This is ChatPlus's own feature list:

Adds more customization to the chat.
- /chatplus
- [Wiki](https://github.com/ebicep/ChatPlus/wiki)
- <video src="https://github.com/user-attachments/assets/1c48a7cc-09bc-4cef-9389-035d8b6e52a0" controls></video>

### Vanilla Changes
- Increases the maximum messages Minecraft can store.
- Increases the maximum number of command suggestions shown.
- Resize/Relocate the chat box.
- Scroll through the chat faster/slower.
- Made the tiny scrollbar larger and actually functional.
- Removed the "Server message. Cannot be reported" sidebar.
- And more!

### Custom Features

#### General
- Create chat windows with chat tabs.
- Customize the chat window.
  - Resize
  - Relocate
  - Background Color
  - Scale
  - Text Opacity
  - Unfocused Opacities
  - Unfocused Height
  - Line Spacing
  - Outline
  - Padding
  - Message alignment (left/middle/right)
  - Message direction (top down/bottom up)
- Automatically create tabs.
- Tab notifications.
- Highlight lines automatically*.
- Bookmark messages manually or automatically* and jump to them.
- Highlight the current hovered over line.
- Find messages in chat and jump to them.
- Take screenshots of the whole chat or specific messages and auto upload them to imgur.
- Select groups of messages by right clicking them, and then perform many of the custom options on them (Screenshot/Copy/Bookmark/etc).
- Adds timestamps to messages.
- Adds player heads next to messages.
- Peek chat.
- Compact messages.
- And more!

#### Language Related
- Automatically* translate incoming messages.
- Send messages in different languages.
- Control click messages to translate them.
- Send messages using speech to text (can be auto translated) using [VOSK](https://alphacephei.com/vosk/models), download your desired model and place in config/chatplus/models. (NOTE: This uses push to talk and larger models are slower!)


*Based on user defined regexes.


### Compatibility

This mod overrides the vanilla chat renderer. Any mod that uses mixin on it will probably not work.

## Credits and license

ChatPlus is by [ebicep](https://github.com/ebicep/ChatPlus). This fork adds the staff tools and translate fixes listed above. Both are GPL-3.0 (see [LICENSE](LICENSE)): you may share and change it, and anyone you give the jar to must also be able to get this source.
