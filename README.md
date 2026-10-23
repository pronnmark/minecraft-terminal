# Minecraft Terminal

A real terminal inside Minecraft. Press **K**.

## Install

Mac or Linux. Open Terminal and paste:

```sh
curl -fsSL https://github.com/pronnmark/minecraft-terminal/releases/latest/download/install.sh | sh
```

Open Minecraft, pick **fabric-loader-26.3** next to Play, press Play.

## Use

Press **K**. The first time it asks one thing: leave it empty for a terminal on your computer, or
type an SSH host to open [herdr](https://herdr.dev) there.

**Computers:** write `[pc]` on a sign and a project name under it. Right-click the sign to use that
computer; it opens the herdr space with that name. Sneak + right-click edits the sign.

| key (Mac / other) | action |
|---|---|
| Cmd+W / Ctrl+Shift+W | leave (the terminal keeps running) |
| Cmd+V / Ctrl+Shift+V | paste |
| Cmd+= and Cmd+- | text size |
| Cmd+, / Ctrl+Shift+, | change the host |

## Notes

- Minecraft Java 26.3. Needs the Minecraft Launcher opened once first. Windows is not supported.
- Updating: run the install line again.
- The terminal has your user's rights. A sign's name is typed only as letters, digits, spaces and
  `. _ -`, so a sign someone else wrote cannot run commands.
- Build: `./gradlew build installer` (JDK 25) → `build/libs/*.jar`, `build/distributions/install.sh`.
- Licences: code MIT; fonts SIL OFL 1.1 (`licenses/`); JediTerm Apache-2.0/LGPL-3.0, pty4j EPL-1.0.
