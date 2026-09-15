package com.robdog777.enchantmentsplus.util;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * PORT-NOTE: Ersatz fuer die frueheren statischen "public static Enchantment
 * BLAZEWALKER = ...;"-Felder in EnchantmentsPlus.java. Da Enchantments jetzt
 * Registry-Eintraege aus Datenpack-JSON sind (siehe
 * data/enchantmentsplus/enchantment/*.json) und nicht mehr als Java-Objekt
 * existieren, bevor die Welt/der Server geladen hat, haelt diese Klasse nur
 * die ResourceKeys bereit. In Mixins damit z. B. so verwenden:
 *
 *   int level = EnchantmentHelper.getEnchantmentLevel(
 *           EnchantmentIds.holder(registryAccess, EnchantmentIds.HIKER), itemStack);
 *
 * "registryAccess" ist dabei die RegistryAccess des aktuellen Levels (z. B.
 * entity.level().registryAccess() / entity.registryAccess()).
 *
 * FIX (verifiziert gegen den Compiler-Log): RegistryAccess.holderOrThrow(key)
 * existiert in 26.2 nicht mehr direkt (38 Fehler-Log, 15x "Methode
 * holderOrThrow(ResourceKey<Enchantment>) nicht gefunden"). Stattdessen muss
 * man ueber die Registry selbst gehen: registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
 * liefert eine HolderLookup<Enchantment>, darauf dann .getOrThrow(key). Dieses
 * Muster wurde bereits an einer Stelle im Originalcode (LivingEntityMixin,
 * MoonWalker-Effekt-Lookup) verwendet und kompilierte dort fehlerfrei - siehe
 * die Methode holder(...) unten, die das fuer Enchantments buendelt.
 */
public final class EnchantmentIds {
    private EnchantmentIds() {}

    /** Ersetzt registryAccess.holderOrThrow(key), das in 26.2 nicht mehr existiert. */
    public static Holder<Enchantment> holder(RegistryAccess registryAccess, ResourceKey<Enchantment> key) {
        return registryAccess.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ResourceKey<Enchantment> of(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath("enchantmentsplus", path));
    }

    public static final ResourceKey<Enchantment> BLAZEWALKER = of("blazewalker");
    public static final ResourceKey<Enchantment> CUBICAL = of("cubical");
    public static final ResourceKey<Enchantment> DUALLEAP = of("dualleap");
    public static final ResourceKey<Enchantment> ENDSLAYER = of("endslayer");
    public static final ResourceKey<Enchantment> EXCAVATOR = of("excavator");
    public static final ResourceKey<Enchantment> FLASHFORGE = of("flashforge");
    public static final ResourceKey<Enchantment> FROSTBITE = of("frostbite");
    public static final ResourceKey<Enchantment> HIKER = of("hiker");
    public static final ResourceKey<Enchantment> LEVITATION = of("levitation");
    public static final ResourceKey<Enchantment> LIFESTEAL = of("lifesteal");
    public static final ResourceKey<Enchantment> LUNARSIGHT = of("lunarsight");
    public static final ResourceKey<Enchantment> MOONWALKER = of("moonwalker");
    public static final ResourceKey<Enchantment> MYSTICMIND = of("mysticmind");
    public static final ResourceKey<Enchantment> PAYBACK = of("payback");
    public static final ResourceKey<Enchantment> RAIDER = of("raider");
    public static final ResourceKey<Enchantment> SNIPER = of("sniper");
    public static final ResourceKey<Enchantment> STORMSTRIKE = of("stormstrike");
    public static final ResourceKey<Enchantment> THUNDERLORD = of("thunderlord");
    public static final ResourceKey<Enchantment> TOXICSTRIKE = of("toxicstrike");
}
