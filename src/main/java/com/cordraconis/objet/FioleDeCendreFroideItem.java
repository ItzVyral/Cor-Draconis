package com.cordraconis.objet;

import com.cordraconis.braise.SystemeBraise;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Fiole de cendre froide : se boit d'un trait pour éteindre une partie de la Braise.
 * Rend une fiole vide.
 */
public class FioleDeCendreFroideItem extends Item {

	/** Braise retirée par fiole. */
	private static final float REFROIDISSEMENT = 50.0F;

	public FioleDeCendreFroideItem(final Item.Properties proprietes) {
		super(proprietes);
	}

	@Override
	public InteractionResult use(final Level niveau, final Player joueur, final InteractionHand main) {
		if (SystemeBraise.obtenir(joueur) <= 0.0F) {
			return InteractionResult.PASS; // rien à refroidir
		}

		if (niveau instanceof ServerLevel serveur && joueur instanceof ServerPlayer joueurServeur) {
			SystemeBraise.retirer(joueurServeur, REFROIDISSEMENT);
			joueurServeur.clearFire();
			serveur.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.2F);
			serveur.sendParticles(ParticleTypes.WHITE_ASH, joueur.getX(), joueur.getY() + 1.0, joueur.getZ(), 25, 0.4, 0.5, 0.4, 0.02);

			// On consomme la fiole et on rend une fiole vide (ItemUtils gère le mode créatif
			// et l'inventaire plein, comme pour un seau d'eau).
			ItemStack fiole = joueur.getItemInHand(main);
			joueur.setItemInHand(main, ItemUtils.createFilledResult(fiole, joueur, new ItemStack(Items.GLASS_BOTTLE)));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(
		final ItemStack objet, final Item.TooltipContext contexte, final TooltipDisplay affichage, final Consumer<Component> ligne, final TooltipFlag options
	) {
		ligne.accept(Component.translatable("item.cordraconis.fiole_de_cendre_froide.effet", (int) REFROIDISSEMENT).withStyle(ChatFormatting.AQUA));
	}
}
