package com.cordraconis.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * La Forge runique : un clic droit ouvre l'interface de gravure.
 *
 * Elle est orientée (sa face gravée regarde le joueur qui la pose), comme un métier à tisser.
 * NOTION — "BlockState" : un même bloc peut avoir plusieurs états (ici sa direction, FACING).
 */
public class ForgeRuniqueBlock extends HorizontalDirectionalBlock {

	private static final Component TITRE = Component.translatable("container.cordraconis.forge_runique");

	public ForgeRuniqueBlock(final BlockBehaviour.Properties proprietes) {
		super(proprietes);
	}

	/** Clic droit à main vide (ou avec un objet sans action) : ouvre le menu côté serveur. */
	@Override
	protected InteractionResult useWithoutItem(final BlockState etat, final Level niveau, final BlockPos position, final Player joueur, final BlockHitResult impact) {
		if (!niveau.isClientSide()) {
			joueur.openMenu(etat.getMenuProvider(niveau, position));
		}
		return InteractionResult.SUCCESS;
	}

	/** Fabrique le menu quand un joueur ouvre la forge. */
	@Override
	protected MenuProvider getMenuProvider(final BlockState etat, final Level niveau, final BlockPos position) {
		return new SimpleMenuProvider(
			(idConteneur, inventaire, joueur) -> new ForgeRuniqueMenu(idConteneur, inventaire, ContainerLevelAccess.create(niveau, position)),
			TITRE
		);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext contexte) {
		return this.defaultBlockState().setValue(FACING, contexte.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> constructeur) {
		constructeur.add(FACING);
	}

	/** Effet visuel côté client : quelques braises s'échappent du dessus de la forge. */
	@Override
	public void animateTick(final BlockState etat, final Level niveau, final BlockPos position, final RandomSource aleatoire) {
		if (aleatoire.nextInt(3) == 0) {
			double x = position.getX() + 0.2 + aleatoire.nextDouble() * 0.6;
			double y = position.getY() + 1.02;
			double z = position.getZ() + 0.2 + aleatoire.nextDouble() * 0.6;
			niveau.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.01, 0.0);
		}
		if (aleatoire.nextInt(8) == 0) {
			niveau.addParticle(ParticleTypes.ASH, position.getX() + 0.5, position.getY() + 1.1, position.getZ() + 0.5, 0.0, 0.0, 0.0);
		}
	}
}
