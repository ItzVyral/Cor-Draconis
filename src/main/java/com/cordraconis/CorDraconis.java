package com.cordraconis;

import com.cordraconis.braise.SystemeBraise;
import com.cordraconis.registre.ModAttachements;
import com.cordraconis.registre.ModBlocs;
import com.cordraconis.registre.ModComposants;
import com.cordraconis.registre.ModEntites;
import com.cordraconis.registre.ModItems;
import com.cordraconis.registre.ModMenus;
import com.cordraconis.registre.ModOngletCreatif;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal du mod Cor Draconis.
 *
 * Fabric appelle {@link #onInitialize()} au démarrage du jeu, côté client
 * ET côté serveur. C'est ici qu'on enregistre tout ce qui existe dans le monde :
 * items, blocs, entités, sons...
 */
public class CorDraconis implements ModInitializer {

	/** Identifiant unique du mod : préfixe de toutes nos ressources ("cordraconis:epee_du_veilleur"). */
	public static final String MOD_ID = "cordraconis";

	/** Journal pour écrire des messages dans la console et dans logs/latest.log. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// L'ordre compte : les items ont besoin des blocs et des entités (œufs d'apparition).
		ModComposants.initialiser();
		ModAttachements.initialiser();
		ModBlocs.initialiser();
		ModEntites.initialiser();
		ModItems.initialiser();
		ModMenus.initialiser();
		ModOngletCreatif.initialiser();

		// Mécaniques de jeu
		SystemeBraise.initialiser();

		LOGGER.info("Cor Draconis : le Veilleur s'éveille.");
	}

	/**
	 * Raccourci pour créer un identifiant dans l'espace de noms du mod.
	 * Exemple : id("braise") donne "cordraconis:braise".
	 */
	public static Identifier id(final String chemin) {
		return Identifier.fromNamespaceAndPath(MOD_ID, chemin);
	}
}
