package com.apocalypseplus;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Eventos aleatorios cada pocos minutos por jugador:
 *  - Caída de suministros: un cofre con botín custodiado por zombis.
 *  - Tormenta de niebla: tormenta + oleada inmediata (y más zombis mientras dura).
 *  - Superviviente: un aldeano herido rodeado de zombis; si lo alcanzas con vida, te da recompensa.
 */
@Mod.EventBusSubscriber(modid = ApocalypsePlus.MODID)
public class RandomEvents {

    public static final String SURVIVOR_TAG = "apoc_survivor";
    private static final int SURVIVOR_LIFETIME_TICKS = 12000; // 10 minutos

    private record Survivor(UUID id, long expires) {
    }

    private static final Map<UUID, Long> nextEvent = new HashMap<>();
    private static final List<Survivor> survivors = new ArrayList<>();

    public static void reset() {
        nextEvent.clear();
        survivors.clear();
    }

    // ------------------------------------------------------------------
    // Programación
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.level instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) event.level;
        if (level.dimension() != Level.OVERWORLD) return;

        long now = level.getGameTime();
        if (now % 100 != 0) return;

        cleanSurvivors(level, now);
        if (!Config.EVENTS_ENABLED.get()) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;

            UUID id = player.getUUID();
            Long next = nextEvent.get(id);
            if (next == null || next - now > 20L * 60L * 600L) { // primera vez (o dato viejo de otro mundo)
                nextEvent.put(id, now + randomDelay(level));
                continue;
            }
            if (now < next) continue;

            nextEvent.put(id, now + randomDelay(level));
            trigger(level, player);
        }
    }

    private static long randomDelay(ServerLevel level) {
        int min = Math.min(Config.EVENT_MIN_MINUTES.get(), Config.EVENT_MAX_MINUTES.get());
        int max = Math.max(Config.EVENT_MIN_MINUTES.get(), Config.EVENT_MAX_MINUTES.get());
        double minutes = min + level.random.nextDouble() * (max - min);
        return (long) (minutes * 60.0D * 20.0D);
    }

    private static void trigger(ServerLevel level, ServerPlayer player) {
        int roll = level.random.nextInt(100);
        if (roll < 40) {
            supplyDrop(level, player);
        } else if (roll < 70 && !level.isThundering()) {
            fogStorm(level, player);
        } else {
            survivor(level, player);
        }
    }

    // ------------------------------------------------------------------
    // Evento 1: caída de suministros
    // ------------------------------------------------------------------
    private static void supplyDrop(ServerLevel level, ServerPlayer player) {
        BlockPos spot = HordeHandler.findSurface(level, player.position(), 35.0D, 60.0D);
        if (spot == null) return;

        level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(spot);
        if (be instanceof RandomizableContainerBlockEntity) {
            ((RandomizableContainerBlockEntity) be).setLootTable(pickLootTable(level), level.random.nextLong());
        }

        spawnGuards(level, spot, 2 + (int) Math.round(HordeHandler.progress(level) * 3.0D) + level.random.nextInt(3), null);

        player.sendSystemMessage(Component.literal("Caída de suministros " + describe(player.position(), spot)
                + ". Cuidado: hay infectados custodiándola.").withStyle(ChatFormatting.GOLD));
    }

    private static ResourceLocation pickLootTable(ServerLevel level) {
        List<? extends String> tables = Config.SUPPLY_LOOT_TABLES.get();
        ResourceLocation fallback = new ResourceLocation("minecraft", "chests/village/village_weaponsmith");
        if (tables.isEmpty()) return fallback;
        ResourceLocation id = ResourceLocation.tryParse(tables.get(level.random.nextInt(tables.size())));
        return id != null ? id : fallback;
    }

    // ------------------------------------------------------------------
    // Evento 2: tormenta de niebla
    // ------------------------------------------------------------------
    private static void fogStorm(ServerLevel level, ServerPlayer player) {
        level.setWeatherParameters(0, Config.STORM_MINUTES.get() * 1200, true, true);

        int wave = 4 + (int) Math.round(HordeHandler.progress(level) * 6.0D) + level.random.nextInt(5);
        for (int i = 0; i < wave; i++) {
            BlockPos pos = HordeHandler.findSurface(level, player.position(), 20.0D, 35.0D);
            if (pos != null) HordeHandler.spawnZombie(level, pos, player);
        }

        player.sendSystemMessage(Component.literal("Se acerca una tormenta de niebla... los infectados avanzan hacia ti.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
    }

    // ------------------------------------------------------------------
    // Evento 3: superviviente que pide ayuda
    // ------------------------------------------------------------------
    private static void survivor(ServerLevel level, ServerPlayer player) {
        BlockPos spot = HordeHandler.findSurface(level, player.position(), 40.0D, 70.0D);
        if (spot == null) return;

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;
        villager.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.0F, 0.0F);
        villager.setCustomName(Component.literal("Superviviente"));
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.getPersistentData().putBoolean(SURVIVOR_TAG, true);
        level.addFreshEntity(villager);

        survivors.add(new Survivor(villager.getUUID(), level.getGameTime() + SURVIVOR_LIFETIME_TICKS));
        spawnGuards(level, spot, 3 + (int) Math.round(HordeHandler.progress(level) * 3.0D) + level.random.nextInt(3), villager);

        player.sendSystemMessage(Component.literal("Alguien pide ayuda " + describe(player.position(), spot)
                + ". Llega antes de que lo maten (clic derecho sobre él).").withStyle(ChatFormatting.AQUA));
    }

    private static void cleanSurvivors(ServerLevel level, long now) {
        Iterator<Survivor> it = survivors.iterator();
        while (it.hasNext()) {
            Survivor s = it.next();
            Entity e = level.getEntity(s.id());
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (now > s.expires()) {
                e.discard();
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) event.getLevel();
        Entity target = event.getTarget();
        if (!target.getPersistentData().getBoolean(SURVIVOR_TAG)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        reward(level, target, new ItemStack(Items.COOKED_BEEF, 4 + level.random.nextInt(5)));
        reward(level, target, new ItemStack(Items.IRON_INGOT, 2 + level.random.nextInt(4)));
        reward(level, target, new ItemStack(Items.ARROW, 8 + level.random.nextInt(9)));
        if (level.random.nextFloat() < 0.5F) reward(level, target, new ItemStack(Items.GOLDEN_APPLE));

        event.getEntity().sendSystemMessage(Component.literal("Superviviente: ¡Gracias por salvarme! Toma, es lo poco que tengo.")
                .withStyle(ChatFormatting.GREEN));

        UUID id = target.getUUID();
        survivors.removeIf(s -> s.id().equals(id));
        target.discard();
    }

    @SubscribeEvent
    public static void onSurvivorDeath(LivingDeathEvent event) {
        Entity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel)) return;
        if (!e.getPersistentData().getBoolean(SURVIVOR_TAG)) return;

        ServerLevel level = (ServerLevel) e.level();
        Player near = level.getNearestPlayer(e, 100.0D);
        if (near != null) {
            near.sendSystemMessage(Component.literal("El superviviente no lo logró...").withStyle(ChatFormatting.RED));
        }
    }

    private static void reward(ServerLevel level, Entity at, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, at.getX(), at.getY() + 0.5D, at.getZ(), stack));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------
    private static void spawnGuards(ServerLevel level, BlockPos center, int count, Entity target) {
        Vec3 c = Vec3.atCenterOf(center);
        for (int i = 0; i < count; i++) {
            BlockPos pos = HordeHandler.findSurface(level, c, 2.0D, 6.0D);
            if (pos == null) continue;
            Zombie z = HordeHandler.spawnZombie(level, pos, null);
            if (z != null && target instanceof net.minecraft.world.entity.LivingEntity) {
                z.setTarget((net.minecraft.world.entity.LivingEntity) target);
            }
        }
    }

    /** Texto tipo "a unos 45 bloques al noreste". En Minecraft +X es este y +Z es sur. */
    private static String describe(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5D - from.x;
        double dz = to.getZ() + 0.5D - from.z;
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String[] names = {"este", "sureste", "sur", "suroeste", "oeste", "noroeste", "norte", "noreste"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        int index = (int) Math.round(((angle + 360.0D) % 360.0D) / 45.0D) % 8;
        return "a unos " + dist + " bloques al " + names[index];
    }
}
