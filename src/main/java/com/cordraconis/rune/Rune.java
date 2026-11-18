package com.cordraconis.rune;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Les runes que le Veilleur peut graver sur son épée.
 *
 * NOTION JAVA — enum : une "énumération" est une liste FERMÉE de valeurs possibles.
 * Chaque rune est un objet unique (Rune.FLAMME, Rune.GIVRE...) qui porte ses propres
 * données (couleur, coût en Braise, particule). On ne peut pas en créer d'autres
 * ailleurs dans le code : c'est parfait pour un ensemble connu à l'avance.
 *
 * Cette enum sert aussi de "composant de données" : une pierre runique ou une épée
 * gravée stockent simplement l'une de ces valeurs (voir ModComposants.RUNE).
 */
public enum Rune implements StringRepresentable, TooltipProvider {
	// id réseau, nom de sauvegarde, couleur, coût en Braise, particule du sort
	FLAMME(0, "flamme", ChatFormatting.GOLD, 18.0F, ParticleTypes.FLAME),
	VIGUEUR(1, "vigueur", ChatFormatting.GREEN, 22.0F, ParticleTypes.HAPPY_VILLAGER),
	GIVRE(2, "givre", ChatFormatting.AQUA, 15.0F, ParticleTypes.SNOWFLAKE),
	FOUDRE(3, "foudre", ChatFormatting.YELLOW, 20.0F, ParticleTypes.ELECTRIC_SPARK);

	/** Sert à sauvegarder la rune sur le disque (sous forme de texte : "flamme"). */
	public static final Codec<Rune> CODEC = StringRepresentable.fromValues(Rune::values);

	/** Sert à envoyer la rune sur le réseau (sous forme de petit nombre : 0, 1, 2...). */
	private static final IntFunction<Rune> PAR_ID = ByIdMap.continuous(r -> r.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
	public static final StreamCodec<ByteBuf, Rune> STREAM_CODEC = ByteBufCodecs.idMapper(PAR_ID, r -> r.id);

	private final int id;
	private final String nom;
	private final ChatFormatting couleur;
	private final float coutBraise;
	private final SimpleParticleType particule;

	// NOTION JAVA — constructeur d'enum : appelé une fois pour chaque valeur listée plus haut.
	Rune(final int id, final String nom, final ChatFormatting couleur, final float coutBraise, final SimpleParticleType particule) {
		this.id = id;
		this.nom = nom;
		this.couleur = couleur;
		this.coutBraise = coutBraise;
		this.particule = particule;
	}

	public ChatFormatting couleur() {
		return this.couleur;
	}

	/** Quantité de Braise ajoutée à chaque lancer de sort avec cette rune. */
	public float coutBraise() {
		return this.coutBraise;
	}

	public SimpleParticleType particule() {
		return this.particule;
	}

	/** Nom traduit de la rune ("Flamme", "Givre"...). */
	public Component nomTraduit() {
		return Component.translatable("rune.cordraconis." + this.nom);
	}

	@Override
	public String getSerializedName() {
		return this.nom;
	}

	/**
	 * Lignes ajoutées automatiquement à l'info-bulle de tout objet portant cette rune.
	 * (Enregistré dans ModComposants grâce à Fabric API.)
	 */
	@Override
	public void addToTooltip(final Item.TooltipContext context, final Consumer<Component> ligne, final TooltipFlag flag, final DataComponentGetter composants) {
		ligne.accept(Component.translatable("rune.cordraconis.titre", this.nomTraduit()).withStyle(this.couleur));
		ligne.accept(Component.translatable("rune.cordraconis." + this.nom + ".effet").withStyle(ChatFormatting.GRAY));
		ligne.accept(Component.translatable("rune.cordraconis.cout", (int) this.coutBraise).withStyle(ChatFormatting.DARK_RED));
	}
}
