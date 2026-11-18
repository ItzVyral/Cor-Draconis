package com.cordraconis.objet;

import com.cordraconis.entite.gardien.ArenePremierGardien;
import com.cordraconis.entite.gardien.PremierGardienEntity;
import com.cordraconis.registre.ModEntites;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;

/**
 * Sceau du Gardien : utilisé sur le sol, il élève l'arène du Premier Gardien
 * autour du point visé, puis réveille le boss en son centre.
 */
public class SceauDuGardienItem extends Item {

	public SceauDuGardienItem(final Item.Properties proprietes) {
		super(proprietes);
	}

	@Override
	public InteractionResult useOn(final UseOnContext contexte) {
		if (!(contexte.getLevel() instanceof ServerLevel niveau)) {
			return InteractionResult.SUCCESS; // côté client : on laisse le serveur décider
		}
		Player joueur = contexte.getPlayer();
		BlockPos centre = contexte.getClickedPos().above();

		// Un seul Gardien éveillé à la fois dans les environs.
		AABB environs = new AABB(centre).inflate(64.0);
		if (!niveau.getEntitiesOfClass(PremierGardienEntity.class, environs).isEmpty()) {
			if (joueur != null) {
				joueur.sendOverlayMessage(Component.translatable("message.cordraconis.gardien_deja_eveille").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}

		ArenePremierGardien.construire(niveau, centre);

		PremierGardienEntity gardien = ModEntites.PREMIER_GARDIEN.create(niveau, EntitySpawnReason.TRIGGERED);
		if (gardien == null) {
			return InteractionResult.FAIL;
		}
		gardien.snapTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		gardien.lierAArene(centre);
		niveau.addFreshEntity(gardien);

		niveau.playSound(null, centre, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (ServerPlayer proche : niveau.getEntitiesOfClass(ServerPlayer.class, new AABB(centre).inflate(32.0))) {
			proche.sendSystemMessage(Component.translatable("message.cordraconis.gardien_eveil").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}

		contexte.getItemInHand().consume(1, joueur);
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(
		final ItemStack objet, final Item.TooltipContext contexte, final TooltipDisplay affichage, final Consumer<Component> ligne, final TooltipFlag options
	) {
		ligne.accept(Component.translatable("item.cordraconis.sceau_du_gardien.aide").withStyle(ChatFormatting.GRAY));
		ligne.accept(Component.translatable("item.cordraconis.sceau_du_gardien.avertissement").withStyle(ChatFormatting.DARK_RED));
	}
}
