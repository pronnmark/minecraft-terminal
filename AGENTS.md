# minecraft-terminal — a real terminal inside Minecraft

Fabric client mod (`mcterm`) for Minecraft 26.3 / Java 25. Press **K** in-game, or right-click a
`[pc]` sign, to open a full-window terminal running a command in a pseudo-terminal — by default the
player's own shell. Cmd/Ctrl+Shift+, opens `SetupScreen`: empty = own shell, a host =
`ssh -t <host> herdr`. Public on GitHub. Players get it through the one-click setup in
`minecraft-server` (`client/`), which pins the jar from this repo's latest release. Our own use:
`pbox` on pmac.

## Commands

| what | command | notes |
|---|---|---|
| build | `./gradlew build` | jar in `build/libs/minecraft-terminal-<ver>.jar` |
| test | `xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest` | real client; set `MCTERM_TEST_COMMAND='["ssh","-t","pbox","herdr"]'` and/or `MCTERM_TEST_COMPUTER=<space>` to also run herdr; use `--no-daemon` (the daemon was killed mid-run twice on pbox); screenshots in `build/run/clientGameTest/screenshots/` |
| release | bump `version` in `gradle.properties`, commit, `git tag v<ver> && git push origin v<ver>` | CI builds and attaches the jar; then run `client/update-pack.sh` in `minecraft-server` |

## Hard stops

- **Never bundle JNA or SLF4J.** Minecraft already ships both; a second copy breaks class loading. `build.gradle` uses `transitive = false` for that reason.
- **Nothing personal in defaults.** Defaults must work for any player: own shell, no host names. Our pbox/herdr setup is the answer to the setup question, never baked in.
- **Sign text is untrusted.** `computerName` keeps only letters, digits, space and `. _ -` because any player can write a sign and its name is typed into the terminal.
- **No AWT/Swing.** Only `jediterm-core` (no UI) is used; Minecraft's render thread is not AWT-safe, and macOS forbids AWT on the main thread.

## Working rules

- Terminal emulation is JediTerm (`term/TerminalSession`); the PTY is pty4j. Rendering reads JediTerm's buffer each frame and draws in physical pixels (GUI pose scaled by `1/guiScale`) so TrueType glyphs stay sharp.
- Font sizes are fixed in `assets/mcterm/font/term_<px>.json`; the screen picks the largest that gives ≥120×30 cells. The JetBrainsMono advance is 0.6 em, so cell width is an integer only for multiples of 5 px.
- Key encoding: special keys go through JediTerm's `getCodeForKey` (respects application-cursor mode); printable text comes from SDL text input (`charTyped`) so layouts, dead keys and IME work. Left Alt = Meta (ESC prefix), right Alt composes — same as WezTerm on pmac.
- Mod shortcuts: Cmd (macOS) or Ctrl+Shift (Linux/Windows) + W close, V paste, = / - font size, 0 auto size. Everything else is sent to the terminal, including Esc.
- Computers: a sign whose first line is `[pc]` opens its own session per name (`McTermClient.openComputer`). It is client-side only (`UseBlockCallback`, returns FAIL so nothing reaches the server). herdr 0.9.5 has no flag to open a client on a space, so `computerKeys` types Ctrl+K, the name, Enter after the first draw; the palette moves only that client (verified with two clients, 2026-10-23).
- The session survives closing the screen; K reopens it. When the process has exited, the next K re-reads `config/mcterm.json`.

## Reference

| topic | read |
|---|---|
| user guide & config | `README.md` |
| font licences | `licenses/` |
| related repos | `minecraft-server` — the PaperMC server this client plays on |
