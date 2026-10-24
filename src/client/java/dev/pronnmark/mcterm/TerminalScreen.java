package dev.pronnmark.mcterm;

import com.jediterm.core.input.KeyEvent;
import com.jediterm.core.input.InputEvent;
import com.jediterm.core.input.MouseEvent;
import com.jediterm.core.input.MouseWheelEvent;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;
import com.jediterm.terminal.util.CharUtils;
import com.mojang.blaze3d.platform.InputConstants;
import dev.pronnmark.mcterm.term.Palette;
import dev.pronnmark.mcterm.term.TerminalSession;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.sdl.SDLKeyboard;

import java.nio.charset.StandardCharsets;

/**
 * The computer's screen, opened in front of you: the terminal fills 90% of the window inside a
 * monitor bezel and the world stays visible around it. Draws in physical pixels (the GUI pose is scaled by 1/guiScale) so the
 * TrueType glyphs are rasterised 1:1 and stay sharp at any GUI scale.
 */
public final class TerminalScreen extends Screen {
	/** Font sizes shipped in assets/mcterm/font/term_N.json; advance is 0.6 em, an integer for each. */
	static final int[] FONT_SIZES = {15, 20, 25, 30, 35, 40};
	private static final int MIN_AUTO_COLUMNS = 120;
	private static final int MIN_AUTO_ROWS = 30;
	private static final int PAD = 4;
	/** Share of the window's shorter side left around the monitor on each side. */
	private static final float MARGIN = 0.05f;
	private static final long OPEN_MS = 180;
	private static final int BEZEL_COLOR = 0xff2b2b33, BEZEL_EDGE = 0xff3a3a44;

	// SDL modifier bits (KeyEvent.modifiers()).
	private static final int MOD_SHIFT = 0x0003;
	private static final int MOD_CTRL = 0x00c0;
	private static final int MOD_LALT = 0x0100;
	private static final int MOD_RALT = 0x0200;
	private static final int MOD_ALT = MOD_LALT | MOD_RALT;
	private static final int MOD_GUI = 0x0c00;
	private static final int KEY_KP_MINUS = 86; // SDL_SCANCODE_KP_MINUS
	private static final boolean MAC = Util.getPlatform() == Util.OS.OSX;

	private final TerminalSession session;
	private final McTermConfig config;

	private int fontSize;
	private Style style = Style.EMPTY;
	private int cellW = 12;
	private int cellH = 26;
	private int baseline = 20;
	private int cols = 80;
	private int rows = 24;
	private final Int2BooleanOpenHashMap monospaced = new Int2BooleanOpenHashMap();
	/** The terminal area in window pixels: top-left corner and size. */
	private int ox, oy, areaW, areaH;
	private final long openedAt = Util.getMillis();
	private boolean hidHud;

	private int scrollBack;
	private boolean swallowText;
	private int heldButton = -1;
	private int lastMotionCol = -1;
	private int lastMotionRow = -1;

	public TerminalScreen(TerminalSession session, McTermConfig config, Component title) {
		super(title);
		this.session = session;
		this.config = config;
	}

	public TerminalSession session() {
		return session;
	}

	// ---- layout ----

	private int guiScale() {
		return Math.max(1, minecraft.getWindow().getGuiScale());
	}

	private int pixelWidth() {
		return minecraft.getWindow().getWidth();
	}

	private int pixelHeight() {
		return minecraft.getWindow().getHeight();
	}

	private int bezel() {
		return Math.max(6, Math.round(Math.min(pixelWidth(), pixelHeight()) * 0.012f));
	}

	private void layout() {
		int margin = Math.round(Math.min(pixelWidth(), pixelHeight()) * MARGIN) + bezel();
		ox = margin;
		oy = margin;
		areaW = Math.max(100, pixelWidth() - 2 * margin);
		areaH = Math.max(60, pixelHeight() - 2 * margin);
	}

	@Override
	protected void init() {
		minecraft.textInputManager().startTextInput(this);
		// The hotbar would sit under the monitor's bottom edge; hide it while you use the computer.
		if (!minecraft.gui.hud.isHidden()) {
			minecraft.gui.hud.toggle();
			hidHud = true;
		}
		layout();
		applyFont(config.fontSize > 0 ? config.fontSize : autoFontSize());
	}

	@Override
	public void removed() {
		minecraft.textInputManager().stopTextInput(this);
		if (hidHud && minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
		hidHud = false;
		super.removed();
	}

	private int autoFontSize() {
		int w = areaW - 2 * PAD;
		int h = areaH - 2 * PAD;
		for (int i = FONT_SIZES.length - 1; i >= 0; i--) {
			int s = FONT_SIZES[i];
			if (w / Math.round(s * 0.6f) >= MIN_AUTO_COLUMNS && h / Math.round(s * 1.32f) >= MIN_AUTO_ROWS) return s;
		}
		return FONT_SIZES[0];
	}

	private void applyFont(int size) {
		int chosen = FONT_SIZES[0];
		for (int s : FONT_SIZES) if (s <= size) chosen = s;
		fontSize = chosen;
		style = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcterm", "term_" + chosen)));
		monospaced.clear();
		cellW = Math.max(1, Math.round(advance("M")));
		cellH = Math.round(chosen * 1.32f);
		baseline = Math.round(chosen * 1.02f);
		cols = Math.max(10, (areaW - 2 * PAD) / cellW);
		rows = Math.max(4, (areaH - 2 * PAD) / cellH);
		if (!session.isStarted()) {
			session.start(cols, rows);
		} else {
			session.resize(cols, rows);
		}
	}

	private void stepFont(int direction) {
		int idx = 0;
		for (int i = 0; i < FONT_SIZES.length; i++) if (FONT_SIZES[i] == fontSize) idx = i;
		idx = Math.max(0, Math.min(FONT_SIZES.length - 1, idx + direction));
		config.fontSize = FONT_SIZES[idx];
		config.save();
		applyFont(config.fontSize);
	}

	private float advance(String s) {
		return font.getSplitter().stringWidth(FormattedText.of(s, style));
	}

	/** True when the glyph for {@code cp} is exactly one cell wide, so it can be batched in a run. */
	private boolean fitsCell(int cp) {
		if (cp < 0x7f) return true;
		return monospaced.computeIfAbsent(cp, c -> Math.round(advance(Character.toString(c))) == cellW);
	}

	// ---- screen behaviour ----

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// No blur: the world stays visible around the monitor, only dimmed a little to keep focus.
	}

	// ---- rendering ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// SDL delivers a key-down and its text event in the same poll, so a swallow never outlives a frame.
		swallowText = false;
		TerminalSession.Snapshot snap = session.snapshot(scrollBack);
		float inv = 1f / guiScale();
		g.pose().pushMatrix();
		g.pose().scale(inv, inv);
		float t = Math.min(1f, (Util.getMillis() - openedAt) / (float) OPEN_MS);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		g.fill(0, 0, pixelWidth(), pixelHeight(), Math.round(0x50 * ease) << 24);
		// Opening: the monitor grows from the middle of the window, as if you lean in to it.
		float grow = 0.85f + 0.15f * ease;
		g.pose().translate(pixelWidth() / 2f, pixelHeight() / 2f);
		g.pose().scale(grow, grow);
		g.pose().translate(-pixelWidth() / 2f + ox, -pixelHeight() / 2f + oy);
		int b = bezel();
		g.fill(-b - 1, -b - 1, areaW + b + 1, areaH + b + 1, BEZEL_EDGE);
		g.fill(-b, -b, areaW + b, areaH + b, BEZEL_COLOR);
		g.fill(areaW - 3 * b, areaH + b / 2 - 1, areaW - 2 * b, areaH + b / 2 + 1, 0xff40d060);
		g.enableScissor(0, 0, areaW, areaH);
		g.fill(0, 0, areaW, areaH, argb(Palette.BACKGROUND));
		if (snap != null) {
			scrollBack = Math.min(scrollBack, snap.historyLines());
			drawCells(g, snap);
			drawCursor(g, snap);
		}
		String banner = banner();
		if (banner != null) {
			g.nextStratum();
			int y = PAD + (rows - 1) * cellH;
			g.fill(0, y, areaW, y + cellH, argb(Palette.BAR_BACKGROUND));
			drawText(g, banner, PAD, y, Palette.FOREGROUND, false, false);
		}
		g.disableScissor();
		g.pose().popMatrix();
	}

	private String banner() {
		String shortcut = MAC ? "Cmd+W" : "Ctrl+Shift+W";
		if (session.startError() != null) {
			return " Could not start " + String.join(" ", session.command()) + ": " + session.startError()
					+ "   Enter: retry   " + shortcut + ": close";
		}
		if (session.isStarted() && !session.isRunning()) {
			Integer code = session.exitCode();
			return " Process exited" + (code == null ? "" : " (" + code + ")") + "   Enter: restart   " + shortcut + ": close";
		}
		if (scrollBack > 0) {
			return " Scrollback: " + scrollBack + " lines up — type to return";
		}
		return null;
	}

	private void drawCells(GuiGraphicsExtractor g, TerminalSession.Snapshot snap) {
		// Backgrounds first (fills are drawn before text within a stratum anyway).
		for (TerminalSession.Run run : snap.runs()) {
			Palette.Colors c = Palette.resolve(run.style());
			if (!c.defaultBg()) {
				int x = PAD + run.col() * cellW;
				int y = PAD + run.row() * cellH;
				g.fill(x, y, x + run.text().length() * cellW, y + cellH, argb(c.bg()));
			}
		}
		for (TerminalSession.Run run : snap.runs()) {
			if (run.blank()) continue;
			Palette.Colors c = Palette.resolve(run.style());
			boolean bold = run.style().hasOption(TextStyle.Option.BOLD);
			boolean italic = run.style().hasOption(TextStyle.Option.ITALIC);
			boolean underline = run.style().hasOption(TextStyle.Option.UNDERLINED);
			drawRun(g, run.text(), run.col(), run.row(), c.fg(), bold, italic, underline);
		}
	}

	/** Draws one styled run, batching cell-wide glyphs and placing every other glyph on its own cell. */
	private void drawRun(GuiGraphicsExtractor g, String text, int col, int row, int fg, boolean bold, boolean italic, boolean underline) {
		int y = PAD + row * cellH;
		StringBuilder batch = new StringBuilder();
		int batchCol = col;
		int i = 0;
		int c = col;
		while (i < text.length()) {
			int cp = text.codePointAt(i);
			int len = Character.charCount(cp);
			boolean dwc = cp == CharUtils.DWC;
			boolean batchable = !bold && !dwc && cp != ' ' && fitsCell(cp);
			if (!batchable || cp == ' ') {
				if (!batch.isEmpty()) {
					drawStyled(g, batch.toString(), PAD + batchCol * cellW, y, fg, false, italic, underline);
					batch.setLength(0);
				}
				if (!dwc && cp != ' ') {
					drawStyled(g, Character.toString(cp), PAD + c * cellW, y, fg, bold, italic, underline);
				} else if (cp == ' ' && underline) {
					drawStyled(g, " ", PAD + c * cellW, y, fg, false, false, true);
				}
				batchCol = c + 1;
			} else {
				if (batch.isEmpty()) batchCol = c;
				batch.appendCodePoint(cp);
			}
			// JediTerm stores one char per cell (a surrogate pair occupies two cells' worth of chars).
			c += len;
			i += len;
		}
		if (!batch.isEmpty()) {
			drawStyled(g, batch.toString(), PAD + batchCol * cellW, y, fg, false, italic, underline);
		}
	}

	private void drawStyled(GuiGraphicsExtractor g, String s, int x, int cellTop, int rgb, boolean bold, boolean italic, boolean underline) {
		Style st = style;
		if (bold) st = st.withBold(true);
		if (italic) st = st.withItalic(true);
		if (underline) st = st.withUnderlined(true);
		// Minecraft places the glyph baseline 7 units below the draw position.
		g.text(font, FormattedCharSequence.forward(s, st), x, cellTop + baseline - 7, argb(rgb), false);
	}

	private void drawText(GuiGraphicsExtractor g, String s, int x, int cellTop, int rgb, boolean bold, boolean italic) {
		drawStyled(g, s, x, cellTop, rgb, bold, italic, false);
	}

	private void drawCursor(GuiGraphicsExtractor g, TerminalSession.Snapshot snap) {
		if (!snap.cursorVisible() || snap.cursorRow() < 0 || snap.cursorRow() >= rows) return;
		int x = PAD + snap.cursorCol() * cellW;
		int y = PAD + snap.cursorRow() * cellH;
		CursorShape shape = snap.cursorShape();
		int color = argb(Palette.CURSOR);
		if (shape == CursorShape.BLINK_UNDERLINE || shape == CursorShape.STEADY_UNDERLINE) {
			g.fill(x, y + cellH - Math.max(2, cellH / 12), x + cellW, y + cellH, color);
		} else if (shape == CursorShape.BLINK_VERTICAL_BAR || shape == CursorShape.STEADY_VERTICAL_BAR) {
			g.fill(x, y, x + Math.max(2, cellW / 8), y + cellH, color);
		} else {
			g.fill(x, y, x + cellW, y + cellH, color);
			String under = charAt(snap, snap.cursorCol(), snap.cursorRow());
			if (under != null) drawText(g, under, x, y, Palette.BACKGROUND, false, false);
		}
	}

	private static String charAt(TerminalSession.Snapshot snap, int col, int row) {
		for (TerminalSession.Run run : snap.runs()) {
			if (run.row() != row || run.blank()) continue;
			int idx = col - run.col();
			if (idx >= 0 && idx < run.text().length()) {
				char ch = run.text().charAt(idx);
				if (ch == ' ' || ch == CharUtils.DWC || Character.isSurrogate(ch)) return null;
				return String.valueOf(ch);
			}
		}
		return null;
	}

	private static int argb(int rgb) {
		return 0xFF000000 | rgb;
	}

	// ---- keyboard ----

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		swallowText = false;
		int mods = event.modifiers();
		int scancode = event.key();
		boolean shortcutMod = MAC ? (mods & MOD_GUI) != 0 : (mods & MOD_CTRL) != 0 && (mods & MOD_SHIFT) != 0;

		if (shortcutMod && handleShortcut(scancode)) {
			swallowText = true;
			return true;
		}
		if (!session.isRunning()) {
			if (scancode == InputConstants.KEY_RETURN || scancode == InputConstants.KEY_NUMPADENTER) {
				scrollBack = 0;
				session.start(cols, rows);
			}
			swallowText = true;
			return true;
		}

		boolean shift = (mods & MOD_SHIFT) != 0;
		boolean ctrl = (mods & MOD_CTRL) != 0;
		// Left Option/Alt is Meta (sends ESC); right Option/AltGr composes characters, like WezTerm on pmac.
		boolean meta = (mods & MOD_LALT) != 0 && (mods & MOD_RALT) == 0;
		boolean anyAlt = (mods & MOD_ALT) != 0;

		byte[] special = specialKey(scancode, shift, ctrl, anyAlt);
		if (special != null) {
			typed();
			session.sendBytes(special);
			swallowText = true;
			return true;
		}
		if ((mods & MOD_GUI) != 0) {
			swallowText = true;
			return true;
		}
		if ((ctrl && (mods & MOD_RALT) == 0) || meta) {
			int base = SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) (shift ? MOD_SHIFT : 0), false);
			if (base > 0 && base < 0x110000 && (base & 0x40000000) == 0) {
				String out;
				if (ctrl) {
					int code = controlCode(base);
					if (code < 0) return true;
					out = String.valueOf((char) code);
				} else {
					out = Character.toString(base);
				}
				typed();
				session.sendString(meta ? "\u001b" + out : out);
				swallowText = true;
			}
			return true;
		}
		// Printable keys arrive through charTyped (SDL text input handles layouts, dead keys and IME).
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (swallowText || !session.isRunning()) return true;
		typed();
		session.sendString(event.codepointAsString());
		return true;
	}

	private void typed() {
		scrollBack = 0;
	}

	private boolean handleShortcut(int scancode) {
		switch (scancode) {
			case InputConstants.KEY_W -> onClose();
			case InputConstants.KEY_COMMA -> McTermClient.openSetup(minecraft);
			case InputConstants.KEY_V -> {
				typed();
				session.paste(minecraft.keyboardHandler.getClipboard());
			}
			case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> stepFont(1);
			case InputConstants.KEY_MINUS, KEY_KP_MINUS -> stepFont(-1);
			case InputConstants.KEY_0 -> {
				config.fontSize = 0;
				config.save();
				applyFont(autoFontSize());
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private byte[] specialKey(int scancode, boolean shift, boolean ctrl, boolean alt) {
		int modsEx = (shift ? InputEvent.SHIFT_DOWN_MASK : 0) | (ctrl ? InputEvent.CTRL_DOWN_MASK : 0)
				| (alt ? InputEvent.ALT_DOWN_MASK : 0);
		String esc = alt ? "\u001b" : "";
		switch (scancode) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER:
				return (esc + "\r").getBytes(StandardCharsets.UTF_8);
			case InputConstants.KEY_ESCAPE:
				return (esc + "\u001b").getBytes(StandardCharsets.UTF_8);
			case InputConstants.KEY_TAB:
				return shift ? "\u001b[Z".getBytes(StandardCharsets.UTF_8) : (esc + "\t").getBytes(StandardCharsets.UTF_8);
			case InputConstants.KEY_BACKSPACE:
				return ctrl ? new byte[]{0x08} : alt ? new byte[]{0x1b, 0x7f} : new byte[]{0x7f};
			default:
		}
		int vk = switch (scancode) {
			case InputConstants.KEY_UP -> KeyEvent.VK_UP;
			case InputConstants.KEY_DOWN -> KeyEvent.VK_DOWN;
			case InputConstants.KEY_LEFT -> KeyEvent.VK_LEFT;
			case InputConstants.KEY_RIGHT -> KeyEvent.VK_RIGHT;
			case InputConstants.KEY_HOME -> KeyEvent.VK_HOME;
			case InputConstants.KEY_END -> KeyEvent.VK_END;
			case InputConstants.KEY_PAGEUP -> KeyEvent.VK_PAGE_UP;
			case InputConstants.KEY_PAGEDOWN -> KeyEvent.VK_PAGE_DOWN;
			case InputConstants.KEY_INSERT -> KeyEvent.VK_INSERT;
			case InputConstants.KEY_DELETE -> KeyEvent.VK_DELETE;
			case InputConstants.KEY_F1 -> KeyEvent.VK_F1;
			case InputConstants.KEY_F2 -> KeyEvent.VK_F2;
			case InputConstants.KEY_F3 -> KeyEvent.VK_F3;
			case InputConstants.KEY_F4 -> KeyEvent.VK_F4;
			case InputConstants.KEY_F5 -> KeyEvent.VK_F5;
			case InputConstants.KEY_F6 -> KeyEvent.VK_F6;
			case InputConstants.KEY_F7 -> KeyEvent.VK_F7;
			case InputConstants.KEY_F8 -> KeyEvent.VK_F8;
			case InputConstants.KEY_F9 -> KeyEvent.VK_F9;
			case InputConstants.KEY_F10 -> KeyEvent.VK_F10;
			case InputConstants.KEY_F11 -> KeyEvent.VK_F11;
			case InputConstants.KEY_F12 -> KeyEvent.VK_F12;
			default -> -1;
		};
		return vk < 0 ? null : session.codeForKey(vk, modsEx);
	}

	private static int controlCode(int key) {
		if (key >= 'a' && key <= 'z') return key - 'a' + 1;
		if (key >= 'A' && key <= 'Z') return key - 'A' + 1;
		return switch (key) {
			case ' ', '@', '2' -> 0;
			case '[', '3' -> 27;
			case '\\', '4' -> 28;
			case ']', '5' -> 29;
			case '^', '6' -> 30;
			case '_', '-', '/', '7' -> 31;
			case '8', '?' -> 127;
			default -> -1;
		};
	}

	// ---- mouse ----

	private int col(double guiX) {
		return clamp((int) ((guiX * guiScale() - ox - PAD) / cellW), cols - 1);
	}

	private int row(double guiY) {
		return clamp((int) ((guiY * guiScale() - oy - PAD) / cellH), rows - 1);
	}

	private static int clamp(int v, int max) {
		return Math.max(0, Math.min(max, v));
	}

	private static int mouseButton(int mcButton) {
		return switch (mcButton) {
			case InputConstants.MOUSE_BUTTON_LEFT -> MouseButtonCodes.LEFT;
			case InputConstants.MOUSE_BUTTON_MIDDLE -> MouseButtonCodes.MIDDLE;
			case InputConstants.MOUSE_BUTTON_RIGHT -> MouseButtonCodes.RIGHT;
			default -> MouseButtonCodes.NONE;
		};
	}

	private static int mouseMods(int sdlMods) {
		int m = 0;
		if ((sdlMods & MOD_SHIFT) != 0) m |= MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG;
		if ((sdlMods & MOD_ALT) != 0) m |= MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG;
		if ((sdlMods & MOD_CTRL) != 0) m |= MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG;
		return m;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = mouseButton(event.button());
		heldButton = button;
		session.mouseEvent(col(event.x()), row(event.y()), new MouseEvent(MouseEvent.Type.PRESSED, button, mouseMods(event.modifiers())));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		heldButton = -1;
		session.mouseEvent(col(event.x()), row(event.y()),
				new MouseEvent(MouseEvent.Type.RELEASED, mouseButton(event.button()), mouseMods(event.modifiers())));
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		int c = col(event.x());
		int r = row(event.y());
		if (c == lastMotionCol && r == lastMotionRow) return true;
		lastMotionCol = c;
		lastMotionRow = r;
		session.mouseEvent(c, r, new MouseEvent(MouseEvent.Type.DRAGGED, mouseButton(event.button()), mouseMods(event.modifiers())));
		return true;
	}

	@Override
	public void mouseMoved(double x, double y) {
		if (heldButton >= 0 || !session.mouseReporting()) return;
		int c = col(x);
		int r = row(y);
		if (c == lastMotionCol && r == lastMotionRow) return;
		lastMotionCol = c;
		lastMotionRow = r;
		session.mouseEvent(c, r, new MouseEvent(MouseEvent.Type.MOVED, MouseButtonCodes.NONE, 0));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY == 0) return true;
		int steps = Math.max(1, (int) Math.round(Math.abs(scrollY)));
		if (session.mouseReporting() || session.usingAlternateBuffer()) {
			// xterm wheel-up is JediTerm's SCROLLDOWN code.
			int code = scrollY > 0 ? MouseButtonCodes.SCROLLDOWN : MouseButtonCodes.SCROLLUP;
			for (int i = 0; i < steps; i++) {
				session.mouseEvent(col(x), row(y), new MouseWheelEvent(code, 0, 1));
			}
		} else {
			scrollBack = Math.max(0, scrollBack + (scrollY > 0 ? 3 : -3) * steps);
		}
		return true;
	}
}
