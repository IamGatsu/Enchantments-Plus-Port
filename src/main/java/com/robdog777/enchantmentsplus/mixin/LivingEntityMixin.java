package com.robdog777.enchantmentsplus.mixin;

// PORT-NOTE: unverifiziert fuer 26.2, Mojang-Mappings Bestwert.

import com.robdog777.enchantmentsplus.EnchantmentsPlus;
import com.robdog777.enchantmentsplus.SharedStates;
import com.robdog777.enchantmentsplus.config.EnchantmentsPlusConfig;
import com.robdog777.enchantmentsplus.util.EnchantEffects;
import com.robdog777.enchantmentsplus.util.EnchantmentIds;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    protected LivingEntityMixin(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    // FIX (Bugreport: Blaze Walker verwandelt keine Lava zu Obsidian):
    // Der bisherige Ansatz injizierte in "checkFallDamage(...)". Dieses Ziel
    // wurde ueber mehrere Fixrunden hinweg als "unverifiziert/offen"
    // markiert (siehe PORT-NOTES-CHAT-FIXRUNDE10.md) und nie tatsaechlich
    // im Spiel bestaetigt - seit dem 1.21-Rework laeuft Vanilla-Frost-Walker
    // ueber die datengetriebene "minecraft:location_changed"-Enchantment-
    // Effect-Komponente und NICHT mehr zwingend ueber diese Methode, daher
    // war unklar, ob/wann der Inject ueberhaupt feuert.
    // Ersatz: Blaze Walker haengt jetzt am bereits vorhandenen, verifizierten
    // 5-Tick-Intervall in tick() (siehe unten, selbes Muster wie Lunar Sight
    // und Moon Walker) - deutlich robuster, weil es auf keinen unsicheren
    // Methodennamen mehr angewiesen ist.
    @Unique
    private void applyBlazeWalker(LivingEntity currentEntity) {
        int blazeWalkerLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(currentEntity.level().registryAccess(), EnchantmentIds.BLAZEWALKER), currentEntity);
        if (blazeWalkerLevel > 0 && EnchantmentsPlus.CONFIG_HOLDER.getConfig().enableBlazeWalker) {
            EnchantEffects.freezeLava(currentEntity, this.level(), currentEntity.blockPosition(), blazeWalkerLevel);
        }
    }

    // FIX (verifiziert): LivingEntity#getStepHeight() zum Ueberschreiben
    // existiert seit Minecraft 1.20+ nicht mehr - Step-Height laeuft nur
    // noch ueber das STEP_HEIGHT-Attribut (daher "ueberschreibt keine
    // Methode aus einem Supertyp" im Compiler-Log). Ersatz: transienter
    // AttributeModifier, der in tick() pro Tick neu gesetzt/entfernt wird.
    @Unique
    private static final Identifier HIKER_STEP_HEIGHT_ID =
            Identifier.fromNamespaceAndPath("enchantmentsplus", "hiker_step_height");

    @Unique
    private void applyHikerStepHeight(LivingEntity currentEntity) {
        var stepAttr = currentEntity.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr == null) {
            return;
        }
        stepAttr.removeModifier(HIKER_STEP_HEIGHT_ID);

        if (!(currentEntity instanceof Player)) {
            return;
        }

        int hikerLevel = EnchantmentHelper.getEnchantmentLevel(
                EnchantmentIds.holder(currentEntity.level().registryAccess(), EnchantmentIds.HIKER), currentEntity);
        if (hikerLevel > 0 && EnchantmentsPlus.CONFIG_HOLDER.getConfig().enableHiker) {
            double height = hikerLevel + 0.1D;
            double defaultHeight = stepAttr.getBaseValue();
            if (height > defaultHeight) {
                stepAttr.addTransientModifier(new AttributeModifier(
                        HIKER_STEP_HEIGHT_ID, height - defaultHeight, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    protected void tick(CallbackInfo ci) {
        LivingEntity currentEntity = (LivingEntity) (Object) this;

        applyHikerStepHeight(currentEntity);

        if (currentEntity instanceof Player player) {
            if (SharedStates.dualLeapSuccessful) {
                player.level().playSound(null, player.blockPosition(), EnchantmentsPlus.WhooshEvent,
                        SoundSource.PLAYERS, 0.7f, 1f);
                SharedStates.dualLeapSuccessful = false;
            } else if (SharedStates.dualLeapFailed) {
                player.level().playSound(null, player.blockPosition(), EnchantmentsPlus.DenyEvent,
                        SoundSource.PLAYERS, 0.7f, 1f);
                SharedStates.dualLeapFailed = false;
            }
        }

        // Only process every 5 ticks (4 times per second) instead of every tick
        if (currentEntity.tickCount % 5 == 0) {
            ConfigHolder<EnchantmentsPlusConfig> config = EnchantmentsPlus.CONFIG_HOLDER;
            var registryAccess = currentEntity.level().registryAccess();

            // Blaze Walker
            applyBlazeWalker(currentEntity);

            // Lunar Sight
            if (config.getConfig().enableLunarSight) {
                int nightVisionLevel = EnchantmentHelper.getEnchantmentLevel(
                        EnchantmentIds.holder(registryAccess, EnchantmentIds.LUNARSIGHT), currentEntity);

                if (nightVisionLevel > 0) {
                    currentEntity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                            220, 0, false, false, true));
                }
            }

            // Moon Walker
            if (config.getConfig().enableMoonWalker) {
                Holder<net.minecraft.world.effect.MobEffect> moonrestEntry =
                        currentEntity.level().registryAccess()
                                .lookupOrThrow(net.minecraft.core.registries.Registries.MOB_EFFECT)
                                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.MOB_EFFECT,
                                        net.minecraft.resources.Identifier.fromNamespaceAndPath("enchantmentsplus", "moonresteffect")));
                int moonWalkerLevel = EnchantmentHelper.getEnchantmentLevel(
                        EnchantmentIds.holder(registryAccess, EnchantmentIds.MOONWALKER), currentEntity);

                if (moonWalkerLevel > 0) {
                    // PORT-NOTE (Verdachtsfix, nicht 100% verifiziert): siehe
                    // SLOWNESS-Kommentar in EnchantmentEffectsMixin - dasselbe
                    // Muster spricht dafuer, dass MOVEMENT_SPEED zu SPEED wurde.
                    currentEntity.addEffect(new MobEffectInstance(MobEffects.SPEED, 20,
                            0, false, false, true));
                    currentEntity.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 20,
                            0, false, false, true));

                    if (!currentEntity.hasEffect(moonrestEntry)) {
                        currentEntity.level().playSound(null, currentEntity.blockPosition(),
                                EnchantmentsPlus.SwoopEvent, currentEntity.getSoundSource(), 1.0f, 1f);

                        currentEntity.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST,
                                moonWalkerLevel * 100, moonWalkerLevel + 1, false, false, true));

                        currentEntity.addEffect(new MobEffectInstance(moonrestEntry, 400, 0, false, false, true));
                    }
                }
            }
        }
    }
}
