package dev.pronnmark.mcterm.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pronnmark.mcterm.TerminalScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens the terminal in a real client, runs a shell through the PTY, checks the emulated screen,
 * then (when MCTERM_TEST_COMMAND is set) runs that command too, e.g. ssh into herdr.
 */
public class TerminalClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		writeConfig("[\"bash\", \"--norc\", \"-i\"]");
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender(true, 1200);

			context.getInput().pressKey(InputConstants.KEY_K);
			context.waitForScreen(TerminalScreen.class);
			waitForText(context, "$");

			context.getInput().typeChars("printf '\\e[31mred\\e[0m \\e[1;32mbold\\e[0m box:\\u2500\\u2502\\u250c braille:\\u28ff nf:\\uf015\\n'");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.getInput().typeChars("echo $TERM $COLUMNS x $LINES");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			waitForText(context, "xterm-256color");
			String text = context.computeOnClient(TerminalClientGameTest::screenText);
			if (!text.contains("red bold box:")) throw new AssertionError("printf output missing:\n" + text);
			context.takeScreenshot("mcterm-shell");

			context.getInput().typeChars("exit");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitFor(mc -> !((TerminalScreen) mc.gui.screen()).session().isRunning(), 200);
			context.takeScreenshot("mcterm-exited");

			// A [pc] sign is a computer: right-click opens its own session and types its start keys.
			writeConfig("[\"bash\", \"--norc\", \"-i\"]", "[\"bash\", \"--norc\", \"-i\"]", "echo computer-{name}-ok\\r");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			world.getServer().runCommand("setblock 0 -60 2 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"[pc]\",\"alpha\",\"\",\"\"]}}");
			world.getServer().runCommand("tp @a 0.5 -60 0.5 0 30");
			world.getConnection().waitForChunksRender(true, 1200);
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(TerminalScreen.class);
			waitForText(context, "computer-alpha-ok");
			String title = context.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
			if (!title.equals("alpha")) throw new AssertionError("computer screen title was " + title);
			context.takeScreenshot("mcterm-computer");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitFor(mc -> mc.gui.screen() == null, 100);

			// Closed, the computer keeps running and its monitor above the sign shows the live screen.
			world.getServer().runCommand("setblock 3 -60 2 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"pc\",\"\",\"\",\"\"]}}");
			world.getServer().runCommand("tp @a 1.5 -60 -3.5 0 -12");
			context.waitTicks(40);
			context.takeScreenshot("mcterm-monitors");
			// A bare "pc" sign is a computer too, named after where it stands.
			String bare = context.computeOnClient(mc -> dev.pronnmark.mcterm.McTermClient.computerName(
					((net.minecraft.world.level.block.entity.SignBlockEntity) mc.level.getBlockEntity(new net.minecraft.core.BlockPos(3, -60, 2)))
							.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT), new net.minecraft.core.BlockPos(3, -60, 2)));
			if (!"pc 3 -60 2".equals(bare)) throw new AssertionError("bare pc sign was named " + bare);
			world.getServer().runCommand("setblock 3 -60 2 minecraft:air");
			world.getServer().runCommand("tp @a 0.5 -60 0.5 0 30");
			context.waitTicks(20);

			// MCTERM_TEST_COMPUTER=<space>: a real [pc] sign for that herdr space, using the default computer config.
			String space = System.getenv("MCTERM_TEST_COMPUTER");
			if (space != null && !space.isBlank()) {
				// Exactly what a player does: Cmd/Ctrl+Shift+, opens the setup question; type the host, Enter.
				context.runOnClient(mc -> dev.pronnmark.mcterm.McTermClient.openSetup(mc));
				context.waitForScreen(dev.pronnmark.mcterm.SetupScreen.class);
				context.getInput().typeChars("pbox");
				context.getInput().pressKey(InputConstants.KEY_RETURN);
				context.waitForScreen(TerminalScreen.class);
				context.runOnClient(mc -> mc.gui.screen().onClose());
				context.waitFor(mc -> mc.gui.screen() == null, 100);
				world.getServer().runCommand("setblock 0 -60 2 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"[pc]\",\"" + space + "\",\"\",\"\"]}}");
				context.waitTicks(20);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitForScreen(TerminalScreen.class);
				context.waitTicks(300);
				context.takeScreenshot("mcterm-computer-herdr");
				System.out.println("[mcterm-test] computer " + space + ":\n" + context.computeOnClient(TerminalClientGameTest::screenText));
				context.runOnClient(mc -> mc.gui.screen().onClose());
				context.waitFor(mc -> mc.gui.screen() == null, 100);
			}

			String command = System.getenv("MCTERM_TEST_COMMAND");
			if (command != null && !command.isBlank()) {
				writeConfig(command);
				// The gametest harness sends key events with modifiers=0, so Ctrl+Shift+W cannot be simulated.
				context.runOnClient(mc -> mc.gui.screen().onClose());
				context.waitFor(mc -> mc.gui.screen() == null, 100);
				context.getInput().pressKey(InputConstants.KEY_K);
				context.waitForScreen(TerminalScreen.class);
				context.waitTicks(200);
				context.takeScreenshot("mcterm-command");
				System.out.println("[mcterm-test] screen after command:\n" + context.computeOnClient(TerminalClientGameTest::screenText));
			}
		}
	}

	private static void waitForText(ClientGameTestContext context, String needle) {
		try {
			context.waitFor(mc -> screenText(mc).contains(needle), 200);
		} catch (AssertionError e) {
			throw new AssertionError("'" + needle + "' never appeared; screen:\n" + context.computeOnClient(TerminalClientGameTest::screenText), e);
		}
	}

	private static String screenText(net.minecraft.client.Minecraft mc) {
		return mc.gui.screen() instanceof TerminalScreen screen ? screen.session().screenText() : "";
	}

	private static void writeConfig(String commandJson) {
		writeConfig(commandJson, commandJson, "");
	}

	private static void writeConfig(String commandJson, String computerJson, String computerKeys) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("mcterm.json");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "{\"command\": " + commandJson + ", \"computerCommand\": " + computerJson
					+ ", \"computerKeys\": \"" + computerKeys + "\", \"env\": {}, \"fontSize\": 0}", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
