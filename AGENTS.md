# minecraft-terminal — a real terminal inside Minecraft

Fabric client mod (`mcterm`) for Minecraft 26.3 / Java 25. Press **K** in-game to open a
full-window terminal that runs a command in a pseudo-terminal — by default `ssh -t pbox herdr`.
Runs on the player's machine (pmac); nothing is deployed.

## Commands

| what | command | notes |
|---|---|---|
| build | `./gradlew build` | jar in `build/libs/minecraft-terminal-<ver>.jar` |
| test | `xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest` | real client; set `MCTERM_TEST_COMMAND='["ssh","-t","pbox","herdr"]'` to also run herdr; screenshots in `build/run/clientGameTest/screenshots/` |
| install | copy the jar into the instance's `mods/` | needs Fabric API |

## Hard stops

- **Never bundle JNA or SLF4J.** Minecraft already ships both; a second copy breaks class loading. `build.gradle` uses `transitive = false` for that reason.
- **No AWT/Swing.** Only `jediterm-core` (no UI) is used; Minecraft's render thread is not AWT-safe, and macOS forbids AWT on the main thread.

## Working rules

- Terminal emulation is JediTerm (`term/TerminalSession`); the PTY is pty4j. Rendering reads JediTerm's buffer each frame and draws in physical pixels (GUI pose scaled by `1/guiScale`) so TrueType glyphs stay sharp.
- Font sizes are fixed in `assets/mcterm/font/term_<px>.json`; the screen picks the largest that gives ≥120×30 cells. The JetBrainsMono advance is 0.6 em, so cell width is an integer only for multiples of 5 px.
- Key encoding: special keys go through JediTerm's `getCodeForKey` (respects application-cursor mode); printable text comes from SDL text input (`charTyped`) so layouts, dead keys and IME work. Left Alt = Meta (ESC prefix), right Alt composes — same as WezTerm on pmac.
- Mod shortcuts: Cmd (macOS) or Ctrl+Shift (Linux/Windows) + W close, V paste, = / - font size, 0 auto size. Everything else is sent to the terminal, including Esc.
- The session survives closing the screen; K reopens it. When the process has exited, the next K re-reads `config/mcterm.json`.

## Reference

| topic | read |
|---|---|
| user guide & config | `README.md` |
| font licences | `licenses/` |
| related repos | `minecraft-server` — the PaperMC server this client plays on |
