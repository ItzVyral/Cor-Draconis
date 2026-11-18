package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.cordraconis.rune.Rune;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Onglet "Cor Draconis" de l'inventaire créatif.
 */
public final class ModOngletCreatif {

	public static final ResourceKey<CreativeModeTab> CLE = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CorDraconis.id("principal"));

	private ModOngletCreatif() {
	}

	public static void initialiser() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CLE, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.cordraconis.main"))
			.icon(() -> new ItemStack(ModItems.EPEE_DU_VEILLEUR))
			.displayItems((parametres, sortie) -> {
				sortie.accept(ModItems.EPEE_DU_VEILLEUR);

				// Une épée déjà gravée pour chaque rune, pratique pour tester.
				// NOTION JAVA — boucle "for-each" : répète le bloc pour chaque valeur de Rune.values().
				for (Rune rune : Rune.values()) {
					ItemStack epeeGravee = new ItemStack(ModItems.EPEE_DU_VEILLEUR);
					epeeGravee.set(ModComposants.RUNE, rune);
					sortie.accept(epeeGravee);
				}

				sortie.accept(ModItems.RUNE_DE_FLAMME);
				sortie.accept(ModItems.RUNE_DE_VIGUEUR);
				sortie.accept(ModItems.RUNE_DE_GIVRE);
				sortie.accept(ModItems.RUNE_DE_FOUDRE);
				sortie.accept(ModItems.FORGE_RUNIQUE);
				sortie.accept(ModItems.CENDRE_ARDENTE);
				sortie.accept(ModItems.FIOLE_DE_CENDRE_FROIDE);
				sortie.accept(ModItems.SCEAU_DU_GARDIEN);
				sortie.accept(ModItems.PREMIER_ECLAT);
				sortie.accept(ModItems.OEUF_CENDREUX);
				sortie.accept(ModItems.OEUF_PREMIER_GARDIEN);
			})
			.build());
	}
}
