package dev.pronnmark.mcterm;

import com.jediterm.terminal.TextStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.pronnmark.mcterm.term.Palette;
import dev.pronnmark.mcterm.term.TerminalSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.StandingSignRenderer;
import net.minecraft.client.renderer.blockentity.state.StandingSignRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The sign renderer, replaced: a sign that says pc is drawn as a computer instead of a sign, its
 * screen showing that computer's live terminal. Every other sign is drawn exactly as vanilla does.
 */
final class ComputerRenderer extends StandingSignRenderer {
	private static final RenderType SOLID = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath("mcterm", "textures/misc/white.png"));
	private static final int FULL_BRIGHT = 15728880;
	private static final int CASE = 0xff2b2b33, CASE_BACK = 0xff222228, STAND = 0xff3a3a44, KEY = 0xff17171c, LED = 0xff40d060;
	/** Screen content units: 192 across the glass, like a sign's 1/96-block text space but finer. */
	private static final float UNITS = 192, UNIT = Computer.SCREEN_W / UNITS;
	private static final Style TERM = Style.EMPTY.withFont(
			new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcterm", "term_15")));
	private static final float CELL_H = Math.round(15 * 1.32f), BASELINE = Math.round(15 * 1.02f);

	static final class State extends StandingSignRenderState {
		Computer computer;
	}

	ComputerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public StandingSignRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SignBlockEntity sign, StandingSignRenderState state, float partialTick, Vec3 camera,
								   ModelFeatureRenderer.CrumblingOverlay crumbling) {
		super.extractRenderState(sign, state, partialTick, camera, crumbling);
		Computer computer = Computer.of(sign);
		((State) state).computer = computer;
		HideComputerSigns.seen(sign, computer != null);
	}

	@Override
	public void submit(StandingSignRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		Computer c = ((State) state).computer;
		if (c == null) {
			super.submit(state, pose, out, camera);
			return;
		}
		pose.pushPose();
		pose.translate(0.5f, 0, 0.5f);
		pose.rotate(Axis.YP.rotationDegrees(-c.angle()));
		int light = state.lightCoords;
		out.submitCustomGeometry(pose, SOLID, (p, vc) -> body(p, vc, c, light));
		screen(pose, out, c);
		pose.popPose();
	}

	// ---- the case ----

	private static void body(PoseStack.Pose p, VertexConsumer vc, Computer c, int light) {
		float hw = Computer.SCREEN_W / 2 + Computer.BEZEL;
		float y0 = c.bottom(), y1 = c.screenTop() + Computer.BEZEL, z1 = c.front(), z0 = z1 - 0.06f;
		box(p, vc, -hw, y0, z0, hw, y1, z1, CASE, light);
		box(p, vc, -0.32f, y0 + 0.1f, z0 - 0.04f, 0.32f, y1 - 0.1f, z0, CASE_BACK, light);
		// Power light on the chin, lit whatever the room's light.
		float ly = y0 + Computer.CHIN / 2;
		box(p, vc, hw - 0.07f, ly - 0.008f, z1, hw - 0.05f, ly + 0.008f, z1 + 0.004f, LED, FULL_BRIGHT);
		if (c.wall()) return;
		box(p, vc, -0.045f, 0.02f, z0 - 0.03f, 0.045f, y0 + 0.12f, z0 + 0.01f, STAND, light);
		box(p, vc, -0.2f, 0, -0.22f, 0.2f, 0.02f, 0.02f, STAND, light);
		// Keyboard: a slab with four rows of keys and a space bar.
		float kx = 0.3f, kz0 = 0.1f, kz1 = 0.3f, top = 0.025f;
		box(p, vc, -kx, 0, kz0, kx, top, kz1, CASE, light);
		int cols = 12;
		float pitch = (2 * kx - 0.03f) / cols, size = pitch * 0.8f;
		for (int row = 0; row < 4; row++) {
			float z = kz0 + 0.018f + row * pitch;
			for (int col = 0; col < cols; col++) {
				float x = -kx + 0.015f + col * pitch + (pitch - size) / 2;
				box(p, vc, x, top, z, x + size, top + 0.008f, z + size, KEY, light);
			}
		}
		float sz = kz0 + 0.018f + 4 * pitch;
		box(p, vc, -0.12f, top, sz, 0.12f, top + 0.008f, Math.min(kz1 - 0.01f, sz + size), KEY, light);
	}

	/** An axis-aligned box with outward, counter-clockwise faces (the solid entity type culls back faces). */
	private static void box(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light) {
		quad(p, vc, argb, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(p, vc, argb, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		quad(p, vc, argb, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		quad(p, vc, argb, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(p, vc, argb, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		quad(p, vc, argb, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
	}

	private static void quad(PoseStack.Pose p, VertexConsumer vc, int argb, int light, float nx, float ny, float nz, float... v) {
		for (int i = 0; i < 12; i += 3) {
			vc.addVertex(p, v[i], v[i + 1], v[i + 2]).setColor(argb).setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		}
	}

	// ---- the screen ----

	private static void screen(PoseStack pose, SubmitNodeCollector out, Computer c) {
		Font font = Minecraft.getInstance().font;
		String name = c.name();
		TerminalSession session = McTermClient.computerSession(name);
		float height = Computer.SCREEN_H / UNIT;
		pose.pushPose();
		// Text space: origin at the screen's top-left, y down, just in front of the glass.
		pose.translate(-Computer.SCREEN_W / 2, c.screenTop(), c.front() + 0.003f);
		pose.scale(UNIT, -UNIT, UNIT);
		background(out, pose, 0, 0, UNITS, height, 0xff000000 | Palette.BACKGROUND);
		TerminalSession.Snapshot snap = session == null ? null : session.snapshot(0);
		pose.translate(0, 0, 0.3f);
		if (snap == null || snap.runs().isEmpty()) {
			centered(font, pose, out, name, height / 2 - 12, Palette.FOREGROUND, 1.5f);
			centered(font, pose, out, session == null ? "right-click to use" : "starting...", height / 2 + 8, Palette.BAR_FOREGROUND, 1f);
		} else {
			grid(font, pose, out, snap, height);
		}
		pose.popPose();
	}

	/** The terminal grid, scaled to fill the glass whatever size the player's window made it. */
	private static void grid(Font font, PoseStack pose, SubmitNodeCollector out, TerminalSession.Snapshot snap, float height) {
		int cols = 1, rows = 1;
		for (TerminalSession.Run run : snap.runs()) {
			cols = Math.max(cols, run.col() + run.text().length());
			rows = Math.max(rows, run.row() + 1);
		}
		float cellW = Math.max(1, font.width(FormattedCharSequence.forward("M", TERM)));
		float pad = 2;
		float scale = Math.min((UNITS - 2 * pad) / (cols * cellW), (height - 2 * pad) / (rows * CELL_H));
		pose.pushPose();
		pose.translate(pad, pad, 0);
		pose.scale(scale, scale, 1);
		for (TerminalSession.Run run : snap.runs()) {
			Palette.Colors col = Palette.resolve(run.style());
			if (col.defaultBg()) continue;
			float x = run.col() * cellW, y = run.row() * CELL_H;
			background(out, pose, x, y, x + run.text().length() * cellW, y + CELL_H, 0xff000000 | col.bg());
		}
		pose.translate(0, 0, 0.3f / scale);
		for (TerminalSession.Run run : snap.runs()) {
			if (run.blank() || run.text().isBlank()) continue;
			Palette.Colors col = Palette.resolve(run.style());
			Style st = run.style().hasOption(TextStyle.Option.BOLD) ? TERM.withBold(true) : TERM;
			text(out, pose, FormattedCharSequence.forward(run.text(), st), run.col() * cellW, run.row() * CELL_H + BASELINE - 7, col.fg());
		}
		if (snap.cursorVisible() && snap.cursorRow() >= 0 && snap.cursorRow() < rows) {
			float x = snap.cursorCol() * cellW, y = snap.cursorRow() * CELL_H;
			pose.translate(0, 0, 0.3f / scale);
			background(out, pose, x, y, x + cellW, y + CELL_H, 0xb0000000 | Palette.CURSOR);
		}
		pose.popPose();
	}

	private static void centered(Font font, PoseStack pose, SubmitNodeCollector out, String s, float y, int rgb, float scale) {
		FormattedCharSequence seq = FormattedCharSequence.forward(s, Style.EMPTY);
		pose.pushPose();
		pose.translate(UNITS / 2, y, 0);
		pose.scale(scale, scale, 1);
		text(out, pose, seq, -font.width(seq) / 2f, 0, rgb);
		pose.popPose();
	}

	private static void text(SubmitNodeCollector out, PoseStack pose, FormattedCharSequence s, float x, float y, int rgb) {
		out.submitText(pose, x, y, s, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, 0xff000000 | rgb, 0, 0);
	}

	private static void background(SubmitNodeCollector out, PoseStack pose, float x0, float y0, float x1, float y1, int argb) {
		out.submitTextBackground(pose, x0, y0, x1, y1, argb, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT);
	}
}
