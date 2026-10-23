package dev.pronnmark.mcterm;

import com.jediterm.terminal.TextStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.pronnmark.mcterm.term.Palette;
import dev.pronnmark.mcterm.term.TerminalSession;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.StandingSignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a monitor above every visible [pc] sign showing that computer's live terminal, so a room of
 * computers looks like one. Runs in Fabric's COLLECT_SUBMITS and draws in the sign's own text space
 * (1/96 block per unit, y down, +z towards the reader).
 */
final class ComputerMonitors {
	/** Monitor size in sign-text units (96 per block): 2 blocks wide, 16:10. */
	private static final float WIDTH = 192, HEIGHT = 120, BEZEL = 5;
	/** Gap between the top of the sign board (24 units above the text centre) and the monitor. */
	private static final float BOTTOM = -30;
	private static final double MAX_DISTANCE = 48;
	private static final int FULL_BRIGHT = 15728880;
	private static final int BEZEL_COLOR = 0xff11111b;
	private static final Style TERM = Style.EMPTY.withFont(
			new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcterm", "term_15")));
	private static final float CELL_H = Math.round(15 * 1.32f), BASELINE = Math.round(15 * 1.02f);

	private ComputerMonitors() {
	}

	static void submit(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		if (level == null || camera == null) return;
		// Signs come straight from the loaded chunks: by COLLECT_SUBMITS the game's own block-entity
		// render states have already been consumed, so the list is empty.
		int cx = SectionPos.blockToSectionCoord(camera.x), cz = SectionPos.blockToSectionCoord(camera.z);
		int r = (int) Math.ceil(MAX_DISTANCE / 16);
		PoseStack pose = context.poseStack();
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				if (!level.hasChunk(x, z)) continue;
				for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
					if (!(be instanceof SignBlockEntity sign) || sign instanceof HangingSignBlockEntity) continue;
					BlockPos pos = sign.getBlockPos();
					if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) continue;
					String front = McTermClient.computerName(sign.getText(SignTextSlot.FRONT));
					String back = McTermClient.computerName(sign.getText(SignTextSlot.BACK));
					if (front == null && back == null) continue;
					SignRenderState.SignTransformations t = transformations(sign.getBlockState());
					if (t == null) continue;
					for (int side = 0; side < 2; side++) {
						String name = side == 0 ? front : back;
						if (name == null) continue;
						pose.pushPose();
						pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
						pose.mulPose(side == 0 ? t.frontText() : t.backText());
						drawMonitor(mc.font, pose, context.submitNodeCollector(), name, McTermClient.computerSession(name));
						pose.popPose();
					}
				}
			}
		}
	}

	/** The same text transform vanilla uses for this standing or wall sign, so the monitor faces like the text. */
	private static SignRenderState.SignTransformations transformations(BlockState state) {
		if (state.getBlock() instanceof WallSignBlock) {
			return StandingSignRenderer.TRANSFORMATIONS.wallTransformation(state.getValue(WallSignBlock.FACING));
		}
		if (state.getBlock() instanceof StandingSignBlock) {
			return StandingSignRenderer.TRANSFORMATIONS.freeTransformations(state.getValue(StandingSignBlock.ROTATION));
		}
		return null;
	}

	private static void drawMonitor(Font font, PoseStack pose, SubmitNodeCollector out, String name, TerminalSession session) {
		float x0 = -WIDTH / 2, y1 = BOTTOM, y0 = y1 - HEIGHT;
		// Bezel slightly behind the glass: a frame from the front, and the monitor's back from behind.
		pose.pushPose();
		pose.translate(0, 0, -0.6f);
		background(out, pose, x0 - BEZEL, y0 - BEZEL, -x0 + BEZEL, y1 + BEZEL, BEZEL_COLOR);
		pose.popPose();
		background(out, pose, x0, y0, -x0, y1, argb(Palette.BACKGROUND));

		TerminalSession.Snapshot snap = session == null ? null : session.snapshot(0);
		pose.pushPose();
		pose.translate(0, 0, 0.3f);
		if (snap == null || snap.runs().isEmpty()) {
			String hint = session == null ? "right-click to turn on" : "starting...";
			centered(font, pose, out, name, y0 + HEIGHT / 2 - 12, Palette.FOREGROUND, 1.5f);
			centered(font, pose, out, hint, y0 + HEIGHT / 2 + 8, Palette.BAR_FOREGROUND, 1f);
		} else {
			drawScreen(font, pose, out, snap, x0, y0);
		}
		pose.popPose();
	}

	/** The terminal grid, scaled to fit the glass whatever size the player's screen made it. */
	private static void drawScreen(Font font, PoseStack pose, SubmitNodeCollector out, TerminalSession.Snapshot snap, float x0, float y0) {
		int cols = 1, rows = 1;
		for (TerminalSession.Run run : snap.runs()) {
			cols = Math.max(cols, run.col() + run.text().length());
			rows = Math.max(rows, run.row() + 1);
		}
		float cellW = Math.max(1, Math.round(font.width(FormattedCharSequence.forward("M", TERM))));
		float pad = 3;
		float scale = Math.min((WIDTH - 2 * pad) / (cols * cellW), (HEIGHT - 2 * pad) / (rows * CELL_H));
		pose.pushPose();
		pose.translate(x0 + pad, y0 + pad, 0);
		pose.scale(scale, scale, 1);
		for (TerminalSession.Run run : snap.runs()) {
			Palette.Colors c = Palette.resolve(run.style());
			if (c.defaultBg()) continue;
			float x = run.col() * cellW, y = run.row() * CELL_H;
			background(out, pose, x, y, x + run.text().length() * cellW, y + CELL_H, argb(c.bg()));
		}
		pose.translate(0, 0, 0.3f / scale);
		for (TerminalSession.Run run : snap.runs()) {
			if (run.blank() || run.text().isBlank()) continue;
			Palette.Colors c = Palette.resolve(run.style());
			Style st = run.style().hasOption(TextStyle.Option.BOLD) ? TERM.withBold(true) : TERM;
			text(out, pose, FormattedCharSequence.forward(run.text(), st), run.col() * cellW,
					run.row() * CELL_H + BASELINE - 7, c.fg());
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
		pose.translate(0, y, 0);
		pose.scale(scale, scale, 1);
		text(out, pose, seq, -font.width(seq) / 2f, 0, rgb);
		pose.popPose();
	}

	private static void text(SubmitNodeCollector out, PoseStack pose, FormattedCharSequence s, float x, float y, int rgb) {
		out.submitText(pose, x, y, s, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, argb(rgb), 0, 0);
	}

	private static void background(SubmitNodeCollector out, PoseStack pose, float x0, float y0, float x1, float y1, int argb) {
		out.submitTextBackground(pose, x0, y0, x1, y1, argb, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT);
	}

	private static int argb(int rgb) {
		return 0xff000000 | rgb;
	}
}
