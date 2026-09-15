package com.robdog777.enchantmentsplus;

import com.robdog777.enchantmentsplus.config.EnchantmentsPlusConfig;
import com.robdog777.enchantmentsplus.statuseffects.MoonRestEffect;
import com.robdog777.enchantmentsplus.util.CombatEffects;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
// PORT-NOTE: In den 26.x-Mappings heisst die Klasse "Identifier" (nicht
// "ResourceLocation" wie in aelteren Mojang-Mappings/mein urspruenglicher
// Tippfehler). Falls "Identifier.fromNamespaceAndPath(...)" nicht existiert,
// alternativ "new Identifier(namespace, path)" oder "Identifier.of(...)"
// probieren - Methodenname dieser statischen Fabrik ist unverifiziert.
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PORT-NOTE (WICHTIG): Seit Minecraft 1.21 sind Enchantments vollstaendig
 * datengetrieben (siehe src/main/resources/data/enchantmentsplus/enchantment/*.json).
 * Es gibt daher KEINE Java-Objekte mehr, die man wie in 1.20.6
 * "Registry.register(Registries.ENCHANTMENT, id, new XyzEnchantment())" haendisch
 * anlegt. Stattdessen referenziert man eine Verzauberung ueber einen
 * ResourceKey<Enchantment>, der zur Laufzeit gegen die DynamicRegistryManager
 * des Levels/Servers aufgeloest wird (siehe EnchantmentIds.java und die
 * neuen/aktualisierten Mixins, die EnchantmentHelper.getEnchantmentLevel(...)
 * mit einem Holder<Enchantment> statt eines Enchantment-Objekts aufrufen).
 *
 * PORT-NOTE: Klassen-/Methodennamen hier sind Mojang-Mappings-Bestwerte
 * (Stand Trainingsdaten bis Anfang 2026, MC ~1.21.4/1.21.5). Da 26.2 laut
 * Fabric-Blog "primaer Rendering und Registrierung" veraendert hat, koennen
 * sich gerade Registry-bezogene Signaturen (BuiltInRegistries, Registries,
 * Registry.register) nochmal verschoben haben - bitte gegen die tatsaechliche
 * 26.2-API im entpackten/decompilierten Client pruefen.
 */
public class EnchantmentsPlus implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("enchantmentsplus");

    // moon png file from https://www.pngitem.com
    // Audio files copyright free from https://freesound.org/
    public static final Identifier SWOOP = Identifier.fromNamespaceAndPath("enchantmentsplus", "swoop");
    public static final Identifier BLURP = Identifier.fromNamespaceAndPath("enchantmentsplus", "blurp");
    public static final Identifier WHOOSH = Identifier.fromNamespaceAndPath("enchantmentsplus", "whoosh");
    public static final Identifier DENY = Identifier.fromNamespaceAndPath("enchantmentsplus", "deny");

    public static final ConfigHolder<EnchantmentsPlusConfig> CONFIG_HOLDER = AutoConfig.register(
            EnchantmentsPlusConfig.class, JanksonConfigSerializer::new);

    public static MobEffect MOONREST = new MoonRestEffect();

    public static SoundEvent SwoopEvent = SoundEvent.createVariableRangeEvent(SWOOP);
    public static SoundEvent BlurpEvent = SoundEvent.createVariableRangeEvent(BLURP);
    public static SoundEvent WhooshEvent = SoundEvent.createVariableRangeEvent(WHOOSH);
    public static SoundEvent DenyEvent = SoundEvent.createVariableRangeEvent(DENY);

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        LOGGER.info("enchantmentsplus is now loaded");

        Registry.register(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath("enchantmentsplus", "moonresteffect"), MOONREST);
        Registry.register(BuiltInRegistries.SOUND_EVENT, SWOOP, SwoopEvent);
        Registry.register(BuiltInRegistries.SOUND_EVENT, BLURP, BlurpEvent);
        Registry.register(BuiltInRegistries.SOUND_EVENT, WHOOSH, WhooshEvent);
        Registry.register(BuiltInRegistries.SOUND_EVENT, DENY, DenyEvent);

        AutoConfig.getConfigHolder(EnchantmentsPlusConfig.class).getConfig();
        CONFIG_HOLDER.load();

        // FIX (Fixrunde 10): Die Kampf-Verzauberungen haengen nicht mehr an
        // LivingEntity#doHurtTarget (das ist der Mob-Angriffspfad und feuert
        // bei Spielerschlaegen nie), sondern an
        // ServerLivingEntityEvents.AFTER_DAMAGE. Siehe util/CombatEffects.java.
        CombatEffects.register();

        // Die 19 Verzauberungen selbst werden NICHT mehr hier registriert -
        // sie kommen rein datengetrieben aus
        // src/main/resources/data/enchantmentsplus/enchantment/*.json.
        // Java-seitig braucht es nur noch die ResourceKey-Referenzen in
        // EnchantmentIds, damit Mixins die jeweilige Stufe abfragen koennen.
    }
}
