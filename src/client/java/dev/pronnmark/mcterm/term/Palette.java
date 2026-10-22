package dev.pronnmark.mcterm.term;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;

/** Catppuccin Mocha, matching the WezTerm colour scheme on pmac. Colours are 0xRRGGBB. */
public final class Palette {
	public static final int BACKGROUND = 0x1e1e2e;
	public static final int FOREGROUND = 0xcdd6f4;
	public static final int CURSOR = 0xf5e0dc;
	public static final int BAR_BACKGROUND = 0x181825;
	public static final int BAR_FOREGROUND = 0x7f849c;

	private static final int[] ANSI = {
			0x45475a, 0xf38ba8, 0xa6e3a1, 0xf9e2af, 0x89b4fa, 0xf5c2e7, 0x94e2d5, 0xbac2de,
			0x585b70, 0xf38ba8, 0xa6e3a1, 0xf9e2af, 0x89b4fa, 0xf5c2e7, 0x94e2d5, 0xa6adc8,
	};

	private Palette() {
	}

	/** Resolved foreground/background for one cell style, after inverse/dim/hidden. */
	public record Colors(int fg, int bg, boolean defaultBg) {}

	public static Colors resolve(TextStyle style) {
		TerminalColor fgColor = style.getForeground();
		TerminalColor bgColor = style.getBackground();
		int fg = fgColor == null ? FOREGROUND : rgb(fgColor);
		int bg = bgColor == null ? BACKGROUND : rgb(bgColor);
		boolean defaultBg = bgColor == null;
		if (style.hasOption(TextStyle.Option.INVERSE)) {
			int t = fg;
			fg = bg;
			bg = t;
			defaultBg = false;
		}
		if (style.hasOption(TextStyle.Option.DIM)) {
			fg = mix(fg, bg, 0.5f);
		}
		if (style.hasOption(TextStyle.Option.HIDDEN)) {
			fg = bg;
		}
		return new Colors(fg, bg, defaultBg);
	}

	public static int rgb(TerminalColor color) {
		if (color.isIndexed()) {
			int index = color.getColorIndex();
			if (index < 16) return ANSI[index];
			TerminalColor resolved = ColorPalette.getIndexedTerminalColor(index);
			return resolved == null ? FOREGROUND : rgb(resolved);
		}
		Color c = color.toColor();
		return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
	}

	public static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xff) * (1 - t) + ((b >> 16) & 0xff) * t);
		int g = Math.round(((a >> 8) & 0xff) * (1 - t) + ((b >> 8) & 0xff) * t);
		int bl = Math.round((a & 0xff) * (1 - t) + (b & 0xff) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
