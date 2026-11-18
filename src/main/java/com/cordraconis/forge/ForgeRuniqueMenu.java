package com.cordraconis.forge;

import com.cordraconis.epee.EpeeDuVeilleurItem;
import com.cordraconis.registre.ModBlocs;
import com.cordraconis.registre.ModComposants;
import com.cordraconis.registre.ModMenus;
import com.cordraconis.rune.Rune;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Logique de la Forge runique.
 *
 * Trois emplacements, comme une table de forgeron :
 *   [Épée] + [Pierre runique]  →  [Épée gravée]
 *
 * Graver coûte des niveaux d'expérience et consomme la pierre. Une rune déjà gravée
 * est remplacée (l'ancienne est perdue : l'épée n'a qu'un seul emplacement).
 *
 * NOTION — on hérite de ItemCombinerMenu (la classe de Minecraft derrière l'enclume et la
 * table de forgeron) : elle gère déjà les emplacements, l'inventaire du joueur et le
 * rendu des objets au joueur quand il ferme l'interface.
 */
public class ForgeRuniqueMenu extends ItemCombinerMenu {

	public static final int EMPLACEMENT_EPEE = 0;
	public static final int EMPLACEMENT_RUNE = 1;
	public static final int EMPLACEMENT_RESULTAT = 2;

	/** Coût d'une gravure, en niveaux d'expérience. */
	public static final int COUT_NIVEAUX = 3;

	/** Constructeur utilisé côté client (le client ne connaît pas la position du bloc). */
	public ForgeRuniqueMenu(final int idConteneur, final Inventory inventaire) {
		this(idConteneur, inventaire, ContainerLevelAccess.NULL);
	}

	public ForgeRuniqueMenu(final int idConteneur, final Inventory inventaire, final ContainerLevelAccess acces) {
		super(ModMenus.FORGE_RUNIQUE, idConteneur, inventaire, acces, definirEmplacements());
	}

	/** Position (en pixels, dans l'interface) et filtre de chaque emplacement. */
	private static ItemCombinerMenuSlotDefinition definirEmplacements() {
		return ItemCombinerMenuSlotDefinition.create()
			.withSlot(EMPLACEMENT_EPEE, 27, 47, objet -> objet.getItem() instanceof EpeeDuVeilleurItem)
			.withSlot(EMPLACEMENT_RUNE, 76, 47, ForgeRuniqueMenu::estPierreRunique)
			.withResultSlot(EMPLACEMENT_RESULTAT, 134, 47)
			.build();
	}

	/** Une pierre runique : porte une rune, mais n'est pas l'épée elle-même. */
	private static boolean estPierreRunique(final ItemStack objet) {
		return objet.has(ModComposants.RUNE) && !(objet.getItem() instanceof EpeeDuVeilleurItem);
	}

	@Override
	protected boolean isValidBlock(final BlockState etat) {
		return etat.is(ModBlocs.FORGE_RUNIQUE);
	}

	/** Calcule l'objet du résultat à chaque changement dans les emplacements d'entrée. */
	@Override
	public void createResult() {
		ItemStack epee = this.inputSlots.getItem(EMPLACEMENT_EPEE);
		ItemStack pierre = this.inputSlots.getItem(EMPLACEMENT_RUNE);
		Rune nouvelleRune = pierre.get(ModComposants.RUNE);

		boolean gravurePossible = !epee.isEmpty() && nouvelleRune != null && epee.get(ModComposants.RUNE) != nouvelleRune;
		if (gravurePossible) {
			ItemStack resultat = epee.copyWithCount(1);
			resultat.set(ModComposants.RUNE, nouvelleRune);
			this.resultSlots.setItem(0, resultat);
		} else {
			this.resultSlots.setItem(0, ItemStack.EMPTY);
		}
	}

	/** Le joueur ne peut récupérer l'épée gravée que s'il a assez de niveaux (ou en créatif). */
	@Override
	protected boolean mayPickup(final Player joueur, final boolean contientObjet) {
		return contientObjet && (joueur.hasInfiniteMaterials() || joueur.experienceLevel >= COUT_NIVEAUX);
	}

	/** Appelé quand le joueur prend l'épée gravée : on paie et on consomme les entrées. */
	@Override
	protected void onTake(final Player joueur, final ItemStack epeeGravee) {
		if (!joueur.hasInfiniteMaterials()) {
			joueur.giveExperienceLevels(-COUT_NIVEAUX);
		}
		this.inputSlots.removeItem(EMPLACEMENT_EPEE, 1);
		this.inputSlots.removeItem(EMPLACEMENT_RUNE, 1);

		// Son d'enclume + gerbe d'étincelles au-dessus de la forge.
		this.access.execute((niveau, position) -> {
			niveau.playSound(null, position, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
			niveau.playSound(null, position, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			if (niveau instanceof ServerLevel serveur) {
				serveur.sendParticles(ParticleTypes.LAVA, position.getX() + 0.5, position.getY() + 1.1, position.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.0);
				serveur.sendParticles(ParticleTypes.ENCHANT, position.getX() + 0.5, position.getY() + 1.5, position.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.5);
			}
		});
	}

	/** Shift-clic depuis l'inventaire : l'objet va-t-il dans un emplacement d'entrée ? */
	@Override
	protected boolean canMoveIntoInputSlots(final ItemStack objet) {
		return objet.getItem() instanceof EpeeDuVeilleurItem || estPierreRunique(objet);
	}
}
