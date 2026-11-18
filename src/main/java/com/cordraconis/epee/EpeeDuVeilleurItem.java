package com.cordraconis.epee;

import com.cordraconis.braise.SystemeBraise;
import com.cordraconis.registre.ModComposants;
import com.cordraconis.rune.Rune;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * L'Épée du Veilleur.
 *
 * Clic gauche : coup d'épée normal (dégâts d'épée en diamant améliorée).
 * Clic droit  : lance le sort "Souffle draconique", modifié par la rune gravée.
 *               Sans rune, l'épée reste muette.
 *
 * NOTION JAVA — héritage ("extends Item") : notre classe reprend tout le comportement
 * d'un Item de Minecraft et ne redéfinit que ce qui change (ici : le clic droit).
 */
public class EpeeDuVeilleurItem extends Item {

	/** Temps de recharge du sort, en ticks (20 ticks = 1 seconde). */
	private static final int RECHARGE_SORT = 25;

	public EpeeDuVeilleurItem(final Item.Properties proprietes) {
		super(proprietes);
	}

	/** Lit la rune gravée sur une épée (null si aucune). */
	public static Rune runeGravee(final ItemStack epee) {
		return epee.get(ModComposants.RUNE);
	}

	// NOTION JAVA — @Override : on remplace une méthode de la classe parente (Item).
	@Override
	public InteractionResult use(final Level niveau, final Player joueur, final InteractionHand main) {
		ItemStack epee = joueur.getItemInHand(main);
		Rune rune = runeGravee(epee);

		if (rune == null) {
			// Le message n'est envoyé que par le serveur, pour ne pas l'afficher deux fois.
			if (!niveau.isClientSide()) {
				joueur.sendOverlayMessage(Component.translatable("message.cordraconis.epee_muette").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}

		// NOTION — client / serveur : ce code tourne des deux côtés. Les vrais effets
		// (dégâts, Braise) ne doivent être calculés QUE sur le serveur, qui fait autorité.
		if (niveau instanceof ServerLevel serveur && joueur instanceof ServerPlayer joueurServeur) {
			SouffleDraconique.lancer(serveur, joueurServeur, rune);
			SystemeBraise.ajouter(joueurServeur, rune.coutBraise());
			epee.hurtAndBreak(1, joueur, main);
		}

		joueur.getCooldowns().addCooldown(epee, RECHARGE_SORT);
		return InteractionResult.SUCCESS;
	}

	/** Ligne d'info-bulle quand aucune rune n'est gravée (sinon c'est la rune qui s'affiche). */
	@Override
	@SuppressWarnings("deprecation") // méthode marquée "dépréciée" par Mojang mais toujours appelée
	public void appendHoverText(
		final ItemStack epee, final Item.TooltipContext contexte, final TooltipDisplay affichage, final Consumer<Component> ligne, final TooltipFlag options
	) {
		if (runeGravee(epee) == null) {
			ligne.accept(Component.translatable("item.cordraconis.epee_du_veilleur.sans_rune").withStyle(ChatFormatting.DARK_GRAY));
		}
		ligne.accept(Component.translatable("item.cordraconis.epee_du_veilleur.aide").withStyle(ChatFormatting.DARK_PURPLE));
	}
}
