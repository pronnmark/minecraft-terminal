package dev.pronnmark.mcterm;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pronnmark.mcterm.term.TerminalSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class McTermClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mcterm", "terminal"));

	private static McTermConfig config;
	private static TerminalSession session;

	/** Reuses the running session; otherwise re-reads config/mcterm.json so edits apply without a restart. */
	private static TerminalSession session() {
		if (session == null || !session.isRunning()) {
			if (session != null) session.close();
			config = McTermConfig.load();
			session = new TerminalSession(config.command, config.env);
		}
		return session;
	}

	@Override
	public void onInitializeClient() {
		config = McTermConfig.load();

		KeyMapping open = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.mcterm.open", InputConstants.KEY_K, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				if (client.gui.screen() == null) {
					TerminalSession s = session();
					client.gui.setScreen(new TerminalScreen(s, config));
				}
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (session != null) session.close();
		});
	}
}
