package com.apocalypseplus;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuración del cliente. Se genera en config/apocalypse_plus-client.toml
 */
public class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue FOG_ENABLED;
    public static final ForgeConfigSpec.IntValue FOG_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOG_NEAR_RATIO;
    public static final ForgeConfigSpec.DoubleValue FOG_DESATURATION;
    public static final ForgeConfigSpec.DoubleValue FOG_BRIGHTNESS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("niebla");
        FOG_ENABLED = b.comment("Niebla constante en el Overworld")
                .define("nieblaActivada", true);
        FOG_DISTANCE = b.comment(
                        "Distancia (bloques) donde la niebla tapa todo.",
                        "Siempre se limita a ~85% de tu distancia de renderizado para que no se vea el borde de los chunks.")
                .defineInRange("distanciaNiebla", 70, 16, 512);
        FOG_NEAR_RATIO = b.comment("Dónde empieza la niebla, como fracción de la distancia (0.0 = pegada a ti)")
                .defineInRange("inicioNiebla", 0.1D, 0.0D, 0.9D);
        FOG_DESATURATION = b.comment("Qué tan gris es la niebla (0.0 = color normal, 1.0 = gris total)")
                .defineInRange("grisNiebla", 0.6D, 0.0D, 1.0D);
        FOG_BRIGHTNESS = b.comment("Brillo de la niebla (1.0 = normal, menos = más oscura)")
                .defineInRange("brilloNiebla", 0.9D, 0.2D, 1.0D);
        b.pop();
        SPEC = b.build();
    }
}
