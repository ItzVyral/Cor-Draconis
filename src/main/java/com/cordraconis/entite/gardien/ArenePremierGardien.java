package com.cordraconis.entite.gardien;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Construit l'arène circulaire du Premier Gardien, bloc par bloc.
 *
 *  - un sol de dalles de deepslate, avec des anneaux de pierre noire ;
 *  - un autel au centre ;
 *  - un mur circulaire percé de 4 entrées (nord, sud, est, ouest) ;
 *  - 8 piliers de basalte surmontés de feux de camp des âmes ;
 *  - l'intérieur est vidé sur 8 blocs de haut pour laisser la place au combat.
 */
public final class ArenePremierGardien {

	/** Rayon intérieur de l'arène, en blocs. Le Gardien ne peut pas s'en éloigner. */
	public static final int RAYON = 12;
	private static final int HAUTEUR_LIBRE = 8;
	private static final int HAUTEUR_MUR = 4;
	private static final int NOMBRE_PILIERS = 8;

	private ArenePremierGardien() {
	}

	/**
	 * @param centre le bloc juste au-dessus du sol, au centre de l'arène
	 */
	public static void construire(final ServerLevel niveau, final BlockPos centre) {
		RandomSource aleatoire = niveau.getRandom();
		int ySol = centre.getY() - 1;

		// NOTION JAVA — boucles imbriquées : on parcourt un carré de blocs autour du centre,
		// puis on garde seulement ceux qui tombent dans le cercle (distance au centre).
		for (int dx = -RAYON - 1; dx <= RAYON + 1; dx++) {
			for (int dz = -RAYON - 1; dz <= RAYON + 1; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;

				if (distance <= RAYON + 0.5) {
					poser(niveau, x, ySol - 1, z, Blocks.DEEPSLATE.defaultBlockState());
					poser(niveau, x, ySol, z, blocDuSol(distance, aleatoire));
					for (int y = 1; y <= HAUTEUR_LIBRE; y++) {
						poser(niveau, x, ySol + y, z, Blocks.AIR.defaultBlockState());
					}
				} else if (distance <= RAYON + 1.5 && !estUneEntree(dx, dz)) {
					poser(niveau, x, ySol, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					for (int y = 1; y <= HAUTEUR_MUR; y++) {
						BlockState mur = (y == HAUTEUR_MUR)
							? Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState()
							: (aleatoire.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS).defaultBlockState();
						poser(niveau, x, ySol + y, z, mur);
					}
				}
			}
		}

		construirePiliers(niveau, centre, ySol);
		construireAutel(niveau, centre, ySol);
	}

	/** Motif du sol : des anneaux de pierre noire tous les 4 blocs, des dalles fissurées ailleurs. */
	private static BlockState blocDuSol(final double distance, final RandomSource aleatoire) {
		int anneau = (int) Math.round(distance);
		if (anneau % 4 == 0) {
			return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		if (aleatoire.nextInt(10) == 0) {
			return Blocks.MAGMA_BLOCK.defaultBlockState(); // braises au sol : attention où l'on marche !
		}
		return (aleatoire.nextInt(3) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES).defaultBlockState();
	}

	/** Les 4 entrées : là où le mur croise les axes nord-sud et est-ouest. */
	private static boolean estUneEntree(final int dx, final int dz) {
		return Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
	}

	private static void construirePiliers(final ServerLevel niveau, final BlockPos centre, final int ySol) {
		for (int i = 0; i < NOMBRE_PILIERS; i++) {
			double angle = (Math.PI * 2.0 / NOMBRE_PILIERS) * i + Math.PI / NOMBRE_PILIERS;
			int x = centre.getX() + (int) Math.round(Math.cos(angle) * (RAYON - 2));
			int z = centre.getZ() + (int) Math.round(Math.sin(angle) * (RAYON - 2));
			for (int y = 1; y <= 4; y++) {
				poser(niveau, x, ySol + y, z, Blocks.POLISHED_BASALT.defaultBlockState());
			}
			poser(niveau, x, ySol + 5, z, Blocks.SOUL_CAMPFIRE.defaultBlockState());
		}
	}

	private static void construireAutel(final ServerLevel niveau, final BlockPos centre, final int ySol) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Block bloc = (dx == 0 && dz == 0) ? Blocks.CRYING_OBSIDIAN : Blocks.CHISELED_POLISHED_BLACKSTONE;
				poser(niveau, centre.getX() + dx, ySol, centre.getZ() + dz, bloc.defaultBlockState());
			}
		}
	}

	private static void poser(final ServerLevel niveau, final int x, final int y, final int z, final BlockState etat) {
		// Le drapeau 3 = prévenir les blocs voisins ET envoyer la mise à jour aux joueurs.
		niveau.setBlock(new BlockPos(x, y, z), etat, Block.UPDATE_ALL);
	}
}
