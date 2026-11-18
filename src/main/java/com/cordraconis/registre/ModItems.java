package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.cordraconis.epee.EpeeDuVeilleurItem;
import com.cordraconis.objet.EclatDuCoeurItem;
import com.cordraconis.objet.FioleDeCendreFroideItem;
import com.cordraconis.objet.SceauDuGardienItem;
import com.cordraconis.rune.Rune;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;

/**
 * Tous les objets du mod.
 */
public final class ModItems {

	// --- L'arme du Veilleur ---------------------------------------------------------

	/** Épée du Veilleur : une épée de diamant améliorée, avec 1 emplacement de rune et 1 sort. */
	public static final Item EPEE_DU_VEILLEUR = enregistrer(
		"epee_du_veilleur",
		EpeeDuVeilleurItem::new,
		new Item.Properties()
			.sword(ToolMaterial.DIAMOND, 4.0F, -2.4F)
			.rarity(Rarity.EPIC)
			.fireResistant()
	);

	// --- Pierres runiques (chacune porte sa rune dans le composant RUNE) ------------

	public static final Item RUNE_DE_FLAMME = pierreRunique("rune_de_flamme", Rune.FLAMME);
	public static final Item RUNE_DE_VIGUEUR = pierreRunique("rune_de_vigueur", Rune.VIGUEUR);
	public static final Item RUNE_DE_GIVRE = pierreRunique("rune_de_givre", Rune.GIVRE);
	public static final Item RUNE_DE_FOUDRE = pierreRunique("rune_de_foudre", Rune.FOUDRE);

	// --- Matériaux et objets de quête ------------------------------------------------

	/** Lâchée par les Cendreux : de la cendre encore tiède du sang de Vaelthor. */
	public static final Item CENDRE_ARDENTE = enregistrer("cendre_ardente", Item::new, new Item.Properties());

	/** Se boit pour refroidir la Braise. */
	public static final Item FIOLE_DE_CENDRE_FROIDE = enregistrer(
		"fiole_de_cendre_froide", FioleDeCendreFroideItem::new, new Item.Properties().stacksTo(16)
	);

	/** Brisé sur le sol, il élève l'arène et réveille le Premier Gardien. */
	public static final Item SCEAU_DU_GARDIEN = enregistrer(
		"sceau_du_gardien", SceauDuGardienItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
	);

	/** Le premier des 7 Éclats du cœur de Vaelthor. */
	public static final Item PREMIER_ECLAT = enregistrer(
		"premier_eclat",
		proprietes -> new EclatDuCoeurItem(proprietes, 1),
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
	);

	// --- Blocs et œufs d'apparition ---------------------------------------------------

	public static final Item FORGE_RUNIQUE = enregistrer(
		"forge_runique",
		proprietes -> new BlockItem(ModBlocs.FORGE_RUNIQUE, proprietes.useBlockDescriptionPrefix()),
		new Item.Properties()
	);

	public static final Item OEUF_CENDREUX = enregistrer(
		"cendreux_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntites.CENDREUX)
	);

	public static final Item OEUF_PREMIER_GARDIEN = enregistrer(
		"premier_gardien_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntites.PREMIER_GARDIEN)
	);

	private ModItems() {
	}

	/** Une pierre runique est un simple Item qui porte une rune par défaut. */
	private static Item pierreRunique(final String nom, final Rune rune) {
		return enregistrer(nom, Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).component(ModComposants.RUNE, rune));
	}

	private static Item enregistrer(final String nom, final Function<Item.Properties, Item> fabrique, final Item.Properties proprietes) {
		ResourceKey<Item> cle = ResourceKey.create(Registries.ITEM, CorDraconis.id(nom));
		Item item = fabrique.apply(proprietes.setId(cle));

		// NOTION JAVA — "instanceof ... blocItem" : teste le type ET crée une variable
		// déjà convertie si le test réussit (pattern matching).
		if (item instanceof BlockItem blocItem) {
			blocItem.registerBlocks(Item.BY_BLOCK, item);
		}

		return Registry.register(BuiltInRegistries.ITEM, cle, item);
	}

	public static void initialiser() {
	}
}
