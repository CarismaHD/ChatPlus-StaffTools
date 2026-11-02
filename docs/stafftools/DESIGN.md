# ChatPlus Staff Tools: Design

ChatPlus 2.7.0 stays as it is. Moderation is a small addition that looks like part of ChatPlus and vanilla Minecraft.

## Decisions

| Area | Decision |
|---|---|
| Scope | A right-click name menu, a lookup screen, and a confirm and input screen. Tabs are left to the user's own ChatPlus config. There is no separate window. |
| Look | Only the vanilla tooltip box, buttons, edit boxes and checkbox, so nothing looks new to staff. |
| Settings | A Moderation section in ChatPlus's own settings screen. Server details live in `config/chatplus/moderation/profile.json`. |
| Local data | None. No logs, notes or review lists are stored. The server's records are the only source. |
| Portability | The core packages `core`, `command`, `parse` and `profile` use no Minecraft classes. |
| Builds | Built against a current NeoForge 1.21.1 setup: Minecraft 1.21.1, NeoForge 21.1.265 and Architectury 13.0.11. Kotlin for Forge 5.3.0 is used for compiling and 5.12.0 for the dev client. |

## Rules

1. The jar and repository hold no server details. The bundled profile is empty.
2. Commands are sent only from a click, and only on a server the profile lists.
3. Anything that changes something is confirmed on a screen showing the exact command. Send is disabled for 500 ms.
4. Commands go one at a time with a delay between them. Changing server clears the queue.
5. The menu's player comes from the click data on the name, matched by the profile's `nameFromClick` patterns.

## Testing

- **JUnit:** the core is tested against a made-up example profile, and translate parsing against recorded Google responses.
- **Real profile:** checked against real log samples kept outside the repository, using `-Pstafftools.profile` and `-Pstafftools.samples`.
- **`runSelfTest`:** an offline dev client in a throwaway singleplayer world. It can load your own ChatPlus config, and drives the menu, popups, lookup, translate and the settings screen through ChatPlus's real input path.
