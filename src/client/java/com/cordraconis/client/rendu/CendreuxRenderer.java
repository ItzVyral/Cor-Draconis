package com.cordraconis.client.rendu;

import com.cordraconis.CorDraconis;
import com.cordraconis.entite.cendreux.CendreuxEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Affiche le Cendreux avec GeckoLib.
 *
 * Fichiers utilisés (déduits automatiquement de l'identifiant "cordraconis:cendreux") :
 *  - assets/cordraconis/geckolib/models/entity/cendreux.geo.json         (modèle Blockbench)
 *  - assets/cordraconis/geckolib/animations/entity/cendreux.animation.json (animations)
 *  - assets/cordraconis/textures/entity/cendreux.png                      (texture)
 *  - assets/cordraconis/textures/entity/cendreux_glowmask.png             (braises lumineuses)
 */
public class CendreuxRenderer extends GeoEntityRenderer<CendreuxEntity, LivingEntityRenderState> {

	public CendreuxRenderer(final EntityRendererProvider.Context contexte) {
		super(contexte, new DefaultedEntityGeoModel<>(CorDraconis.id("cendreux")));
		// Couche "glowmask" : les pixels de cendreux_glowmask.png brillent même dans le noir.
		this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
		this.shadowRadius = 0.5F;
	}

	/** Pas de chute sur le côté à la mort : le Cendreux s'effondre déjà en tas de cendre. */
	@Override
	protected float getDeathMaxRotation(final GeoRenderState etat) {
		return 0.0F;
	}
}
