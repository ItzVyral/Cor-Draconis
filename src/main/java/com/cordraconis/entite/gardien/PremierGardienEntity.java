package com.cordraconis.entite.gardien;

import com.cordraconis.CorDraconis;
import com.cordraconis.entite.cendreux.CendreuxEntity;
import com.cordraconis.registre.ModEntites;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DataTickets;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Le Premier Gardien : Ser Aldric le Parjure, premier des chevaliers qui brisèrent le cœur
 * de Vaelthor. La Cendre l'a figé autour de l'Éclat qu'il avait volé.
 *
 * PHASE 1 (100 % → 50 % de vie) :
 *  - coups d'épée au corps à corps ;
 *  - "Fracas" : il abat son arme sur le sol, onde de choc autour de lui.
 * PHASE 2 (sous 50 %) :
 *  - il hurle, devient plus rapide et invoque des Cendreux ;
 *  - Fracas plus fréquents, nouvelles invocations régulières.
 *
 * Il est lié à son arène : s'il s'en éloigne, la Cendre le ramène au centre.
 * À sa mort, il lâche le Premier Éclat (voir loot_table/entities/premier_gardien.json).
 */
public class PremierGardienEntity extends Monster implements GeoEntity {

	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(PremierGardienEntity.class, EntityDataSerializers.INT);

	private static final RawAnimation REPOS = RawAnimation.begin().thenLoop("animation.premier_gardien.repos");
	private static final RawAnimation MARCHE = RawAnimation.begin().thenLoop("animation.premier_gardien.marche");
	private static final RawAnimation MORT = RawAnimation.begin().thenPlayAndHold("animation.premier_gardien.mort");
	private static final RawAnimation FRAPPE = RawAnimation.begin().thenPlay("animation.premier_gardien.frappe");
	private static final RawAnimation FRACAS = RawAnimation.begin().thenPlay("animation.premier_gardien.fracas");
	private static final RawAnimation INVOCATION = RawAnimation.begin().thenPlay("animation.premier_gardien.invocation");

	private static final Identifier MODIF_RAGE = CorDraconis.id("rage_du_gardien");
	private static final double RAYON_FRACAS = 5.0;
	private static final int MAX_CENDREUX_INVOQUES = 4;

	private final AnimatableInstanceCache cacheAnimations = GeckoLibUtil.createInstanceCache(this);

	/** La barre de vie de boss affichée en haut de l'écran. */
	private final ServerBossEvent barreDeVie = new ServerBossEvent(
		Mth.createInsecureUUID(this.random),
		this.getDisplayName(),
		BossEvent.BossBarColor.RED,
		BossEvent.BossBarOverlay.NOTCHED_10
	);

	// NOTION JAVA — @Nullable : cette variable peut valoir "null" (aucune arène liée,
	// par exemple si le Gardien vient d'un œuf d'apparition).
	private @Nullable BlockPos centreArene;
	private int delaiFracas = 100;
	private int delaiInvocation = 400;

	public PremierGardienEntity(final EntityType<? extends PremierGardienEntity> type, final Level niveau) {
		super(type, niveau);
		this.xpReward = 150;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder creerAttributs() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 300.0)
			.add(Attributes.ATTACK_DAMAGE, 11.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.2)
			.add(Attributes.MOVEMENT_SPEED, 0.26)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder donnees) {
		super.defineSynchedData(donnees);
		donnees.define(PHASE, 1);
	}

	public int phase() {
		return this.entityData.get(PHASE);
	}

	/** Lie le Gardien au centre de son arène (appelé par le Sceau du Gardien). */
	public void lierAArene(final BlockPos centre) {
		this.centreArene = centre;
	}

	// --- Intelligence du combat (côté serveur, chaque tick) --------------------------

	@Override
	protected void customServerAiStep(final ServerLevel niveau) {
		super.customServerAiStep(niveau);
		this.barreDeVie.setProgress(this.getHealth() / this.getMaxHealth());

		// Passage en phase 2 à 50 % de vie.
		if (this.phase() == 1 && this.getHealth() <= this.getMaxHealth() * 0.5F) {
			this.entrerEnPhase2(niveau);
		}

		this.resterDansLArene(niveau);

		LivingEntity cible = this.getTarget();
		if (cible == null) {
			return;
		}

		// Fracas : seulement si la cible est assez proche.
		if (--this.delaiFracas <= 0 && this.distanceTo(cible) <= RAYON_FRACAS + 1.0) {
			this.fracas(niveau);
			this.delaiFracas = this.phase() == 2 ? 70 : 120;
		}

		// Invocations régulières en phase 2.
		if (this.phase() == 2 && --this.delaiInvocation <= 0) {
			this.invoquerCendreux(niveau, 2);
			this.delaiInvocation = 400;
		}
	}

	private void entrerEnPhase2(final ServerLevel niveau) {
		this.entityData.set(PHASE, 2);
		this.barreDeVie.setColor(BossEvent.BossBarColor.YELLOW);
		this.triggerAnim("actions", "invocation");
		niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
		niveau.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 2.0, this.getZ(), 80, 1.0, 1.5, 1.0, 0.1);

		// La rage : +25 % de vitesse.
		AttributeInstance vitesse = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (vitesse != null) {
			vitesse.addOrUpdateTransientModifier(new AttributeModifier(MODIF_RAGE, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}

		for (ServerPlayer joueur : this.barreDeVie.getPlayers()) {
			joueur.sendSystemMessage(Component.translatable("message.cordraconis.gardien_phase2").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
		this.invoquerCendreux(niveau, 3);
	}

	/** Attaque de zone : dégâts et projection pour tous les joueurs proches. */
	private void fracas(final ServerLevel niveau) {
		this.triggerAnim("actions", "fracas");
		niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0F, 0.6F);

		// Un anneau de particules qui s'élargit sur le sol.
		for (int i = 0; i < 36; i++) {
			double angle = Math.toRadians(i * 10.0);
			double x = this.getX() + Math.cos(angle) * RAYON_FRACAS;
			double z = this.getZ() + Math.sin(angle) * RAYON_FRACAS;
			niveau.sendParticles(ParticleTypes.LAVA, x, this.getY() + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
			niveau.sendParticles(ParticleTypes.LARGE_SMOKE, x, this.getY() + 0.2, z, 1, 0.1, 0.1, 0.1, 0.02);
		}
		niveau.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 3, 1.0, 0.2, 1.0, 0.0);

		DamageSource source = niveau.damageSources().mobAttack(this);
		AABB zone = this.getBoundingBox().inflate(RAYON_FRACAS, 1.0, RAYON_FRACAS);
		for (Player joueur : niveau.getEntitiesOfClass(Player.class, zone, j -> j.isAlive() && !j.isSpectator())) {
			joueur.hurtServer(niveau, source, this.phase() == 2 ? 12.0F : 9.0F);
			double dx = joueur.getX() - this.getX();
			double dz = joueur.getZ() - this.getZ();
			double longueur = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			// hurtServer a déjà marqué le joueur "blessé" : sa nouvelle vitesse sera envoyée au client.
			joueur.push(dx / longueur * 1.4, 0.6, dz / longueur * 1.4);
		}
	}

	private void invoquerCendreux(final ServerLevel niveau, final int nombre) {
		AABB environs = this.getBoundingBox().inflate(ArenePremierGardien.RAYON);
		int dejaPresents = niveau.getEntitiesOfClass(CendreuxEntity.class, environs).size();
		int aInvoquer = Math.min(nombre, MAX_CENDREUX_INVOQUES - dejaPresents);

		for (int i = 0; i < aInvoquer; i++) {
			CendreuxEntity cendreux = ModEntites.CENDREUX.create(niveau, EntitySpawnReason.MOB_SUMMONED);
			if (cendreux == null) {
				continue;
			}
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double x = this.getX() + Math.cos(angle) * 4.0;
			double z = this.getZ() + Math.sin(angle) * 4.0;
			cendreux.snapTo(x, this.getY(), z);
			cendreux.setTarget(this.getTarget());
			niveau.addFreshEntity(cendreux);
			niveau.sendParticles(ParticleTypes.ASH, x, this.getY() + 1.0, z, 40, 0.3, 0.8, 0.3, 0.02);
		}
		if (aInvoquer > 0) {
			this.triggerAnim("actions", "invocation");
			niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.6F);
		}
	}

	/** Si le Gardien quitte son arène, la Cendre le ramène au centre. */
	private void resterDansLArene(final ServerLevel niveau) {
		if (this.centreArene == null) {
			return;
		}
		double limite = ArenePremierGardien.RAYON + 2.0;
		if (this.blockPosition().distSqr(this.centreArene) > limite * limite) {
			niveau.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.5, this.getZ(), 50, 0.6, 1.2, 0.6, 0.02);
			this.teleportTo(this.centreArene.getX() + 0.5, this.centreArene.getY(), this.centreArene.getZ() + 0.5);
			niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
		}
	}

	@Override
	public boolean doHurtTarget(final ServerLevel niveau, final Entity cible) {
		this.triggerAnim("actions", "frappe");
		return super.doHurtTarget(niveau, cible);
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel niveau) {
			niveau.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.5, this.getZ(), 120, 0.8, 1.5, 0.8, 0.05);
			niveau.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 1.4F);
			for (ServerPlayer joueur : this.barreDeVie.getPlayers()) {
				joueur.sendSystemMessage(Component.translatable("message.cordraconis.gardien_vaincu").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			}
		}
	}

	// --- Barre de boss ----------------------------------------------------------------

	/** Un joueur commence à "voir" le Gardien (il est assez proche) : on lui montre la barre. */
	@Override
	public void startSeenByPlayer(final ServerPlayer joueur) {
		super.startSeenByPlayer(joueur);
		this.barreDeVie.addPlayer(joueur);
	}

	@Override
	public void stopSeenByPlayer(final ServerPlayer joueur) {
		super.stopSeenByPlayer(joueur);
		this.barreDeVie.removePlayer(joueur);
	}

	@Override
	public void setCustomName(final @Nullable Component nom) {
		super.setCustomName(nom);
		this.barreDeVie.setName(this.getDisplayName());
	}

	/** Un boss ne disparaît jamais tout seul quand le joueur s'éloigne. */
	@Override
	public boolean removeWhenFarAway(final double distanceCarree) {
		return false;
	}

	// --- Sauvegarde -------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(final ValueOutput sortie) {
		super.addAdditionalSaveData(sortie);
		sortie.putInt("Phase", this.phase());
		if (this.centreArene != null) {
			sortie.putBoolean("AArene", true);
			sortie.putInt("AreneX", this.centreArene.getX());
			sortie.putInt("AreneY", this.centreArene.getY());
			sortie.putInt("AreneZ", this.centreArene.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput entree) {
		super.readAdditionalSaveData(entree);
		this.entityData.set(PHASE, entree.getIntOr("Phase", 1));
		if (entree.getBooleanOr("AArene", false)) {
			this.centreArene = new BlockPos(entree.getIntOr("AreneX", 0), entree.getIntOr("AreneY", 0), entree.getIntOr("AreneZ", 0));
		}
		if (this.hasCustomName()) {
			this.barreDeVie.setName(this.getDisplayName());
		}
	}

	// --- Sons -------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6F;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	// --- GeckoLib ---------------------------------------------------------------------

	@Override
	public void registerControllers(final AnimatableManager.ControllerRegistrar controleurs) {
		controleurs.add(new AnimationController<PremierGardienEntity>("corps", 5, test -> {
			if (test.getDataOrDefault(DataTickets.IS_DEAD_OR_DYING, false)) {
				return test.setAndContinue(MORT);
			}
			return test.setAndContinue(test.isMoving() ? MARCHE : REPOS);
		}));
		controleurs.add(new AnimationController<PremierGardienEntity>("actions", 2, test -> PlayState.STOP)
			.triggerableAnim("frappe", FRAPPE)
			.triggerableAnim("fracas", FRACAS)
			.triggerableAnim("invocation", INVOCATION));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cacheAnimations;
	}
}
