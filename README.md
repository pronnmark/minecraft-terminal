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

## Config

`config/mcterm.json`, created on first launch:

```json
{
  "command": ["ssh", "-t", "pbox", "herdr"],
  "env": {},
  "fontSize": 0
}
```

`fontSize` is 15, 20, 25, 30, 35 or 40 pixels; `0` picks the largest that fits 120×30 cells.
Changes apply the next time the command starts.

## Licences

Code: MIT. Bundled fonts: JetBrains Mono Nerd Font and Noto Sans Symbols 1/2, both SIL OFL 1.1
(`licenses/`). Libraries: JediTerm (Apache-2.0/LGPL-3.0), pty4j (EPL-1.0), Kotlin stdlib (Apache-2.0).
