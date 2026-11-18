package com.cordraconis.entite.cendreux;

import com.cordraconis.epee.EpeeDuVeilleurItem;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le Cendreux : un être pétrifié par la Cendre, animé par une braise en guise de cœur.
 *
 * MÉCANIQUE PRINCIPALE — "se reforme tant que son cœur n'est pas détruit" :
 *  1. Quand ses points de vie tombent à zéro, il ne meurt PAS : il s'effondre en tas de cendre.
 *  2. Pendant l'effondrement, son cœur est exposé. Chaque coup frappe le cœur.
 *     Le feu et l'Épée du Veilleur l'endommagent deux fois plus.
 *  3. Si le cœur est détruit → mort définitive (et butin).
 *     Sinon, au bout de 5 secondes, le Cendreux se reforme avec toute sa vie.
 *
 * NOTION JAVA — "implements GeoEntity" : un "interface" est un contrat. En l'implémentant,
 * on promet à GeckoLib de fournir les méthodes qu'il attend pour animer le modèle.
 */
public class CendreuxEntity extends Monster implements GeoEntity {

	/** Durée de l'effondrement avant reformation, en ticks (100 = 5 secondes). */
	private static final int DUREE_EFFONDREMENT = 100;
	/** Points de vie du cœur exposé. */
	private static final float PV_COEUR_MAX = 8.0F;

	// NOTION — donnée synchronisée : valeur automatiquement copiée du serveur vers les clients.
	// Le client en a besoin pour jouer la bonne animation.
	private static final EntityDataAccessor<Boolean> EFFONDRE = SynchedEntityData.defineId(CendreuxEntity.class, EntityDataSerializers.BOOLEAN);

	// Animations (les noms correspondent au fichier cendreux.animation.json)
	private static final RawAnimation REPOS = RawAnimation.begin().thenLoop("animation.cendreux.repos");
	private static final RawAnimation MARCHE = RawAnimation.begin().thenLoop("animation.cendreux.marche");
	private static final RawAnimation ATTAQUE = RawAnimation.begin().thenPlay("animation.cendreux.attaque");
	private static final RawAnimation EFFONDREMENT = RawAnimation.begin().thenPlayAndHold("animation.cendreux.effondrement");
	private static final RawAnimation REFORMATION = RawAnimation.begin().thenPlay("animation.cendreux.reformation");

	private final AnimatableInstanceCache cacheAnimations = GeckoLibUtil.createInstanceCache(this);

	private int tempsEffondre;
	private float pvCoeur = PV_COEUR_MAX;

	public CendreuxEntity(final EntityType<? extends CendreuxEntity> type, final Level niveau) {
		super(type, niveau);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder creerAttributs() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 22.0)
			.add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.ARMOR, 3.0)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		// NOTION — "goals" (objectifs) : l'IA choisit à chaque instant l'objectif de plus haute
		// priorité (le plus petit nombre) qui peut s'exécuter.
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder donnees) {
		super.defineSynchedData(donnees);
		donnees.define(EFFONDRE, false);
	}

	public boolean estEffondre() {
		return this.entityData.get(EFFONDRE);
	}

	// --- Effondrement et reformation -------------------------------------------------

	/**
	 * Appelé quand le Cendreux subit des dégâts (après l'armure).
	 * C'est ici qu'on intercepte la mort pour le faire s'effondrer à la place.
	 */
	@Override
	protected void actuallyHurt(final ServerLevel niveau, final DamageSource source, final float degats) {
		// Les dégâts "absolus" (/kill, vide) tuent toujours.
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			super.actuallyHurt(niveau, source, degats);
			return;
		}

		if (this.estEffondre()) {
			frapperLeCoeur(niveau, source, degats);
			return;
		}

		super.actuallyHurt(niveau, source, degats);
		if (this.getHealth() <= 0.0F) {
			this.setHealth(1.0F); // on annule la mort...
			this.seffondrer(niveau); // ... et on s'effondre
		}
	}

	private void seffondrer(final ServerLevel niveau) {
		this.entityData.set(EFFONDRE, true);
		this.tempsEffondre = 0;
		this.pvCoeur = PV_COEUR_MAX;
		this.setTarget(null);
		this.getNavigation().stop();
		niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
		niveau.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.4, 0.8, 0.4, 0.02);
	}

	private void frapperLeCoeur(final ServerLevel niveau, final DamageSource source, final float degats) {
		float multiplicateur = (source.is(DamageTypeTags.IS_FIRE) || vientDuVeilleur(source)) ? 2.0F : 1.0F;
		this.pvCoeur -= degats * multiplicateur;
		niveau.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 0.3, this.getZ(), 4, 0.2, 0.1, 0.2, 0.0);

		if (this.pvCoeur <= 0.0F) {
			// Le cœur est brisé : vraie mort. LivingEntity.hurtServer appellera die() juste après.
			this.setHealth(0.0F);
			niveau.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.4, this.getZ(), 30, 0.3, 0.3, 0.3, 0.08);
		}
	}

	/** Les coups portés avec l'Épée du Veilleur (ou son sort) blessent davantage le cœur. */
	private static boolean vientDuVeilleur(final DamageSource source) {
		return source.getEntity() instanceof Player joueur && joueur.getMainHandItem().getItem() instanceof EpeeDuVeilleurItem;
	}

	private void sereformer(final ServerLevel niveau) {
		this.entityData.set(EFFONDRE, false);
		this.setHealth(this.getMaxHealth());
		this.triggerAnim("actions", "reformation");
		niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.5F);
		niveau.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.3, 0.9, 0.3, 0.02);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel niveau && this.estEffondre()) {
			this.tempsEffondre++;
			// Le cœur exposé rougeoie dans le tas de cendre.
			if (this.tempsEffondre % 5 == 0) {
				niveau.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.4, this.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
			}
			if (this.tempsEffondre >= DUREE_EFFONDREMENT) {
				this.sereformer(niveau);
			}
		}
	}

	/** Effondré, il ne peut ni bouger ni attaquer. */
	@Override
	public boolean isImmobile() {
		return super.isImmobile() || this.estEffondre();
	}

	@Override
	public boolean doHurtTarget(final ServerLevel niveau, final Entity cible) {
		if (this.estEffondre()) {
			return false;
		}
		this.triggerAnim("actions", "attaque");
		return super.doHurtTarget(niveau, cible);
	}

	// --- Sauvegarde -------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(final ValueOutput sortie) {
		super.addAdditionalSaveData(sortie);
		sortie.putBoolean("Effondre", this.estEffondre());
		sortie.putInt("TempsEffondre", this.tempsEffondre);
		sortie.putFloat("PvCoeur", this.pvCoeur);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput entree) {
		super.readAdditionalSaveData(entree);
		this.entityData.set(EFFONDRE, entree.getBooleanOr("Effondre", false));
		this.tempsEffondre = entree.getIntOr("TempsEffondre", 0);
		this.pvCoeur = entree.getFloatOr("PvCoeur", PV_COEUR_MAX);
	}

	// --- Sons -------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return this.estEffondre() ? null : SoundEvents.HUSK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.SAND_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos position, final BlockState bloc) {
		this.playSound(SoundEvents.HUSK_STEP, 0.15F, 0.8F);
	}

	// --- GeckoLib ---------------------------------------------------------------------

	/**
	 * Déclare les "contrôleurs" d'animation.
	 *  - "corps"   : choisit en continu entre repos, marche et effondrement ;
	 *  - "actions" : joue à la demande (triggerAnim) l'attaque et la reformation.
	 */
	@Override
	public void registerControllers(final AnimatableManager.ControllerRegistrar controleurs) {
		controleurs.add(new AnimationController<CendreuxEntity>("corps", 4, test -> {
			if (test.animatable().estEffondre()) {
				return test.setAndContinue(EFFONDREMENT);
			}
			return test.setAndContinue(test.isMoving() ? MARCHE : REPOS);
		}));
		controleurs.add(new AnimationController<CendreuxEntity>("actions", 0, test -> PlayState.STOP)
			.triggerableAnim("attaque", ATTAQUE)
			.triggerableAnim("reformation", REFORMATION));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cacheAnimations;
	}
}
