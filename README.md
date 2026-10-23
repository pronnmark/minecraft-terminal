# Minecraft Terminal

A real terminal inside Minecraft. Press **K** and you get a full-window xterm-256color terminal
running `ssh -t pbox herdr` — your own SSH client, keys and `~/.ssh/config`. Mouse, colours,
box drawing and Nerd Font icons work, so TUIs such as herdr, vim and htop behave normally.

Fabric client mod for Minecraft **26.3**, Java **25**, requires **Fabric API**. Works on macOS
(arm64/x86-64) and Linux.

## Install

```sh
./gradlew build
cp build/libs/minecraft-terminal-0.1.0.jar "<instance>/mods/"
```

## Use

| key | action |
|---|---|
| **K** | open the terminal (rebind under Controls → Terminal) |
| Cmd+W / Ctrl+Shift+W | close the screen — the session keeps running |
| Cmd+V / Ctrl+Shift+V | paste |
| Cmd+= / Cmd+- / Cmd+0 | bigger / smaller / automatic font size |
| Enter (after exit) | restart the command |
| mouse wheel | scrollback (or sent to the app when it uses the mouse) |

Esc goes to the terminal, so the game is not paused and Esc works in vim/herdr.

## Computers (an office with one per project)

Any sign is a computer if its **first line is `[pc]`**. Lines 2–4 are the herdr space it opens
(joined, so `agentic-` / `marketplace` works). **Right-click** it to sit down at that project;
**sneak + right-click** still edits the sign.

1. Build desks, put a block or a sign on each, and write `[pc]` + a project name, e.g.
   `[pc]` / `bofrid` or `[pc]` / `minecraft-server`.
2. Right-click: it runs `ssh -t pbox herdr`, then types **Ctrl+K, the name, Enter**, so herdr's
   space picker jumps to that project. Any part of the space name works, as in the picker.
3. Leave with Cmd+W. That computer keeps running, so coming back shows it exactly as you left it.

Each computer is its own herdr client, so two computers can show different spaces at once, and
your WezTerm on pmac is not moved. Signs are vanilla blocks, so this works on any server, including
ours, and other players just see signs. Only players with this mod (and SSH access to pbox) can use
them.

## Config

`config/mcterm.json`, created on first launch:

```json
{
  "command": ["ssh", "-t", "pbox", "herdr"],
  "computerCommand": ["ssh", "-t", "pbox", "herdr"],
  "computerKeys": "\u000b{name}\r",
  "env": {},
  "fontSize": 0
}
```

`computerCommand` and `computerKeys` are what a `[pc]` sign runs and types; `{name}` is the sign's
name. `fontSize` is 15, 20, 25, 30, 35 or 40 pixels; `0` picks the largest that fits 120×30 cells.
Changes apply the next time the command starts.

## Licences

Code: MIT. Bundled fonts: JetBrains Mono Nerd Font and Noto Sans Symbols 1/2, both SIL OFL 1.1
(`licenses/`). Libraries: JediTerm (Apache-2.0/LGPL-3.0), pty4j (EPL-1.0), Kotlin stdlib (Apache-2.0).
