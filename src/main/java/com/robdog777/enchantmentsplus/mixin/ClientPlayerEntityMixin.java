package com.robdog777.enchantmentsplus.mixin;

// FIX-STATUS: gegen 26.2-Client-Jar verifiziert. player.input ist ein
// ClientInput mit einem Record-Feld "keyPresses" vom Typ
// net.minecraft.world.entity.player.Input; dessen Sprung-Zustand heisst
// jetzt jump() statt jumping (Accessor-Methode statt Feld). ElytraItem
// existiert nicht mehr; die Flugfaehigkeits-Pruefung ist jetzt
// LivingEntity.canGlideUsing(ItemStack, EquipmentSlot).

import com.robdog777.enchantmentsplus.EnchantmentsPlus;
import com.robdog777.enchantmentsplus.SharedStates;
import com.robdog777.enchantmentsplus.util.EnchantmentIds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
    @Unique
    private int jumpCount = 0;
    @Unique
    private boolean jumpedLastTick = false;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void tickMovement(CallbackInfo info) {
        LocalPlayer player = (LocalPlayer) (Object) this;

        // Dual Leap
        if (player.onGround() || player.onClimbable()) {
            jumpCount = EnchantmentHelper.getEnchantmentLevel(
                    EnchantmentIds.holder(player.level().registryAccess(), EnchantmentIds.DUALLEAP), player);
        } else if (!jumpedLastTick && jumpCount > 0 && player.getDeltaMovement().y < 0) {
            // FIX (verifiziert): player.input.jumping -> player.input.keyPresses.jump()
            if (player.fallDistance < 4.0f && player.input.keyPresses.jump() && canJump(player)
                    && EnchantmentsPlus.CONFIG_HOLDER.getConfig().enableDualLeap) {
                jumpCount--;
                player.jumpFromGround();

                SharedStates.dualLeapSuccessful = true;
            } else if (player.fallDistance >= 4.0f) {
                SharedStates.dualLeapFailed = true;
            }
        }

        jumpedLastTick = player.input.keyPresses.jump();
    }

    @Unique
    private boolean canJump(LocalPlayer player) {
        ItemStack chestItemStack = player.getItemBySlot(EquipmentSlot.CHEST);
        // FIX (verifiziert): ElytraItem.isFlyEnabled(ItemStack) ersetzt durch
        // LivingEntity.canGlideUsing(ItemStack, EquipmentSlot).
        boolean wearingUsableElytra = chestItemStack.getItem() == Items.ELYTRA
                && LivingEntity.canGlideUsing(chestItemStack, EquipmentSlot.CHEST);

        return !wearingUsableElytra && !player.isFallFlying() && !player.isPassenger()
                && !player.isInWater() && !player.hasEffect(MobEffects.LEVITATION)
                && !player.getAbilities().instabuild && !player.getAbilities().flying;
    }
}
