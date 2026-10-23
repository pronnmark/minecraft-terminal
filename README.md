# Minecraft Terminal

A real terminal inside Minecraft. Press **K**, or right-click a sign that says **`[pc]`**, and the
screen becomes a full terminal: your own shell, or `ssh` into a machine running
[herdr](https://herdr.dev). Colours, mouse, box drawing and Nerd Font icons work, so vim, htop and
herdr behave normally.

Minecraft **26.3** (Java Edition) with Fabric. macOS and Linux. Windows is untested.

## Install (one file)

1. Install the [Modrinth App](https://modrinth.com/app) (free) and sign in with your Minecraft account.
2. Download **`minecraft-terminal-<version>.mrpack`** from the
   [latest release](https://github.com/pronnmark/minecraft-terminal/releases/latest).
3. Double-click it, or drag it into the Modrinth App. It creates a profile with Minecraft 26.3,
   Fabric and everything needed.
4. Press **Play** on that profile.

Already have a Fabric 26.3 profile? Put `minecraft-terminal-<version>.jar` and
[Fabric API](https://modrinth.com/mod/fabric-api) in its `mods` folder instead.

## Use

| key | action |
|---|---|
| **K** | open the terminal (rebind under Options → Controls → Terminal) |
| Cmd+W (Mac) · Ctrl+Shift+W | leave the screen; the terminal keeps running |
| Cmd+V · Ctrl+Shift+V | paste |
| Cmd+= / Cmd+- / Cmd+0 | bigger / smaller / automatic text size |
| Enter (after it exits) | start it again |

Esc goes to the terminal, so it works in vim and does not pause the game.

## Computers: an office with one per project

Place any sign and write **`[pc]`** on the first line and a name on the next (lines 2–4 are joined,
so `agentic-` / `marketplace` works). **Right-click** the sign to sit down at that computer;
**sneak + right-click** still edits the sign. Each computer keeps its own terminal running, so
coming back shows it exactly as you left it.

Signs are normal blocks, so this works on any server and other players just see signs. Only
players who have this mod can use them, and each player uses their own computer and accounts.

## Connect to herdr

Type in chat:

```
/terminal herdr <ssh-host>
```

Now K and every `[pc]` sign run `ssh -t <ssh-host> herdr`, and a `[pc]` sign jumps to the herdr
space with its name (any part of the name works, like herdr's Ctrl+K picker). Each sign is its own
herdr client, so two computers can show two projects at once. `<ssh-host>` is anything `ssh` accepts:
a host from `~/.ssh/config`, `user@host`, or an IP. Use an SSH key; a password prompt also works.

`/terminal shell` goes back to your own shell.

## Config

`config/mcterm.json` in the profile folder; `/terminal` writes it for you.

```json
{
  "command": ["/bin/zsh", "-l"],
  "computerCommand": ["/bin/zsh", "-l"],
  "computerKeys": "",
  "env": {},
  "fontSize": 0
}
```

`command` is what K runs. `computerCommand` and `computerKeys` are what a `[pc]` sign runs and then
types; `{name}` is the sign's name. `fontSize` is 15, 20, 25, 30, 35 or 40 pixels; `0` picks the
largest that fits 120×30 cells.

## Safety

The terminal is a real shell with your user's rights. A sign's name is typed into your terminal
only as letters, digits, spaces and `. _ -`, so a sign someone else wrote cannot run commands.

## Build

```sh
./gradlew build mrpack   # needs JDK 25
```

`build/libs/*.jar` is the mod; `build/distributions/*.mrpack` is the one-file install.

## Licences

Code: MIT. Bundled fonts: JetBrains Mono Nerd Font and Noto Sans Symbols 1/2, SIL OFL 1.1
(`licenses/`). Libraries: JediTerm (Apache-2.0/LGPL-3.0), pty4j (EPL-1.0), Kotlin stdlib (Apache-2.0).
