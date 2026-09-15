package com.robdog777.enchantmentsplus.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    // FIX (verifiziert gegen den cloth-config-26.2-Quellcode): getConfigScreen(...)
    // wurde von AutoConfig in eine neue, client-only Klasse AutoConfigClient
    // verschoben (selbes Package me.shedaniel.autoconfig). AutoConfig selbst
    // hat seitdem nur noch register(...) und getConfigHolder(...) - deshalb
    // kompilierten genau die beiden in EnchantmentsPlus.java bereits fehlerfrei.
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(EnchantmentsPlusConfig.class, parent).get();
    }
}
