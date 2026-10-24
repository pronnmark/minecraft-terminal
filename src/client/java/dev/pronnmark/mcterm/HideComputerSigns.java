package dev.pronnmark.mcterm;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * In 26.3 a sign's wooden board is part of the terrain mesh, not the block entity renderer, so a
 * computer would stand inside its sign. This leaves the board out of the mesh for signs that say pc,
 * and rebuilds a sign's chunk section when it becomes (or stops being) a computer.
 */
final class HideComputerSigns {
	private static final Set<BlockPos> computers = ConcurrentHashMap.newKeySet();

	private HideComputerSigns() {
	}

	static void register() {
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) -> {
			BlockState state = context.state();
			boolean plain = state.getBlock() instanceof StandingSignBlock || state.getBlock() instanceof WallSignBlock;
			return plain ? new Model(model) : model;
		}));
	}

	/** Called for each sign the game draws: rebuilds its section when it turns into or out of a computer. */
	static void seen(SignBlockEntity sign, boolean computer) {
		BlockPos pos = sign.getBlockPos().immutable();
		boolean changed = computer ? computers.add(pos) : computers.remove(pos);
		if (changed && Minecraft.getInstance().level != null) {
			Minecraft.getInstance().level.setSectionDirtyWithNeighbors(
					SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
					SectionPos.blockToSectionCoord(pos.getZ()));
		}
	}

	private static final class Model extends WrapperBlockStateModel {
		Model(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
							  Predicate<Direction> cullTest) {
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign && Computer.of(sign) != null) return;
			super.emitQuads(emitter, level, pos, state, random, cullTest);
		}
	}
}
