package com.robdog777.enchantmentsplus.mixin;

// FIX-STATUS: Recipe-Zugriff (recipeAccess/getRecipes/input()/assemble/experience())
// und EnchantmentHelper.getEnchantmentLevel(Holder, LivingEntity) wurden gegen
// das echte 26.2-Client-Jar verifiziert (siehe Kommentare unten).

import com.robdog777.enchantmentsplus.EnchantmentsPlus;
import com.robdog777.enchantmentsplus.util.EnchantmentIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(Block.class)
public class BlockMixin {
    // smelt with FlashForge
    // PORT-NOTE (Fixrunde 8): Echte Signatur gegen das lokale, unobfuskierte
    // 26.2-Client-Jar per Bytecode-Inspektion verifiziert. Minecraft 26.2 hat
    // ein neues Interface net.minecraft.world.item.ItemInstance eingefuehrt,
    // das ItemStack implementiert; der "tool"-Parameter von Block#getDrops
    // ist jetzt vom Typ ItemInstance statt ItemStack. Der Rueckgabetyp
    // (List<ItemStack>) und alle anderen Parameter sind unveraendert.
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void getDroppedStacks(BlockState state, ServerLevel world, BlockPos pos, BlockEntity blockEntity,
                                          Entity entity, ItemInstance stack, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> returnValue = cir.getReturnValue();

        // FIX (verifiziert gegen 26.2-Client-Jar): EnchantmentHelper.getEnchantmentLevel(...)
        // nimmt nur noch eine LivingEntity als zweiten Parameter (kein ItemStack
        // mehr). FlashForge ist eine Werkzeug-Verzauberung, deshalb pruefen wir
        // hier den block-abbauenden Entity (falls es eine LivingEntity ist).
        int flashForgeLevel = 0;
        if (entity instanceof net.minecraft.world.entity.LivingEntity breakingEntity) {
            flashForgeLevel = EnchantmentHelper.getEnchantmentLevel(
                    EnchantmentIds.holder(world.registryAccess(), EnchantmentIds.FLASHFORGE), breakingEntity);
        }

        if (flashForgeLevel == 0 || !EnchantmentsPlus.CONFIG_HOLDER.getConfig().enableFlashForge) {
            return;
        }

        // FIX (verifiziert gegen 26.2-Client-Jar):
        // - ServerLevel#getRecipeManager() heisst jetzt recipeAccess() (liefert
        //   dort konkret weiterhin einen RecipeManager, siehe Level#recipeAccess()
        //   vs. der ueberschriebenen ServerLevel-Variante).
        // - RecipeManager#getAllRecipesFor(RecipeType) existiert nicht mehr;
        //   stattdessen ueber getRecipes() (alle Rezepte) filtern.
        // - Recipe#getIngredients() (Liste) gibt es nicht mehr - AbstractCookingRecipe/
        //   SingleItemRecipe haben stattdessen ein einzelnes input(): Ingredient.
        // - Recipe#getResultItem(RegistryAccess) gibt es nicht mehr; stattdessen
        //   assemble(SingleRecipeInput) mit dem tatsaechlichen Eingabe-ItemStack.
        // - AbstractCookingRecipe#getExperience() heisst jetzt experience().
        for (int i = 0; i < returnValue.size(); i++) {
            ItemStack itemStack = returnValue.get(i);
            Optional<RecipeHolder<SmeltingRecipe>> recipe = world.recipeAccess().getRecipes().stream()
                    .filter(r -> r.value() instanceof SmeltingRecipe)
                    .map(r -> new RecipeHolder<>(r.id(), (SmeltingRecipe) r.value()))
                    .filter(r -> r.value().input().test(itemStack))
                    .findFirst();

            if (recipe.isPresent()) {
                ItemStack smelted = recipe.get().value().assemble(new SingleRecipeInput(itemStack));
                smelted = smelted.copy();
                smelted.setCount(itemStack.getCount());
                returnValue.set(i, smelted);

                world.addFreshEntity(new ExperienceOrb(world, pos.getX(), pos.getY(), pos.getZ(),
                        (int) recipe.get().value().experience()));

                if (state.getBlock() == Blocks.IRON_ORE || state.getBlock() == Blocks.DEEPSLATE_IRON_ORE ||
                        state.getBlock() == Blocks.COPPER_ORE || state.getBlock() == Blocks.DEEPSLATE_COPPER_ORE) {
                    world.addFreshEntity(new ExperienceOrb(world, pos.getX(), pos.getY(), pos.getZ(), 1));
                }
            }
        }
    }
}
