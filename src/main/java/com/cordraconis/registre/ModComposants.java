package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.cordraconis.rune.Rune;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Composants de données du mod.
 *
 * NOTION — composant de données : depuis Minecraft 1.20.5, chaque ItemStack transporte
 * une petite "fiche" de valeurs typées (durabilité, enchantements, nom...). On peut y
 * ajouter nos propres valeurs. Ici : la rune gravée sur l'épée ou portée par une pierre.
 */
public final class ModComposants {

	/** La rune portée par un objet (pierre runique ou Épée du Veilleur gravée). */
	public static final DataComponentType<Rune> RUNE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		CorDraconis.id("rune"),
		DataComponentType.<Rune>builder()
			.persistent(Rune.CODEC)              // sauvegardé avec le monde
			.networkSynchronized(Rune.STREAM_CODEC) // envoyé au client (pour l'affichage)
			.build()
	);

	// NOTION JAVA — constructeur privé : empêche de créer des objets "ModComposants".
	// Cette classe ne sert qu'à regrouper des constantes "static".
	private ModComposants() {
	}

	public static void initialiser() {
		// Affiche automatiquement la rune dans l'info-bulle de l'objet.
		ItemComponentTooltipProviderRegistry.addLast(RUNE);
	}
}
