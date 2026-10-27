package com.apocalypseplus;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Contador de días + hordas que crecen con el tiempo.
 * Cada noche aparecen más zombis cerca de cada jugador; cada N días hay noche de horda grande.
 */
@Mod.EventBusSubscriber(modid = ApocalypsePlus.MODID)
public class HordeHandler {

    private static long lastDayShown = -1;
    private static long lastHordeWarning = -1;

    /** Progreso de dificultad: 0.0 el día 1, sube parejo cada día y llega a 1.0 en el día de dificultad máxima. */
    public static double progress(Level level) {
        int daysToMax = Math.max(2, Config.DAYS_TO_MAX.get());
        long dayIndex = level.getDayTime() / 24000L; // día 1 = 0
        return Mth.clamp((double) dayIndex / (daysToMax - 1), 0.0D, 1.0D);
    }

    public static boolean isHordeNight(int dayNumber) {
        int every = Config.HORDE_EVERY_DAYS.get();
        return every > 0 && dayNumber % every == 0;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.level instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) event.level;
        if (level.dimension() != Level.OVERWORLD) return;

        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;
        int dayNumber = (int) (day + 1);
        boolean hordeNight = isHordeNight(dayNumber);

        // --- Contador de días y aviso de horda (revisión cada segundo) ---
        if (gameTime % 20 == 0) {
            if (Config.SHOW_DAY_COUNTER.get() && timeOfDay < 3000 && day != lastDayShown) {
                lastDayShown = day;
                for (ServerPlayer p : level.players()) {
                    int daysToMax = Math.max(2, Config.DAYS_TO_MAX.get());
                    int danger = (int) Math.round(100.0D * Math.min(dayNumber, daysToMax) / daysToMax);
                    String text = "Día " + dayNumber + " del apocalipsis - Peligro " + danger + "%"
                            + (dayNumber >= daysToMax ? " (MÁXIMO)" : "");
                    p.displayClientMessage(Component.literal(text), true);
                }
            }
            if (hordeNight && timeOfDay >= 12500 && timeOfDay < 14000 && day != lastHordeWarning) {
                lastHordeWarning = day;
                for (ServerPlayer p : level.players()) {
                    p.sendSystemMessage(Component.literal("¡Noche de horda! Se acerca una multitud de infectados...")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }
            }
        }

        // --- Aparición de zombis (solo de noche) ---
        double p = progress(level);
        // Los intentos de aparición se hacen más seguidos a medida que pasan los días (1.5x más lento el día 1, 2x más rápido en el máximo).
        int interval = Math.max(20, (int) Mth.lerp(p, Config.SPAWN_INTERVAL_TICKS.get() * 1.5D, Config.SPAWN_INTERVAL_TICKS.get() * 0.5D));
        if (gameTime % interval != 0) return;
        if (timeOfDay < 13000 || timeOfDay > 23000) return;

        double cap = Mth.lerp(p, Config.BASE_ZOMBIES.get(), Config.MAX_ZOMBIES.get());
        if (hordeNight) cap *= Config.HORDE_MULTIPLIER.get();
        if (level.isThundering()) cap *= 1.5D; // la tormenta de niebla trae más infectados
        int max = (int) Math.min(cap, Config.MAX_ZOMBIES.get() * (hordeNight ? 2 : 1));

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;

            int nearby = level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(48.0D), Zombie::isAlive).size();
            if (nearby >= max) continue;

            int baseBatch = 1 + (int) Math.round(p * 2.0D); // 1 zombi por tanda el día 1, 3 en el máximo
            int batch = Math.min(baseBatch + (hordeNight ? 2 : 0), max - nearby);
            for (int i = 0; i < batch; i++) {
                BlockPos pos = findSurface(level, player.position(), 24.0D, 40.0D);
                if (pos != null) spawnZombie(level, pos, player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastDayShown = -1;
        lastHordeWarning = -1;
        NoiseHandler.reset();
        RandomEvents.reset();
    }

    // ------------------------------------------------------------------
    // Utilidades compartidas con RandomEvents
    // ------------------------------------------------------------------

    /** Busca un punto de superficie sólido entre minDist y maxDist del centro. Devuelve null si no encuentra. */
    public static BlockPos findSurface(ServerLevel level, Vec3 center, double minDist, double maxDist) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + level.random.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(center.x + Math.cos(angle) * dist);
            int z = Mth.floor(center.z + Math.sin(angle) * dist);

            if (!level.hasChunkAt(new BlockPos(x, 64, z))) continue;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();

            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
            return pos;
        }
        return null;
    }

    /** Crea un zombi en la posición dada. El objetivo puede ser null. */
    public static Zombie spawnZombie(ServerLevel level, BlockPos pos, LivingEntity target) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) return null;
        zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        if (target != null) zombie.setTarget(target);
        level.addFreshEntity(zombie);
        return zombie;
    }
}
