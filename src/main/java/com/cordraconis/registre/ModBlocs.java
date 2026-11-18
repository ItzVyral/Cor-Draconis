package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.cordraconis.forge.ForgeRuniqueBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Tous les blocs du mod.
 *
 * NOTION — registre : Minecraft garde une "liste officielle" de chaque type de contenu
 * (blocs, items, entités...). Un bloc n'existe dans le jeu que s'il y est enregistré
 * sous un identifiant unique, ici "cordraconis:forge_runique".
 */
public final class ModBlocs {

	/** Forge runique : on y grave une rune sur l'Épée du Veilleur. Ses runes luisent faiblement. */
	public static final Block FORGE_RUNIQUE = enregistrer(
		"forge_runique",
		ForgeRuniqueBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(5.0F, 1200.0F)
			.requiresCorrectToolForDrops()
			.sound(SoundType.DEEPSLATE_BRICKS)
			.lightLevel(etat -> 7)
	);

	private ModBlocs() {
	}

	/**
	 * Enregistre un bloc.
	 *
	 * NOTION JAVA — Function<A, B> : une fonction passée en paramètre. Ici elle reçoit
	 * des propriétés et fabrique le bloc. "ForgeRuniqueBlock::new" est une "référence
	 * de constructeur" : un raccourci pour "proprietes -> new ForgeRuniqueBlock(proprietes)".
	 */
	private static Block enregistrer(final String nom, final Function<BlockBehaviour.Properties, Block> fabrique, final BlockBehaviour.Properties proprietes) {
		ResourceKey<Block> cle = ResourceKey.create(Registries.BLOCK, CorDraconis.id(nom));
		Block bloc = fabrique.apply(proprietes.setId(cle));
		return Registry.register(BuiltInRegistries.BLOCK, cle, bloc);
	}

	public static void initialiser() {
	}
}
