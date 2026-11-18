package com.cordraconis.client;

import com.cordraconis.CorDraconis;
import com.cordraconis.client.ecran.ForgeRuniqueScreen;
import com.cordraconis.client.hud.JaugeBraiseHud;
import com.cordraconis.client.rendu.CendreuxRenderer;
import com.cordraconis.client.rendu.PremierGardienRenderer;
import com.cordraconis.registre.ModEntites;
import com.cordraconis.registre.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Point d'entrée côté client (le jeu installé chez le joueur).
 *
 * On y enregistre uniquement ce qui sert à l'affichage : rendus GeckoLib,
 * écran de la Forge runique, jauge de Braise du HUD...
 * Un serveur dédié n'exécute jamais cette classe.
 */
public class CorDraconisClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Quel "dessinateur" utiliser pour chaque créature
		EntityRendererRegistry.register(ModEntites.CENDREUX, CendreuxRenderer::new);
		EntityRendererRegistry.register(ModEntites.PREMIER_GARDIEN, PremierGardienRenderer::new);

		// Quel écran ouvrir pour chaque menu
		MenuScreens.register(ModMenus.FORGE_RUNIQUE, ForgeRuniqueScreen::new);

		// Interface de jeu
		JaugeBraiseHud.initialiser();

		CorDraconis.LOGGER.info("Cor Draconis : initialisation du client.");
	}
}
