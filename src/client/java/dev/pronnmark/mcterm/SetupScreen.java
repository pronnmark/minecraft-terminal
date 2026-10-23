package dev.pronnmark.mcterm;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * The one question, asked the first time K is pressed (and again with Cmd/Ctrl+Shift+, in the
 * terminal): which machine to connect to. Empty means this computer's own shell; a host name
 * means herdr on that machine over SSH, with [pc] signs opening the herdr space of their name.
 */
public final class SetupScreen extends Screen {
	private final Runnable onDone;
	private EditBox host;

	public SetupScreen(Runnable onDone) {
		super(Component.translatable("mcterm.setup.title"));
		this.onDone = onDone;
	}

	@Override
	protected void init() {
		int w = 240;
		int x = (width - w) / 2;
		int y = height / 2 - 20;
		host = new EditBox(font, x, y, w, 20, Component.translatable("mcterm.setup.host"));
		host.setMaxLength(128);
		host.setHint(Component.translatable("mcterm.setup.hint"));
		host.setValue(McTermConfig.load().herdrHost());
		addRenderableWidget(host);
		addRenderableWidget(Button.builder(Component.translatable("mcterm.setup.done"), b -> done())
				.bounds(x, y + 32, w, 20).build());
		setInitialFocus(host);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int y = height / 2 - 20;
		g.centeredText(font, title, width / 2, y - 40, 0xFFFFFFFF);
		g.centeredText(font, Component.translatable("mcterm.setup.help"), width / 2, y - 22, 0xFFA0A0A0);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			done();
			return true;
		}
		return super.keyPressed(event);
	}

	private void done() {
		String h = host.getValue().strip();
		// A host name must not become an ssh option or carry spaces into the command.
		if (!h.matches("[A-Za-z0-9._@:\\[\\]-]*") || h.startsWith("-")) {
			host.setValue("");
			host.setHint(Component.translatable("mcterm.setup.bad"));
			return;
		}
		McTermConfig c = McTermConfig.load();
		if (h.isEmpty()) {
			c.command = McTermConfig.defaultShell();
			c.computerCommand = c.command;
			c.computerKeys = "";
		} else {
			c.command = List.of("ssh", "-t", h, "herdr");
			c.computerCommand = c.command;
			c.computerKeys = McTermConfig.HERDR_SPACE_KEYS;
		}
		c.save();
		onDone.run();
	}
}
