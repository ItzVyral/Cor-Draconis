package com.cordraconis.client.ecran;

import com.cordraconis.CorDraconis;
import com.cordraconis.forge.ForgeRuniqueMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * L'écran (côté client) de la Forge runique.
 *
 * Il dessine la texture de fond (textures/gui/container/forge_runique.png),
 * le coût en niveaux, et une croix rouge si la gravure est impossible.
 */
public class ForgeRuniqueScreen extends ItemCombinerScreen<ForgeRuniqueMenu> {

	private static final Identifier TEXTURE = CorDraconis.id("textures/gui/container/forge_runique.png");
	private static final Identifier SPRITE_ERREUR = Identifier.withDefaultNamespace("container/smithing/error");

	// Couleurs au format ARGB (0xAARRGGBB) : AA = opacité.
	private static final int COULEUR_TITRE = 0xFF3A2416;
	private static final int COULEUR_COUT_OK = 0xFF80FF20;
	private static final int COULEUR_COUT_KO = 0xFFFF6060;

	public ForgeRuniqueScreen(final ForgeRuniqueMenu menu, final Inventory inventaire, final Component titre) {
		super(menu, inventaire, titre, TEXTURE);
		this.titleLabelX = 60;
		this.titleLabelY = 18;
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor graphismes, final int sourisX, final int sourisY) {
		graphismes.text(this.font, this.title, this.titleLabelX, this.titleLabelY, COULEUR_TITRE, false);
		graphismes.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, COULEUR_TITRE, false);

		// Le coût n'apparaît que lorsqu'une gravure est proposée.
		Player joueur = this.minecraft.player;
		if (joueur != null && this.menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_RESULTAT).hasItem() && !joueur.hasInfiniteMaterials()) {
			boolean assezDeNiveaux = joueur.experienceLevel >= ForgeRuniqueMenu.COUT_NIVEAUX;
			Component cout = Component.translatable("container.cordraconis.forge_runique.cout", ForgeRuniqueMenu.COUT_NIVEAUX);
			int x = this.imageWidth - 8 - this.font.width(cout);
			graphismes.text(this.font, cout, x, 69, assezDeNiveaux ? COULEUR_COUT_OK : COULEUR_COUT_KO, true);
		}
	}

	/** Croix rouge sur la flèche : épée et rune présentes mais aucun résultat (rune déjà gravée). */
	@Override
	protected void extractErrorIcon(final GuiGraphicsExtractor graphismes, final int origineX, final int origineY) {
		boolean deuxEntrees = this.menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_EPEE).hasItem() && this.menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_RUNE).hasItem();
		if (deuxEntrees && !this.menu.getSlot(ForgeRuniqueMenu.EMPLACEMENT_RESULTAT).hasItem()) {
			graphismes.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_ERREUR, origineX + 99, origineY + 45, 28, 21);
		}
	}
}
