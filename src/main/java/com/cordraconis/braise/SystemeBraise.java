package com.cordraconis.braise;

import com.cordraconis.CorDraconis;
import com.cordraconis.objet.EclatDuCoeurItem;
import com.cordraconis.registre.ModAttachements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Le système de Braise : la chaleur du sang de dragon qui monte dans le Veilleur.
 *
 *  - Chaque sort ajoute de la Braise (selon la rune).
 *  - Elle redescend toute seule, un peu chaque seconde.
 *  - Au-delà du SEUIL, c'est l'EMBRASEMENT : le joueur perd des cœurs maximum,
 *    mais ses coups et ses sorts deviennent plus puissants.
 *  - Au MAXIMUM, c'est la SURCHAUFFE : le joueur prend feu et la Braise retombe un peu.
 *
 * NOTION JAVA — "static" : ces méthodes appartiennent à la classe elle-même, pas à un objet.
 * On les appelle directement : SystemeBraise.ajouter(joueur, 10).
 */
public final class SystemeBraise {

	public static final float MAXIMUM = 100.0F;
	public static final float SEUIL = 60.0F;

	/** Braise perdue chaque seconde. Doublée si le joueur porte un Éclat du cœur. */
	private static final float DECLIN_PAR_SECONDE = 2.5F;
	/** Nombre maximum de cœurs perdus en plein Embrasement. */
	private static final int COEURS_PERDUS_MAX = 4;
	/** Bonus de dégâts maximum (en points) en plein Embrasement. */
	private static final double BONUS_DEGATS_MAX = 6.0;

	private static final Identifier MODIF_VIE = CorDraconis.id("embrasement_vie");
	private static final Identifier MODIF_FORCE = CorDraconis.id("embrasement_force");

	private SystemeBraise() {
	}

	public static void initialiser() {
		// Une fois par seconde (toutes les 20 ticks), on met à jour chaque joueur connecté.
		ServerTickEvents.END_SERVER_TICK.register(SystemeBraise::apresTickServeur);
	}

	private static void apresTickServeur(final MinecraftServer serveur) {
		if (serveur.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer joueur : serveur.getPlayerList().getPlayers()) {
			mettreAJour(joueur);
		}
	}

	// --- Lecture / écriture -----------------------------------------------------------

	/** Braise actuelle du joueur (fonctionne aussi côté client grâce à la synchronisation). */
	public static float obtenir(final Player joueur) {
		return joueur.getAttachedOrElse(ModAttachements.BRAISE, 0.0F);
	}

	private static void definir(final Player joueur, final float valeur) {
		float bornee = Mth.clamp(valeur, 0.0F, MAXIMUM);
		// On n'écrit que si la valeur change, pour éviter d'envoyer des paquets réseau inutiles.
		if (bornee != obtenir(joueur)) {
			joueur.setAttached(ModAttachements.BRAISE, bornee);
		}
	}

	/** Ajoute de la Braise (après un sort). Déclenche la Surchauffe si on atteint le maximum. */
	public static void ajouter(final ServerPlayer joueur, final float quantite) {
		boolean etaitEmbrase = estEmbrase(joueur);
		definir(joueur, obtenir(joueur) + quantite);

		if (!etaitEmbrase && estEmbrase(joueur)) {
			joueur.sendOverlayMessage(Component.translatable("message.cordraconis.embrasement").withStyle(ChatFormatting.GOLD));
			joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
		if (obtenir(joueur) >= MAXIMUM) {
			surchauffe(joueur);
		}
		appliquerModificateurs(joueur);
	}

	/** Retire de la Braise (Fiole de cendre froide...). */
	public static void retirer(final ServerPlayer joueur, final float quantite) {
		definir(joueur, obtenir(joueur) - quantite);
		appliquerModificateurs(joueur);
	}

	// --- Calculs ----------------------------------------------------------------------

	public static boolean estEmbrase(final Player joueur) {
		return obtenir(joueur) >= SEUIL;
	}

	/** Part de l'Embrasement, de 0 (sous le seuil) à 1 (Braise au maximum). */
	public static float excedent(final Player joueur) {
		return Math.max(0.0F, obtenir(joueur) - SEUIL) / (MAXIMUM - SEUIL);
	}

	/** Multiplicateur de puissance des sorts : de x1 à x2 en plein Embrasement. */
	public static float multiplicateurPuissance(final Player joueur) {
		return 1.0F + excedent(joueur);
	}

	// --- Mise à jour périodique -------------------------------------------------------

	private static void mettreAJour(final ServerPlayer joueur) {
		if (obtenir(joueur) <= 0.0F) {
			appliquerModificateurs(joueur);
			return;
		}

		float declin = DECLIN_PAR_SECONDE;
		if (joueur.getInventory().contains(objet -> objet.getItem() instanceof EclatDuCoeurItem)) {
			declin *= 2.0F; // l'Éclat apaise la flamme
		}
		definir(joueur, obtenir(joueur) - declin);
		appliquerModificateurs(joueur);

		// Le joueur embrasé laisse échapper des braises à ses pieds.
		// (Pas au niveau des yeux : en vue à la 1re personne, elles masqueraient l'écran.)
		if (estEmbrase(joueur) && joueur.level() instanceof ServerLevel niveau) {
			niveau.sendParticles(ParticleTypes.SMALL_FLAME, joueur.getX(), joueur.getY() + 0.2, joueur.getZ(), 4, 0.35, 0.1, 0.35, 0.01);
			niveau.sendParticles(ParticleTypes.SMOKE, joueur.getX(), joueur.getY() + 0.1, joueur.getZ(), 2, 0.3, 0.05, 0.3, 0.01);
		}
	}

	/**
	 * Met à jour les modificateurs d'attributs liés à l'Embrasement.
	 *
	 * NOTION — modificateur d'attribut : une valeur ajoutée temporairement à une
	 * statistique du joueur (vie max, dégâts...). Identifiée par un id, on peut
	 * la remplacer ou la retirer à tout moment.
	 */
	private static void appliquerModificateurs(final ServerPlayer joueur) {
		float excedent = excedent(joueur);

		AttributeInstance vieMax = joueur.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance force = joueur.getAttribute(Attributes.ATTACK_DAMAGE);
		if (vieMax == null || force == null) {
			return;
		}

		if (excedent > 0.0F) {
			// Cœurs entiers perdus : 1 à 4 selon l'Embrasement (1 cœur = 2 points de vie).
			int coeursPerdus = Math.max(1, Mth.ceil(excedent * COEURS_PERDUS_MAX));
			vieMax.addOrUpdateTransientModifier(new AttributeModifier(MODIF_VIE, -2.0 * coeursPerdus, AttributeModifier.Operation.ADD_VALUE));
			force.addOrUpdateTransientModifier(new AttributeModifier(MODIF_FORCE, BONUS_DEGATS_MAX * excedent, AttributeModifier.Operation.ADD_VALUE));
			if (joueur.getHealth() > joueur.getMaxHealth()) {
				joueur.setHealth(joueur.getMaxHealth());
			}
		} else {
			vieMax.removeModifier(MODIF_VIE);
			force.removeModifier(MODIF_FORCE);
		}
	}

	/** Trop de Braise : le Veilleur s'enflamme et une partie de la chaleur s'échappe. */
	private static void surchauffe(final ServerPlayer joueur) {
		joueur.igniteForSeconds(4.0F);
		joueur.sendOverlayMessage(Component.translatable("message.cordraconis.surchauffe").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.5F);
		if (joueur.level() instanceof ServerLevel niveau) {
			niveau.sendParticles(ParticleTypes.LAVA, joueur.getX(), joueur.getY() + 0.3, joueur.getZ(), 20, 0.6, 0.2, 0.6, 0.0);
			joueur.hurtServer(niveau, niveau.damageSources().onFire(), 4.0F);
		}
		definir(joueur, 75.0F);
	}
}
