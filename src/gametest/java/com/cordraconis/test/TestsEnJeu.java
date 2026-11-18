package com.cordraconis.test;

import com.cordraconis.CorDraconis;
import com.cordraconis.braise.SystemeBraise;
import com.cordraconis.entite.cendreux.CendreuxEntity;
import com.cordraconis.entite.gardien.ArenePremierGardien;
import com.cordraconis.entite.gardien.PremierGardienEntity;
import com.cordraconis.epee.SouffleDraconique;
import com.cordraconis.forge.ForgeRuniqueMenu;
import com.cordraconis.registre.ModBlocs;
import com.cordraconis.registre.ModComposants;
import com.cordraconis.registre.ModEntites;
import com.cordraconis.registre.ModItems;
import com.cordraconis.rune.Rune;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Tests automatiques EN JEU de Cor Draconis (lancés par ./gradlew runClientGameTest).
 *
 * Un vrai client Minecraft démarre, crée un monde plat, puis ce code :
 *  1. vérifie que recettes et tables de butin sont bien chargées ;
 *  2. met en scène le Veilleur, la forge et les créatures (capture d'écran) ;
 *  3. teste l'effondrement, la reformation puis la vraie mort du Cendreux ;
 *  4. lance le Souffle draconique et vérifie la Braise ;
 *  5. grave une rune dans la Forge runique (capture de l'interface) ;
 *  6. élève l'arène, réveille le Gardien et déclenche sa phase 2.
 *
 * Un échec lève une AssertionError : le test s'arrête et Gradle affiche l'erreur.
 */
public class TestsEnJeu implements FabricClientGameTest {

	private static final int Y_SOL = -60; // hauteur du sol d'un monde plat

	@Override
	public void runTest(final ClientGameTestContext contexte) {
		try (TestSingleplayerContext monde = contexte.worldBuilder().create()) {
			TestServerContext serveur = monde.getServer();
			monde.getConnection().waitForChunksRender();

			// --- 1. Données chargées -------------------------------------------------
			serveur.runOnServer(TestsEnJeu::verifierDonnees);

			// --- 2. Mise en scène ------------------------------------------------------
			serveur.runCommand("difficulty normal");
			serveur.runCommand("gamemode survival @a");
			serveur.runCommand("tp @a 0.5 " + Y_SOL + " 0.5 180 8");
			serveur.runOnServer(s -> {
				ServerPlayer joueur = joueur(s);
				ServerLevel niveau = joueur.level();
				niveau.setBlock(new BlockPos(-3, Y_SOL, -4), ModBlocs.FORGE_RUNIQUE.defaultBlockState(), 3);

				ItemStack epee = new ItemStack(ModItems.EPEE_DU_VEILLEUR);
				epee.set(ModComposants.RUNE, Rune.FLAMME);
				joueur.setItemInHand(InteractionHand.MAIN_HAND, epee);
				joueur.getInventory().add(new ItemStack(ModItems.RUNE_DE_GIVRE));
				joueur.getInventory().add(new ItemStack(ModItems.PREMIER_ECLAT));

				apparaitre(ModEntites.CENDREUX, niveau, 2.0, -5.0, true);
				apparaitre(ModEntites.PREMIER_GARDIEN, niveau, -1.0, -11.0, true);
				SystemeBraise.ajouter(joueur, 70.0F);
				verifier(SystemeBraise.estEmbrase(joueur), "70 de Braise devrait déclencher l'Embrasement");
				verifier(joueur.getMaxHealth() < 20.0F, "L'Embrasement devrait retirer des cœurs maximum");
			});
			contexte.waitTicks(40);
			contexte.takeScreenshot("cordraconis-1-scene");

			// --- 3. Le Cendreux s'effondre, se reforme, puis meurt cœur brisé --------
			serveur.runOnServer(s -> {
				CendreuxEntity cendreux = premier(s, CendreuxEntity.class);
				cendreux.hurtServer((ServerLevel) cendreux.level(), cendreux.damageSources().generic(), 100.0F);
				verifier(cendreux.isAlive() && cendreux.estEffondre(), "Le Cendreux aurait dû s'effondrer au lieu de mourir");
			});
			contexte.waitTicks(25);
			contexte.takeScreenshot("cordraconis-2-cendreux-effondre");
			contexte.waitTicks(100);
			serveur.runOnServer(s -> {
				CendreuxEntity cendreux = premier(s, CendreuxEntity.class);
				verifier(!cendreux.estEffondre(), "Le Cendreux aurait dû se reformer après 5 secondes");
				verifier(cendreux.getHealth() == cendreux.getMaxHealth(), "Le Cendreux reformé devrait avoir toute sa vie");
				cendreux.hurtServer((ServerLevel) cendreux.level(), cendreux.damageSources().generic(), 100.0F);
			});
			contexte.waitTicks(25);
			serveur.runOnServer(s -> {
				CendreuxEntity cendreux = premier(s, CendreuxEntity.class);
				verifier(cendreux.estEffondre(), "Deuxième effondrement attendu");
				// Le feu blesse le cœur deux fois plus : 10 x 2 = 20 > 8 PV de cœur.
				cendreux.hurtServer((ServerLevel) cendreux.level(), cendreux.damageSources().onFire(), 10.0F);
				verifier(!cendreux.isAlive(), "Le cœur brisé aurait dû tuer le Cendreux pour de bon");
			});
			contexte.waitTicks(30);

			// --- 4. Souffle draconique ------------------------------------------------
			serveur.runOnServer(s -> {
				ServerPlayer joueur = joueur(s);
				apparaitre(ModEntites.CENDREUX, joueur.level(), 0.5, -3.0, true);
			});
			contexte.waitTicks(5);
			serveur.runOnServer(s -> {
				ServerPlayer joueur = joueur(s);
				float avant = SystemeBraise.obtenir(joueur);
				int touches = SouffleDraconique.lancer(joueur.level(), joueur, Rune.FLAMME);
				SystemeBraise.ajouter(joueur, Rune.FLAMME.coutBraise());
				verifier(touches >= 1, "Le souffle aurait dû toucher le Cendreux devant le joueur (touchés : " + touches + ")");
				verifier(SystemeBraise.obtenir(joueur) > avant, "Le sort aurait dû ajouter de la Braise");
			});
			contexte.waitTicks(3);
			contexte.takeScreenshot("cordraconis-3-souffle-draconique");

			// --- 5. Forge runique -----------------------------------------------------
			serveur.runOnServer(s -> {
				ServerPlayer joueur = joueur(s);
				BlockPos forge = new BlockPos(-3, Y_SOL, -4);
				joueur.giveExperienceLevels(5);
				joueur.openMenu(joueur.level().getBlockState(forge).getMenuProvider(joueur.level(), forge));
				verifier(joueur.containerMenu instanceof ForgeRuniqueMenu, "La Forge runique aurait dû ouvrir son menu");
				ForgeRuniqueMenu menu = (ForgeRuniqueMenu) joueur.containerMenu;
				menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_EPEE).set(new ItemStack(ModItems.EPEE_DU_VEILLEUR));
				menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_RUNE).set(new ItemStack(ModItems.RUNE_DE_GIVRE));
				ItemStack resultat = menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_RESULTAT).getItem();
				verifier(resultat.get(ModComposants.RUNE) == Rune.GIVRE, "La forge aurait dû proposer une épée gravée de Givre");
			});
			contexte.waitTicks(10);
			contexte.takeScreenshot("cordraconis-4-forge-runique");
			serveur.runOnServer(s -> joueur(s).closeContainer());
			contexte.waitTicks(5);

			// --- 6. Arène et Premier Gardien ------------------------------------------
			serveur.runCommand("kill @e[type=!player]");
			serveur.runCommand("gamemode creative @a");
			BlockPos centre = new BlockPos(0, Y_SOL, -40);
			serveur.runOnServer(s -> {
				ServerPlayer joueur = joueur(s);
				ServerLevel niveau = joueur.level();
				ArenePremierGardien.construire(niveau, centre);
				PremierGardienEntity gardien = ModEntites.PREMIER_GARDIEN.create(niveau, EntitySpawnReason.TRIGGERED);
				gardien.snapTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
				gardien.lierAArene(centre);
				niveau.addFreshEntity(gardien);
				joueur.getAbilities().flying = true;
				joueur.onUpdateAbilities();
			});
			serveur.runCommand("tp @a 0.5 " + (Y_SOL + 11) + " -20 180 32");
			contexte.waitTicks(20);
			monde.getConnection().waitForChunksRender();
			contexte.takeScreenshot("cordraconis-5-arene");

			// Phase 2 : sous 50 % de vie, le Gardien invoque des Cendreux.
			serveur.runOnServer(s -> {
				PremierGardienEntity gardien = premier(s, PremierGardienEntity.class);
				gardien.setHealth(gardien.getMaxHealth() * 0.4F);
			});
			contexte.waitTicks(20);
			serveur.runOnServer(s -> {
				PremierGardienEntity gardien = premier(s, PremierGardienEntity.class);
				verifier(gardien.phase() == 2, "Le Gardien aurait dû passer en phase 2");
				int cendreux = joueur(s).level().getEntitiesOfClass(CendreuxEntity.class, gardien.getBoundingBox().inflate(16)).size();
				verifier(cendreux >= 1, "Le Gardien aurait dû invoquer des Cendreux");
			});
			serveur.runCommand("tp @a 0.5 " + (Y_SOL + 3) + " -30 180 10");
			contexte.waitTicks(30);
			contexte.takeScreenshot("cordraconis-6-gardien-phase2");
		}
	}

	// --- Outils -------------------------------------------------------------------

	private static void verifierDonnees(final MinecraftServer serveur) {
		List<String> recettes = List.of(
			"epee_du_veilleur", "forge_runique", "rune_de_flamme", "rune_de_vigueur", "rune_de_givre", "rune_de_foudre",
			"fiole_de_cendre_froide", "sceau_du_gardien"
		);
		for (String recette : recettes) {
			ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> cle = ResourceKey.create(Registries.RECIPE, CorDraconis.id(recette));
			verifier(serveur.getRecipeManager().byKey(cle).isPresent(), "Recette introuvable : " + recette);
		}
		for (String table : List.of("entities/cendreux", "entities/premier_gardien", "blocks/forge_runique")) {
			ResourceKey<LootTable> cle = ResourceKey.create(Registries.LOOT_TABLE, CorDraconis.id(table));
			verifier(serveur.reloadableRegistries().getLootTable(cle) != LootTable.EMPTY, "Table de butin introuvable : " + table);
		}
	}

	private static ServerPlayer joueur(final MinecraftServer serveur) {
		return serveur.getPlayerList().getPlayers().getFirst();
	}

	private static <T extends Mob> T apparaitre(final EntityType<T> type, final ServerLevel niveau, final double x, final double z, final boolean sansIa) {
		T creature = type.create(niveau, EntitySpawnReason.COMMAND);
		verifier(creature != null, "Impossible de créer " + type);
		creature.snapTo(x, Y_SOL, z, 0.0F, 0.0F); // tourné vers le sud, donc vers le joueur
		creature.setNoAi(sansIa);
		creature.setPersistenceRequired();
		niveau.addFreshEntity(creature);
		return creature;
	}

	private static <T extends Mob> T premier(final MinecraftServer serveur, final Class<T> classe) {
		ServerPlayer joueur = joueur(serveur);
		List<T> trouves = joueur.level().getEntitiesOfClass(classe, joueur.getBoundingBox().inflate(64.0));
		verifier(!trouves.isEmpty(), "Aucune entité " + classe.getSimpleName() + " trouvée");
		return trouves.getFirst();
	}

	private static void verifier(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
