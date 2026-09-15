package com.robdog777.enchantmentsplus.mixin;

// PORT-NOTE: Alle Klassen-/Methodennamen in diesem File sind Mojang-Mappings
// (Bestwert aus Trainingsdaten bis Anfang 2026, ca. MC 1.21.4/1.21.5) und
// UNVERIFIZIERT fuer 26.2. Bitte gegen ein 26.2-Client-JAR pruefen, bevor
// dieser Mixin kompiliert/getestet wird - siehe PORT-NOTES.md.

import com.robdog777.enchantmentsplus.EnchantmentsPlus;
import com.robdog777.enchantmentsplus.util.EnchantmentIds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerEntityMixin {
    // Excavator
    // PORT-NOTE: Yarns "getBlockInteractionRange" -> vermutlich Mojangs
    // "blockInteractionRange()". Bitte pruefen.
    @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void modifyBlockReach(CallbackInfoReturnable<Double> cir) {
        LivingEntity currentEntity = (LivingEntity) (Object) this;

        if (currentEntity instanceof Player) {
            double currentValue = currentEntity.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
            // PORT-NOTE: EnchantmentHelper braucht jetzt eine RegistryAccess/
            // Holder<Enchantment>-Referenz statt eines Enchantment-Objekts,
            // siehe EnchantmentIds.java. Exakte Methode/Signatur unverifiziert.
            int excavatorLevel = EnchantmentHelper.getEnchantmentLevel(
                    EnchantmentIds.holder(currentEntity.level().registryAccess(), EnchantmentIds.EXCAVATOR),
                    currentEntity);

            if (excavatorLevel > 0 && EnchantmentsPlus.CONFIG_HOLDER.getConfig().enableExcavator) {
                cir.setReturnValue(currentValue + excavatorLevel);
            }
        }
    }
}
