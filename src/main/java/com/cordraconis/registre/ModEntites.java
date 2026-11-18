package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.cordraconis.entite.cendreux.CendreuxEntity;
import com.cordraconis.entite.gardien.PremierGardienEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Toutes les créatures du mod, leurs attributs et leurs règles d'apparition.
 */
public final class ModEntites {

	/** Cendreux : statue de cendre animée qui se reforme tant que son cœur bat. */
	public static final EntityType<CendreuxEntity> CENDREUX = enregistrer(
		"cendreux",
		EntityType.Builder.<CendreuxEntity>of(CendreuxEntity::new, MobCategory.MONSTER)
			.sized(0.7F, 1.95F)
			.eyeHeight(1.7F)
			.clientTrackingRange(8)
	);

	/** Premier Gardien : chevalier parjure changé en cendre, gardien du 1er Éclat. */
	public static final EntityType<PremierGardienEntity> PREMIER_GARDIEN = enregistrer(
		"premier_gardien",
		EntityType.Builder.<PremierGardienEntity>of(PremierGardienEntity::new, MobCategory.MONSTER)
			.sized(1.6F, 3.6F)
			.eyeHeight(3.2F)
			.fireImmune()
			.clientTrackingRange(10)
	);

	private ModEntites() {
	}

	// NOTION JAVA — méthode générique "<T extends Entity>" : T est un type "à remplir".
	// La même méthode marche pour CendreuxEntity, PremierGardienEntity, etc.
	private static <T extends Entity> EntityType<T> enregistrer(final String nom, final EntityType.Builder<T> constructeur) {
		ResourceKey<EntityType<?>> cle = ResourceKey.create(Registries.ENTITY_TYPE, CorDraconis.id(nom));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, cle, constructeur.build(cle));
	}

	public static void initialiser() {
		// Attributs de base (vie, dégâts, vitesse...) : obligatoires pour toute créature vivante.
		FabricDefaultAttributeRegistry.register(CENDREUX, CendreuxEntity.creerAttributs());
		FabricDefaultAttributeRegistry.register(PREMIER_GARDIEN, PremierGardienEntity.creerAttributs());

		// Les Cendreux apparaissent au sol, dans le noir, comme les zombies...
		SpawnPlacements.register(CENDREUX, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		// ... dans tous les biomes de la surface (poids 25, groupes de 1 à 3).
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, CENDREUX, 25, 1, 3);
	}
}
