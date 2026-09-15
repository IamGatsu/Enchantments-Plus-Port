package com.robdog777.enchantmentsplus.statuseffects;

// PORT-NOTE: unverifiziert fuer 26.2. StatusEffect/StatusEffectCategory
// (Yarn) -> MobEffect/MobEffectCategory (Mojang, Bestwert).

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class MoonRestEffect extends MobEffect {
    public MoonRestEffect() {
        super(
                MobEffectCategory.BENEFICIAL,
                0x464d43);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // PORT-NOTE: Methodenname "canApplyUpdateEffect" (Yarn) wurde hier
        // geraten als "shouldApplyEffectTickThisTick" (Mojang) - bitte pruefen.
        return true;
    }
}
