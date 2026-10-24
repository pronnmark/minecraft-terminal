package dev.pronnmark.mcterm;

import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A sign that says pc, seen as a computer: which one it is and where its parts sit. Coordinates are in
 * the computer's frame: origin at the bottom centre of the sign's block, +y up, +z out of the screen.
 * A standing sign is a monitor on a stand with a keyboard; a wall sign is a monitor on the wall.
 */
record Computer(String name, float angle, boolean wall) {
	static final float SCREEN_W = 0.9f, SCREEN_H = 0.5f, BEZEL = 0.03f, CHIN = 0.06f;

	static Computer of(SignBlockEntity sign) {
		BlockState state = sign.getBlockState();
		float angle;
		boolean wall;
		if (state.getBlock() instanceof WallSignBlock) {
			angle = state.getValue(WallSignBlock.FACING).toYRot();
			wall = true;
		} else if (state.getBlock() instanceof StandingSignBlock) {
			angle = RotationSegment.convertToDegrees(state.getValue(StandingSignBlock.ROTATION));
			wall = false;
		} else {
			return null;
		}
		String name = McTermClient.computerName(sign.getText(SignTextSlot.FRONT), sign.getBlockPos());
		if (name == null) {
			// Written on the back only: the computer faces the back.
			name = McTermClient.computerName(sign.getText(SignTextSlot.BACK), sign.getBlockPos());
			if (name == null) return null;
			angle += 180;
		}
		return new Computer(name, angle, wall);
	}

	/** Bottom of the monitor housing. */
	float bottom() {
		return wall ? 0.2f : 0.31f;
	}

	/** Front face of the monitor housing; a wall monitor hangs flat on the wall behind the sign. */
	float front() {
		return wall ? -0.44f : 0f;
	}

	float screenBottom() {
		return bottom() + CHIN;
	}

	float screenTop() {
		return screenBottom() + SCREEN_H;
	}

	/** World position of the middle of the screen, for turning the player towards it. */
	Vec3 screenCenter(BlockPos pos) {
		Vector3f v = new Vector3f(0, screenBottom() + SCREEN_H / 2, front());
		v.rotate(Axis.YP.rotationDegrees(-angle));
		return new Vec3(pos.getX() + 0.5 + v.x, pos.getY() + v.y, pos.getZ() + 0.5 + v.z);
	}
}
