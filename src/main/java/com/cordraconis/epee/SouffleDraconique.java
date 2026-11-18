package com.cordraconis.epee;

import com.cordraconis.braise.SystemeBraise;
import com.cordraconis.rune.Rune;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le sort de l'Épée du Veilleur : un cône de feu draconique projeté devant le joueur.
 *
 * La rune gravée ajoute un effet :
 *  - Flamme  : enflamme les cibles et frappe plus fort ;
 *  - Vigueur : soigne le Veilleur pour chaque cible touchée ;
 *  - Givre   : ralentit et affaiblit les cibles ;
 *  - Foudre  : repousse violemment ; au-delà du seuil de Braise, appelle la foudre.
 *
 * La puissance augmente avec l'Embrasement (Braise au-delà du seuil).
 */
public final class SouffleDraconique {

	/** Portée du souffle, en blocs. */
	private static final double PORTEE = 7.0;
	/** Ouverture du cône : cosinus de l'angle maximal (0.75 ≈ 41°). */
	private static final double OUVERTURE_COSINUS = 0.75;
	private static final float DEGATS_BASE = 6.0F;

	private SouffleDraconique() {
	}

	/**
	 * Lance le sort.
	 *
	 * @return le nombre de créatures touchées
	 */
	public static int lancer(final ServerLevel niveau, final ServerPlayer lanceur, final Rune rune) {
		Vec3 origine = lanceur.getEyePosition();
		Vec3 direction = lanceur.getLookAngle();
		float puissance = SystemeBraise.multiplicateurPuissance(lanceur);

		// 1. Trouver les cibles : créatures vivantes, dans le cône, visibles.
		// NOTION JAVA — lambda "cible -> ..." : petite fonction écrite sur place, ici un filtre.
		AABB zone = lanceur.getBoundingBox().inflate(PORTEE);
		List<LivingEntity> cibles = niveau.getEntitiesOfClass(
			LivingEntity.class, zone, cible -> cible != lanceur && cible.isAlive() && estDansLeCone(origine, direction, cible) && lanceur.hasLineOfSight(cible)
		);

		// 2. Infliger les dégâts et l'effet de la rune.
		DamageSource source = niveau.damageSources().indirectMagic(lanceur, lanceur);
		float degats = DEGATS_BASE * puissance * (rune == Rune.FLAMME ? 1.25F : 1.0F);
		boolean foudreAppelee = false;

		for (LivingEntity cible : cibles) {
			cible.hurtServer(niveau, source, degats);
			switch (rune) {
				case FLAMME -> cible.igniteForSeconds(5.0F);
				case VIGUEUR -> lanceur.heal(1.5F);
				case GIVRE -> {
					cible.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), lanceur);
					cible.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), lanceur);
					cible.setTicksFrozen(cible.getTicksRequiredToFreeze() + 60);
				}
				case FOUDRE -> {
					cible.knockback(1.6, -direction.x, -direction.z, source, degats);
					// Au-delà du seuil, la première cible attire un éclair.
					if (!foudreAppelee && SystemeBraise.estEmbrase(lanceur)) {
						appelerFoudre(niveau, lanceur, cible.position());
						foudreAppelee = true;
					}
				}
			}
		}

		// 3. Effets visuels et sonores : des particules tout le long du cône.
		// On commence à 1,5 bloc des yeux pour ne pas aveugler le lanceur.
		for (double distance = 1.5; distance <= PORTEE; distance += 0.5) {
			Vec3 point = origine.add(direction.scale(distance));
			double ecart = distance * 0.22;
			niveau.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3, ecart, ecart, ecart, 0.01);
			niveau.sendParticles(rune.particule(), point.x, point.y, point.z, 2, ecart, ecart, ecart, 0.02);
		}
		niveau.playSound(null, lanceur.getX(), lanceur.getY(), lanceur.getZ(), son(rune), SoundSource.PLAYERS, 1.0F, 0.8F + niveau.getRandom().nextFloat() * 0.3F);

		return cibles.size();
	}

	/** Une cible est dans le cône si l'angle entre le regard et la direction de la cible est petit. */
	private static boolean estDansLeCone(final Vec3 origine, final Vec3 direction, final LivingEntity cible) {
		Vec3 versCible = cible.getBoundingBox().getCenter().subtract(origine);
		double distance = versCible.length();
		if (distance > PORTEE) {
			return false;
		}
		if (distance < 1.0) {
			return true; // collée au joueur : toujours touchée
		}
		// NOTION — produit scalaire ("dot") de deux vecteurs normalisés = cosinus de leur angle.
		return versCible.normalize().dot(direction) >= OUVERTURE_COSINUS;
	}

	private static void appelerFoudre(final ServerLevel niveau, final ServerPlayer lanceur, final Vec3 position) {
		LightningBolt eclair = EntityTypes.LIGHTNING_BOLT.create(niveau, EntitySpawnReason.TRIGGERED);
		if (eclair != null) {
			eclair.snapTo(position);
			eclair.setCause(lanceur);
			niveau.addFreshEntity(eclair);
		}
	}

	private static SoundEvent son(final Rune rune) {
		// NOTION JAVA — "switch" en expression : renvoie directement une valeur selon le cas.
		return switch (rune) {
			case FLAMME -> SoundEvents.BLAZE_SHOOT;
			case VIGUEUR -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case GIVRE -> SoundEvents.GLASS_BREAK;
			case FOUDRE -> SoundEvents.FIRECHARGE_USE;
		};
	}
}
