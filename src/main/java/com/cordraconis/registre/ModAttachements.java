package com.cordraconis.registre;

import com.cordraconis.CorDraconis;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Données "attachées" aux entités grâce à Fabric API.
 *
 * NOTION — attachement : une valeur qu'on accroche à n'importe quelle entité
 * (ici le joueur) sans modifier la classe Player de Minecraft.
 */
public final class ModAttachements {

	/**
	 * La Braise du joueur, de 0 à 100.
	 *  - persistent : sauvegardée quand on quitte le monde ;
	 *  - syncWith(targetOnly) : envoyée uniquement au joueur concerné, pour son HUD.
	 * Elle n'est PAS copiée à la mort : on renaît la flamme éteinte.
	 */
	public static final AttachmentType<Float> BRAISE = AttachmentRegistry.create(
		CorDraconis.id("braise"),
		constructeur -> constructeur
			.initializer(() -> 0.0F)
			.persistent(Codec.FLOAT)
			.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
	);

	private ModAttachements() {
	}

	/** Force le chargement de la classe (et donc l'enregistrement ci-dessus). */
	public static void initialiser() {
	}
}
