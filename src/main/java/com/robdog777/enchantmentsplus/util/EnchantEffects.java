package com.robdog777.enchantmentsplus.util;

// PORT-NOTE: unverifiziert fuer 26.2, Mojang-Mappings Bestwert. Enthaelt die
// Spiellogik, die frueher direkt in den (jetzt geloeschten) Enchantment-
// Unterklassen stand, siehe PORT-NOTES.md fuer die Zuordnung alt -> neu.

import com.robdog777.enchantmentsplus.EnchantmentsPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class EnchantEffects {
    private EnchantEffects() {}

    // Blaze Walker - urspruenglich BlazeWalkerEnchantment#freezeLava,
    // basiert selbst auf dem Vanilla-Code von Frost Walker.
    public static void freezeLava(LivingEntity entity, Level world, BlockPos blockPos, int level) {
        if (entity.onGround() && EnchantmentsPlus.CONFIG_HOLDER.getConfig().enableBlazeWalker) {
            BlockState blockState = Blocks.OBSIDIAN.defaultBlockState();
            int f = Math.min(16, 2 + level);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-f, -1, -f), blockPos.offset(f, -1, f))) {
                // FIX (verifiziert gegen 26.2-Client-Jar): Vec3i#closerThan(Vec3i, double)
                // nimmt keine Position/Vec3 mehr an. Das Aequivalent fuer
                // "ist eine Entity nah an diesem Block" ist jetzt
                // Vec3i#closerToCenterThan(Position, double).
                if (blockPos2.closerToCenterThan(entity.position(), f)) {
                    mutable.set(blockPos2.getX(), blockPos2.getY() + 1, blockPos2.getZ());
                    BlockState blockState2 = world.getBlockState(mutable);
                    if (blockState2.isAir()) {
                        BlockState blockState3 = world.getBlockState(blockPos2);
                        Block block = blockState3.getBlock();
                        // PORT-NOTE: "FluidBlock" (Yarn) -> "LiquidBlock" (Mojang).
                        // "ShapeContext.absent()" (Yarn) -> "CollisionContext.empty()".
                        // "world.canPlace(...)" existierte in Yarn 1.20.6 so - Mojang-
                        // Aequivalent hier als bestmoegliche Vermutung eingesetzt.
                        if (blockState3.is(Blocks.LAVA) && block instanceof LiquidBlock
                                && blockState3.getValue(LiquidBlock.LEVEL) == 0
                                && blockState.canSurvive(world, blockPos2)
                                && world.isUnobstructed(blockState, blockPos2, CollisionContext.empty())) {
                            world.setBlockAndUpdate(blockPos2, blockState);
                            world.scheduleTick(blockPos2, Blocks.OBSIDIAN, Mth.nextInt(entity.getRandom(), 60, 120));
                        }
                    }
                }
            }
        }
    }
}
