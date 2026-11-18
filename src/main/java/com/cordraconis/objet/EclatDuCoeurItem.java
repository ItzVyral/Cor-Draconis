package com.cordraconis.objet;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Un Éclat du cœur de Vaelthor (il y en a 7).
 *
 * Effet passif : tant qu'un Éclat est dans l'inventaire, la Braise redescend deux fois
 * plus vite (voir SystemeBraise). Chaque Éclat garde son numéro pour les futurs Gardiens.
 */
public class EclatDuCoeurItem extends Item {

	/** Nombre total d'Éclats à réunir. */
	public static final int NOMBRE_ECLATS = 7;

	private final int numero;

	public EclatDuCoeurItem(final Item.Properties proprietes, final int numero) {
		super(proprietes);
		this.numero = numero;
	}

	public int numero() {
		return this.numero;
	}

	/** L'Éclat brille toujours, comme un objet enchanté. */
	@Override
	public boolean isFoil(final ItemStack objet) {
		return true;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(
		final ItemStack objet, final Item.TooltipContext contexte, final TooltipDisplay affichage, final Consumer<Component> ligne, final TooltipFlag options
	) {
		ligne.accept(Component.translatable("item.cordraconis.eclat.numero", this.numero, NOMBRE_ECLATS).withStyle(ChatFormatting.DARK_RED));
		ligne.accept(Component.translatable("item.cordraconis.eclat.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		ligne.accept(Component.translatable("item.cordraconis.eclat.effet").withStyle(ChatFormatting.BLUE));
	}
}
