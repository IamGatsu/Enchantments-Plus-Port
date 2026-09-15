package com.robdog777.enchantmentsplus.util;

/*
 * ERSETZT EnchantmentEffectsMixin (beide Injects).
 *
 * Grund 1: LivingEntity#doHurtTarget ist der MOB-Angriffspfad. Ein Spieler-
 * Nahkampfschlag laeuft ueber Player#attack(Entity) und ruft doHurtTarget
 * NICHT auf -> der alte Inject feuerte bei Spielern nie.
 *
 * Grund 2: Bogen-Enchantments (Sniper) treffen ueber ein Projektil, also
 * ueberhaupt keinen Nahkampf-Pfad.
 *
 * Statt zwei riskante Mixin-Ziele gegen unbekannte 26.2-Mappings zu raten,
 * haengt alles an ServerLivingEntityEvents.AFTER_DAMAGE aus der Fabric API
 * (schon als Dependency vorhanden). Das Event feuert serverseitig NACH jedem
 * angewandten Schaden - egal ob Spieler, Mob, Nahkampf oder Projektil.
 *
 * Aufruf in EnchantmentsPlus#onInitialize() ergaenzen:
 *     CombatEffects.register();
 * und EnchantmentEffectsMixin.java loeschen + aus enchantmentsplus.mixins.json
 * streichen.
 */

import com.robdog777.enchantmentsplus.EnchantmentsPlus;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class CombatEffects {
    private CombatEffects() {}

    /**
     * Re-Entrancy-Schutz: Payback/Raider/EndSlayer/Cubical/Sniper fuegen
     * innerhalb des Handlers selbst Schaden zu und loesen das Event damit
     * erneut aus. Ohne diese Sperre -> Endlosrekursion / StackOverflow.
     */
    private static final ThreadLocal<Boolean> IN_HANDLER = ThreadLocal.withInitial(() -> false);

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamage, damageTaken, blocked) -> {
                    if (blocked || IN_HANDLER.get()) {
                        return;
                    }
                    if (!(entity.level() instanceof ServerLevel level)) {
                        return;
                    }
                    IN_HANDLER.set(true);
                    try {
                        onUserDamaged(level, entity);

                        Entity attacker = source.getEntity();
                        Entity direct = source.getDirectEntity();
                        if (attacker instanceof LivingEntity user && user != entity) {
                            boolean melee = (direct == attacker);
                            // FIX (Fixrunde 11): AbstractArrow existiert unter diesem
                            // Namen/Paket in 26.2 nicht (Compiler: "Symbol nicht
                            // gefunden"). Statt eine neue Vermutung fuer den
                            // Klassennamen zu raten, wird hier auf das stabile
                            // Projectile-Interface geprueft, das jedes Wurfgeschoss
                            // (Pfeil, Dreizack, Schneeball, ...) implementiert und
                            // ueber viele Versionen unveraendert war. Deckt Sniper
                            // damit etwas breiter ab als nur Pfeile - fuer eine reine
                            // Bogen-Verzauberung unschaedlich, aber bitte im Spiel
                            // gegentesten (z.B. mit einem geworfenen Dreizack).
                            boolean arrow = (direct instanceof Projectile) && direct != attacker;
                            if (melee || arrow) {
                                onTargetDamaged(level, user, entity, melee, arrow);
                            }
                        }
                    } finally {
                        IN_HANDLER.set(false);
                    }
                });
    }

    /**
     * Zusatzschaden MUSS die Unverwundbarkeitsphase umgehen. Nach einem
     * Treffer steht invulnerableTime auf 20 Ticks; ein zweiter hurt()-Aufruf
     * mit kleinerem Betrag wird sonst komplett verschluckt - das ist der
     * zweite Grund, warum Payback/Raider/EndSlayer/Cubical/Sniper "nichts
     * machen", selbst wenn der Hook feuert.
     */
    private static void extraDamage(ServerLevel level, Entity target, float amount) {
        if (amount <= 0) {
            return;
        }
        target.invulnerableTime = 0;
        // PORT-NOTE: falls 26.2 nur noch hurtServer(ServerLevel, DamageSource,
        // float) kennt, hier entsprechend umstellen.
        target.hurt(level.damageSources().generic(), amount);
    }

    private static void onTargetDamaged(ServerLevel level, LivingEntity user, LivingEntity target,
                                        boolean melee, boolean arrow) {
        var registryAccess = level.registryAccess();
        var config = EnchantmentsPlus.CONFIG_HOLDER.getConfig();

        if (arrow) {
            // Sniper - nur Bogen/Projektil
            int sniperLevel = EnchantmentHelper.getEnchantmentLevel(
                    EnchantmentIds.holder(registryAccess, EnchantmentIds.SNIPER), user);
            if (sniperLevel > 0 && config.enableSniper) {
                float distance = user.distanceTo(target);
                if (distance > 10) {
                    extraDamage(level, target, Math.min(sniperLevel * (distance / 2.5F), 40));
                }
            }
            return;
        }

        if (!melee) {
            return;
        }

        // Payback
        int paybackLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.PAYBACK), user);
        if (paybackLevel > 0 && user.getHealth() < 10 && config.enablePayback) {
            extraDamage(level, target, paybackLevel * 0.5F * (20 - user.getHealth()));
        }

        // Levitation
        int levitationLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.LEVITATION), user);
        if (levitationLevel > 0 && config.enableLevitation) {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * levitationLevel, levitationLevel - 1));
        }

        // Frostbite
        int frostbiteLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.FROSTBITE), user);
        if (frostbiteLevel > 0 && config.enableFrostbite) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 * frostbiteLevel, frostbiteLevel - 1));
        }

        // Toxic Strike
        int toxicLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.TOXICSTRIKE), user);
        if (toxicLevel > 0 && config.enableToxicStrike) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 40 * toxicLevel, toxicLevel - 1));
        }

        // Raider
        int raiderLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.RAIDER), user);
        if (raiderLevel > 0 && config.enableRaider
                && (target instanceof Raider || target instanceof Witch || target instanceof Vex)) {
            extraDamage(level, target, raiderLevel * 5F);
        }

        // End Slayer
        int endSlayerLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.ENDSLAYER), user);
        if (endSlayerLevel > 0 && config.enableEndSlayer) {
            if (target instanceof EnderMan || target instanceof Endermite || target instanceof Shulker) {
                extraDamage(level, target, endSlayerLevel * 5F);
            }
            if (target instanceof EnderDragon) {
                extraDamage(level, target, endSlayerLevel * 10F);
            }
        }

        // Cubical
        int cubicalLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.CUBICAL), user);
        if (cubicalLevel > 0 && config.enableCubical
                && (target instanceof Creeper || target instanceof Slime)) {
            extraDamage(level, target, cubicalLevel * 5F);
        }

        // Thunderlord
        int thunderlordLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.THUNDERLORD), user);
        if (thunderlordLevel > 0 && config.enableThunderlord) {
            spawnChanceLightning(level, target, thunderlordLevel, 10);
        }

        // Storm Strike
        int stormStrikeLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.STORMSTRIKE), user);
        if (stormStrikeLevel > 0 && config.enableStormStrike) {
            spawnChanceLightning(level, target, stormStrikeLevel, 20);
        }

        // Life Steal
        int lifeStealLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(registryAccess, EnchantmentIds.LIFESTEAL), user);
        if (lifeStealLevel > 0 && config.enableLifeSteal) {
            if (user.getHealth() < user.getMaxHealth()
                    && user.getRandom().nextDouble() < 0.10 + (lifeStealLevel * 0.20)) {
                float targetHealth = target.getHealth();
                if (targetHealth > 0) {
                    level.playSound(null, user.blockPosition(), EnchantmentsPlus.BlurpEvent,
                            SoundSource.PLAYERS, 1.0f, 1f);
                    user.heal(targetHealth * 0.5f);
                }
            }
        }
    }

    /** Ersetzt MysticMindEnchantment#onUserDamaged. */
    private static void onUserDamaged(ServerLevel level, LivingEntity user) {
        var config = EnchantmentsPlus.CONFIG_HOLDER.getConfig();
        int mysticMindLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(level.registryAccess(), EnchantmentIds.MYSTICMIND), user);
        if (mysticMindLevel <= 0 || user.getHealth() >= 6 || !config.enableMysticMind) {
            return;
        }

        double d = user.getX();
        double e = user.getY();
        double f = user.getZ();

        for (int i = 0; i < 16; ++i) {
            double g = user.getX() + (user.getRandom().nextDouble() - 0.5) * 16.0;
            double h = Mth.clamp(user.getY() + (user.getRandom().nextInt(16) - 8),
                    level.getMinY(), level.getMinY() + level.getLogicalHeight() - 1);
            double j = user.getZ() + (user.getRandom().nextDouble() - 0.5) * 16.0;
            if (user.isPassenger()) {
                user.stopRiding();
            }

            Vec3 vec3 = user.position();
            if (user.randomTeleport(g, h, j, true)) {
                level.gameEvent(user, GameEvent.TELEPORT, vec3);
                SoundEvent soundEvent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                level.playSound(null, d, e, f, soundEvent, SoundSource.PLAYERS, 1.0F, 1.0F);
                user.playSound(soundEvent, 1.0F, 1.0F);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void spawnChanceLightning(ServerLevel level, Entity target, int enchantLevel, int randomBound) {
        BlockPos blockPos = target.blockPosition();
        // FIX (Fixrunde 11): "random" ist in Level nur protected zugaenglich,
        // von hier aus (anderes Package) nicht erreichbar. getRandom() ist der
        // oeffentliche Getter dafuer.
        if (level.getRandom().nextInt(randomBound) < enchantLevel && level.canSeeSky(blockPos)) {
            EntityType<LightningBolt> lightningType = (EntityType<LightningBolt>) (EntityType<?>)
                    BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("lightning_bolt"));
            LightningBolt lightningEntity = lightningType.create(level, EntitySpawnReason.TRIGGERED);
            if (lightningEntity != null) {
                lightningEntity.setPos(target.getX(), target.getY(), target.getZ());
                level.addFreshEntity(lightningEntity);
            }
        }
    }
}
