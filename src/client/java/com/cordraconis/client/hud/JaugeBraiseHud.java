package com.cordraconis.client.hud;

import com.cordraconis.CorDraconis;
import com.cordraconis.braise.SystemeBraise;
import com.cordraconis.epee.EpeeDuVeilleurItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * La jauge de Braise, affichée au-dessus des cœurs (et de l'armure).
 *
 *  - Elle apparaît quand le joueur tient l'Épée du Veilleur ou a de la Braise.
 *  - Elle passe de l'orange au rouge en se remplissant.
 *  - Un repère clair marque le SEUIL d'Embrasement ; au-delà, la jauge pulse.
 *
 * Grâce à HudStatusBarHeightRegistry, Fabric décale automatiquement les autres barres
 * (absorption, etc.) pour qu'elles ne se superposent pas à la nôtre.
 */
public final class JaugeBraiseHud {

	public static final Identifier ID = CorDraconis.id("jauge_braise");

	private static final int LARGEUR = 81; // même largeur que la rangée de cœurs
	private static final int HAUTEUR = 5;
	private static final int MARGE = 2;

	private static final int COULEUR_CADRE = 0xFF2A160C;
	private static final int COULEUR_FOND = 0xFF120A06;
	private static final int COULEUR_FROIDE = 0xFFFFA020; // orange
	private static final int COULEUR_CHAUDE = 0xFFFF2010; // rouge vif
	private static final int COULEUR_SEUIL = 0xFFFFE8B0;

	private JaugeBraiseHud() {
	}

	public static void initialiser() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, JaugeBraiseHud::dessiner);
		HudStatusBarHeightRegistry.addLeft(ID, joueur -> estVisible(joueur) ? HAUTEUR + MARGE : 0);
	}

	private static boolean estVisible(final Player joueur) {
		if (joueur == null || joueur.isSpectator()) {
			return false;
		}
		return SystemeBraise.obtenir(joueur) > 0.0F || joueur.getMainHandItem().getItem() instanceof EpeeDuVeilleurItem;
	}

	private static void dessiner(final GuiGraphicsExtractor graphismes, final DeltaTracker temps) {
		Minecraft minecraft = Minecraft.getInstance();
		Player joueur = minecraft.player;
		if (!estVisible(joueur)) {
			return;
		}

		int x = graphismes.guiWidth() / 2 - 91;
		int y = graphismes.guiHeight() - HudStatusBarHeightRegistry.getHeight(ID);

		float braise = SystemeBraise.obtenir(joueur);
		float remplissage = braise / SystemeBraise.MAXIMUM;
		int largeurRemplie = Mth.floor(remplissage * (LARGEUR - 2));

		// Cadre et fond
		graphismes.fill(x, y, x + LARGEUR, y + HAUTEUR, COULEUR_CADRE);
		graphismes.fill(x + 1, y + 1, x + LARGEUR - 1, y + HAUTEUR - 1, COULEUR_FOND);

		// Remplissage : couleur interpolée entre orange et rouge.
		if (largeurRemplie > 0) {
			int couleur = melanger(COULEUR_FROIDE, COULEUR_CHAUDE, remplissage);
			if (SystemeBraise.estEmbrase(joueur)) {
				// Pulsation : la luminosité oscille avec le temps (sinus).
				float pulsation = 0.75F + 0.25F * Mth.sin((joueur.tickCount + temps.getGameTimeDeltaPartialTick(false)) * 0.4F);
				couleur = assombrir(couleur, pulsation);
			}
			graphismes.fill(x + 1, y + 1, x + 1 + largeurRemplie, y + HAUTEUR - 1, couleur);
			// Reflet clair sur la première ligne de pixels.
			graphismes.fill(x + 1, y + 1, x + 1 + largeurRemplie, y + 2, melanger(couleur, 0xFFFFFFFF, 0.35F));
		}

		// Repère du seuil d'Embrasement
		int xSeuil = x + 1 + Mth.floor(SystemeBraise.SEUIL / SystemeBraise.MAXIMUM * (LARGEUR - 2));
		graphismes.fill(xSeuil, y, xSeuil + 1, y + HAUTEUR, COULEUR_SEUIL);
	}

	/** Mélange deux couleurs ARGB : t = 0 → a, t = 1 → b. */
	private static int melanger(final int a, final int b, final float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int v = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | (r << 16) | (v << 8) | bl;
	}

	private static int assombrir(final int couleur, final float facteur) {
		return melanger(0xFF000000, couleur, facteur);
	}
}
