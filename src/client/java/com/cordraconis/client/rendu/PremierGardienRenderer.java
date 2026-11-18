package com.cordraconis.client.rendu;

import com.cordraconis.CorDraconis;
import com.cordraconis.entite.gardien.PremierGardienEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Affiche le Premier Gardien avec GeckoLib (modèle, animations, armure fissurée lumineuse).
 */
public class PremierGardienRenderer extends GeoEntityRenderer<PremierGardienEntity, LivingEntityRenderState> {

	public PremierGardienRenderer(final EntityRendererProvider.Context contexte) {
		super(contexte, new DefaultedEntityGeoModel<>(CorDraconis.id("premier_gardien")));
		this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
		this.shadowRadius = 1.1F;
	}

	/** La mort est gérée par l'animation "mort" (il tombe à genoux), pas par la rotation par défaut. */
	@Override
	protected float getDeathMaxRotation(final GeoRenderState etat) {
		return 0.0F;
	}
}
