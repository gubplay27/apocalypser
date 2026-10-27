package com.apocalypseplus;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Los zombis oyen: disparos (por el nombre del sonido), romper bloques, abrir puertas/cofres y explosiones.
 * Los zombis dentro del radio caminan hacia el ruido; los más cercanos a un jugador lo toman de objetivo.
 */
@Mod.EventBusSubscriber(modid = ApocalypsePlus.MODID)
public class NoiseHandler {

    public static final String KEY_X = "apoc_noise_x";
    public static final String KEY_Y = "apoc_noise_y";
    public static final String KEY_Z = "apoc_noise_z";
    public static final String KEY_TIME = "apoc_noise_t";

    /** Cuánto "recuerda" un zombi un ruido (ticks). 600 = 30 segundos. */
    private static final int MEMORY_TICKS = 600;

    private static long lastSoundNoise = -1000;
    private static final Map<UUID, Long> lastPlayerNoise = new HashMap<>();

    public static void reset() {
        lastSoundNoise = -1000;
        lastPlayerNoise.clear();
    }

    // ------------------------------------------------------------------
    // Núcleo
    // ------------------------------------------------------------------
    public static void makeNoise(ServerLevel level, Vec3 pos, double radius) {
        AABB box = new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        double r2 = radius * radius;
        double near = radius * 0.3D;
        double near2 = near * near;
        long expire = level.getGameTime() + MEMORY_TICKS;
        Player nearest = level.getNearestPlayer(pos.x, pos.y, pos.z, near, false);

        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, box, Zombie::isAlive)) {
            double d2 = zombie.distanceToSqr(pos);
            if (d2 > r2) continue;

            CompoundTag data = zombie.getPersistentData();
            data.putDouble(KEY_X, pos.x);
            data.putDouble(KEY_Y, pos.y);
            data.putDouble(KEY_Z, pos.z);
            data.putLong(KEY_TIME, expire);

            if (nearest != null && d2 <= near2 && zombie.getTarget() == null) {
                zombie.setTarget(nearest);
            }
        }
    }

    private static boolean onCooldown(Player player, long now) {
        Long last = lastPlayerNoise.get(player.getUUID());
        if (last != null && now >= last && now - last < 10) return true;
        lastPlayerNoise.put(player.getUUID(), now);
        return false;
    }

    // ------------------------------------------------------------------
    // Romper bloques
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!Config.NOISE_ENABLED.get()) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) event.getLevel();
        if (onCooldown(event.getPlayer(), level.getGameTime())) return;
        makeNoise(level, Vec3.atCenterOf(event.getPos()), Config.BREAK_RADIUS.get());
    }

    // ------------------------------------------------------------------
    // Puertas, trampillas, portones, cofres y barriles
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.NOISE_ENABLED.get()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) event.getLevel();

        Block block = level.getBlockState(event.getPos()).getBlock();
        boolean loud = block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock || block instanceof ChestBlock || block instanceof BarrelBlock;
        if (!loud) return;
        if (onCooldown(event.getEntity(), level.getGameTime())) return;
        makeNoise(level, Vec3.atCenterOf(event.getPos()), Config.DOOR_RADIUS.get());
    }

    // ------------------------------------------------------------------
    // Explosiones
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!Config.NOISE_ENABLED.get()) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        makeNoise((ServerLevel) event.getLevel(), event.getExplosion().getPosition(), Config.EXPLOSION_RADIUS.get());
    }

    // ------------------------------------------------------------------
    // Disparos (sonidos de otros mods)
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        handleSound((ServerLevel) entity.level(), entity.position(), event.getSound());
    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        Vec3 pos = event.getPosition();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // Solo cuentan sonidos hechos a menos de 6 bloques de un jugador (un arma, no un mob lejano).
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getNearestPlayer(pos.x, pos.y, pos.z, 6.0D, false) != null) {
                handleSound(level, pos, event.getSound());
                return;
            }
        }
    }

    private static void handleSound(ServerLevel level, Vec3 pos, Object sound) {
        if (!Config.NOISE_ENABLED.get()) return;

        String id = soundName(sound);
        if (id.isEmpty()) return;
        if (id.startsWith("minecraft:")) return; // solo sonidos de otros mods

        String lower = id.toLowerCase(Locale.ROOT);
        boolean match = false;
        for (String keyword : Config.GUN_SOUND_KEYWORDS.get()) {
            if (!keyword.isEmpty() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                match = true;
                break;
            }
        }
        if (!match) return;

        long now = level.getGameTime();
        if (now >= lastSoundNoise && now - lastSoundNoise < 5) return;
        lastSoundNoise = now;
        makeNoise(level, pos, Config.GUN_RADIUS.get());
    }

    /** Acepta Holder<SoundEvent> o SoundEvent para no depender de la firma exacta del evento. */
    private static String soundName(Object sound) {
        if (sound instanceof Holder<?>) {
            return ((Holder<?>) sound).unwrapKey().map(key -> key.location().toString()).orElse("");
        }
        if (sound instanceof SoundEvent) {
            return ((SoundEvent) sound).getLocation().toString();
        }
        return "";
    }
}
