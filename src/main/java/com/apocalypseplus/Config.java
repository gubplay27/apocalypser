package com.apocalypseplus;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Configuración común. Se genera en config/apocalypse_plus-common.toml
 */
public class Config {
    public static final ForgeConfigSpec SPEC;

    // ---- Hordas ----
    public static final ForgeConfigSpec.BooleanValue SHOW_DAY_COUNTER;
    public static final ForgeConfigSpec.IntValue BASE_ZOMBIES;
    public static final ForgeConfigSpec.IntValue DAYS_TO_MAX;
    public static final ForgeConfigSpec.IntValue MAX_ZOMBIES;
    public static final ForgeConfigSpec.IntValue SPAWN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue HORDE_EVERY_DAYS;
    public static final ForgeConfigSpec.DoubleValue HORDE_MULTIPLIER;

    // ---- Zombis especiales ----
    public static final ForgeConfigSpec.DoubleValue GIANT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue EXPLOSIVE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue ACID_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RUNNER_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SPECIAL_DAY1_FACTOR;
    public static final ForgeConfigSpec.BooleanValue EXPLOSIVE_BREAKS_BLOCKS;

    // ---- Ruido ----
    public static final ForgeConfigSpec.BooleanValue NOISE_ENABLED;
    public static final ForgeConfigSpec.IntValue GUN_RADIUS;
    public static final ForgeConfigSpec.IntValue BREAK_RADIUS;
    public static final ForgeConfigSpec.IntValue DOOR_RADIUS;
    public static final ForgeConfigSpec.IntValue EXPLOSION_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_SOUND_KEYWORDS;

    // ---- Eventos aleatorios ----
    public static final ForgeConfigSpec.BooleanValue EVENTS_ENABLED;
    public static final ForgeConfigSpec.IntValue EVENT_MIN_MINUTES;
    public static final ForgeConfigSpec.IntValue EVENT_MAX_MINUTES;
    public static final ForgeConfigSpec.IntValue STORM_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SUPPLY_LOOT_TABLES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Hordas que crecen con los días").push("hordas");
        SHOW_DAY_COUNTER = b.comment("Mostrar 'Día N del apocalipsis' al amanecer")
                .define("mostrarContadorDeDias", true);
        DAYS_TO_MAX = b.comment("Día en el que las hordas llegan a su dificultad MÁXIMA. Antes de ese día suben un poco cada día.")
                .defineInRange("diaDificultadMaxima", 7, 2, 100);
        BASE_ZOMBIES = b.comment("Zombis máximos cerca de un jugador el día 1")
                .defineInRange("zombisBase", 4, 0, 200);
        MAX_ZOMBIES = b.comment("Zombis máximos cerca de un jugador desde el día de dificultad máxima (en noche de horda el tope se duplica)")
                .defineInRange("maxZombis", 30, 1, 400);
        SPAWN_INTERVAL_TICKS = b.comment("Cada cuántos ticks se intenta aparecer zombis de noche (20 ticks = 1 s)")
                .defineInRange("intervaloSpawnTicks", 100, 20, 1200);
        HORDE_EVERY_DAYS = b.comment("Cada cuántos días hay noche de horda grande (0 = nunca)")
                .defineInRange("hordaCadaDias", 7, 0, 100);
        HORDE_MULTIPLIER = b.comment("Multiplicador de zombis en noche de horda")
                .defineInRange("multiplicadorHorda", 2.5D, 1.0D, 20.0D);
        b.pop();

        b.comment("Zombis especiales (probabilidad entre 0.0 y 1.0, la suma no debe pasar de 1.0)").push("especiales");
        SPECIAL_DAY1_FACTOR = b.comment("Qué fracción de las probabilidades de abajo se usa el día 1 (0.3 = 30%). Sube hasta 100% en el día de dificultad máxima.")
                .defineInRange("factorEspecialesDia1", 0.3D, 0.0D, 1.0D);
        GIANT_CHANCE = b.defineInRange("probGigante", 0.04D, 0.0D, 1.0D);
        EXPLOSIVE_CHANCE = b.defineInRange("probExplosivo", 0.06D, 0.0D, 1.0D);
        ACID_CHANCE = b.defineInRange("probAcido", 0.06D, 0.0D, 1.0D);
        RUNNER_CHANCE = b.defineInRange("probCorredor", 0.12D, 0.0D, 1.0D);
        EXPLOSIVE_BREAKS_BLOCKS = b.comment("Si el zombi explosivo rompe bloques al morir (respeta mobGriefing)")
                .define("explosivoRompeBloques", false);
        b.pop();

        b.comment("Ruido que atrae zombis").push("ruido");
        NOISE_ENABLED = b.define("ruidoActivado", true);
        GUN_RADIUS = b.comment("Radio (bloques) en el que se oye un disparo")
                .defineInRange("radioDisparo", 48, 1, 256);
        BREAK_RADIUS = b.comment("Radio al romper bloques").defineInRange("radioRomperBloque", 14, 1, 256);
        DOOR_RADIUS = b.comment("Radio al abrir puertas, trampillas, portones, cofres y barriles")
                .defineInRange("radioPuertas", 12, 1, 256);
        EXPLOSION_RADIUS = b.comment("Radio de una explosión").defineInRange("radioExplosion", 64, 1, 256);
        GUN_SOUND_KEYWORDS = b.comment(
                        "Un sonido de OTRO mod (no vanilla) cuenta como disparo si su nombre contiene alguna de estas palabras.",
                        "Si tu mod de armas no atrae zombis, mira el nombre de sus sonidos y agrega una palabra aquí.")
                .defineListAllowEmpty("palabrasDeDisparo",
                        List.of("shoot", "shot", "gun", "rifle", "pistol", "fire", "bullet", "blast"),
                        o -> o instanceof String);
        b.pop();

        b.comment("Eventos aleatorios por jugador").push("eventos");
        EVENTS_ENABLED = b.define("eventosActivados", true);
        EVENT_MIN_MINUTES = b.comment("Mínimo de minutos reales entre eventos")
                .defineInRange("minutosMinimos", 8, 1, 600);
        EVENT_MAX_MINUTES = b.comment("Máximo de minutos reales entre eventos")
                .defineInRange("minutosMaximos", 20, 1, 600);
        STORM_MINUTES = b.comment("Duración de la tormenta de niebla (minutos)")
                .defineInRange("minutosTormenta", 4, 1, 60);
        SUPPLY_LOOT_TABLES = b.comment(
                        "Loot tables posibles para la caída de suministros.",
                        "Puedes agregar tablas de otros mods (por ejemplo las de tus mods de armas).")
                .defineListAllowEmpty("tablasSuministros",
                        List.of("minecraft:chests/village/village_weaponsmith",
                                "minecraft:chests/village/village_armorer",
                                "minecraft:chests/village/village_butcher",
                                "minecraft:chests/village/village_toolsmith"),
                        o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }
}
