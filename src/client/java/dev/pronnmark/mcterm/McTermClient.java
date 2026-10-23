package dev.pronnmark.mcterm;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pronnmark.mcterm.term.TerminalSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McTermClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mcterm", "terminal"));

	/** First line of a sign that makes it a computer. */
	public static final String COMPUTER_TAG = "[pc]";

	private static McTermConfig config;
	private static TerminalSession session;
	/** One session per computer name, so every computer keeps its own herdr view running. */
	private static final Map<String, TerminalSession> computers = new LinkedHashMap<>();

	/** Reuses the running session; otherwise re-reads config/mcterm.json so edits apply without a restart. */
	private static TerminalSession session() {
		if (session == null || !session.isRunning()) {
			if (session != null) session.close();
			config = McTermConfig.load();
			session = new TerminalSession(config.command, config.env);
		}
		return session;
	}

	/** Opens the computer with this name, starting its session the first time (or after it exited). */
	public static void openComputer(Minecraft client, String name) {
		TerminalSession s = computers.get(name);
		if (s == null || (s.isStarted() && !s.isRunning())) {
			if (s != null) s.close();
			config = McTermConfig.load();
			List<String> command = new ArrayList<>();
			for (String part : config.computerCommand) command.add(part.replace("{name}", name));
			s = new TerminalSession(command, config.env, config.computerKeys.replace("{name}", name));
			computers.put(name, s);
		}
		client.gui.setScreen(new TerminalScreen(s, config, Component.literal(name)));
	}

	/** The running session of a computer, or null before its first right-click. Drawn on its monitor. */
	static TerminalSession computerSession(String name) {
		return computers.get(name);
	}

	/** Cmd/Ctrl+Shift+, in the terminal: which machine to use. Restarts every terminal with the answer. */
	public static void openSetup(Minecraft client) {
		client.gui.setScreen(new SetupScreen(() -> {
			restartAll();
			openTerminal(client);
		}));
	}

	/** K: the terminal. */
	public static void openTerminal(Minecraft client) {
		client.gui.setScreen(new TerminalScreen(session(), config, Component.translatable("mcterm.screen.title")));
	}

	/** Ends every session so the next K or [pc] click starts the new command. */
	private static void restartAll() {
		if (session != null) session.close();
		session = null;
		computers.values().forEach(TerminalSession::close);
		computers.clear();
		config = McTermConfig.load();
	}

	/** The computer name on a sign side, or null when it is not a computer: line 1 is [pc], lines 2-4 are joined. */
	public static String computerName(SignText text) {
		Component[] lines = text.getMessages(false).toArray(Component[]::new);
		if (lines.length == 0 || !lines[0].getString().strip().equalsIgnoreCase(COMPUTER_TAG)) return null;
		StringBuilder name = new StringBuilder();
		for (int i = 1; i < lines.length; i++) name.append(lines[i].getString().strip());
		// Anyone can write a sign, and its name is typed into your terminal: keep letters, digits, space, - _ .
		String safe = name.toString().replaceAll("[^\\p{L}\\p{N} ._-]", "").strip();
		return safe.isEmpty() ? null : safe;
	}

	@Override
	public void onInitializeClient() {
		config = McTermConfig.load();

		KeyMapping open = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.mcterm.open", InputConstants.KEY_K, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				if (client.gui.screen() == null) openTerminal(client);
			}
		});

		// Right-click a [pc] sign to use that computer; sneak + right-click still edits the sign.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || player.isSecondaryUseActive()) return InteractionResult.PASS;
			if (!(level.getBlockEntity(hit.getBlockPos()) instanceof SignBlockEntity sign)) return InteractionResult.PASS;
			String name = computerName(sign.getText(sign.getSlotPlayerIsFacing(player)));
			if (name == null) return InteractionResult.PASS;
			openComputer(Minecraft.getInstance(), name);
			// FAIL: handled here, nothing is sent to the server, so it does not open the sign editor.
			return InteractionResult.FAIL;
		});

		// Every [pc] sign gets a monitor above it showing that computer's live screen.
		LevelRenderEvents.COLLECT_SUBMITS.register(ComputerMonitors::submit);

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (session != null) session.close();
			computers.values().forEach(TerminalSession::close);
		});
	}
}
