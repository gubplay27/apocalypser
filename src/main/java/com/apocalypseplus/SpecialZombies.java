package com.apocalypseplus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Convierte algunos zombis normales en variantes especiales usando solo cosas de vanilla
 * (atributos, equipo en la cabeza y efectos), así no hace falta registrar entidades nuevas.
 *
 *  - Corredor:  muy rápido, poca vida.
 *  - Gigante:   mucha vida, golpea fuerte, casi no retrocede. Lleva armadura de hierro.
 *  - Explosivo: lleva un bloque de TNT en la cabeza y explota al morir.
 *  - Ácido:     lleva un bloque de slime, su golpe envenena y al morir deja una nube de veneno.
 */
@Mod.EventBusSubscriber(modid = ApocalypsePlus.MODID)
public class SpecialZombies {

    public static final String TAG = "apoc_type";
    public static final String NORMAL = "normal";
    public static final String RUNNER = "runner";
    public static final String GIANT = "giant";
    public static final String EXPLOSIVE = "explosive";
    public static final String ACID = "acid";

    private static final UUID RUNNER_SPEED = UUID.fromString("7f1c2b3a-1111-4a5b-8c9d-0e1f2a3b4c01");
    private static final UUID RUNNER_HEALTH = UUID.fromString("7f1c2b3a-1111-4a5b-8c9d-0e1f2a3b4c02");
    private static final UUID GIANT_HEALTH = UUID.fromString("7f1c2b3a-1111-4a5b-8c9d-0e1f2a3b4c03");
    private static final UUID GIANT_DAMAGE = UUID.fromString("7f1c2b3a-1111-4a5b-8c9d-0e1f2a3b4c04");
    private static final UUID GIANT_KNOCKBACK = UUID.fromString("7f1c2b3a-1111-4a5b-8c9d-0e1f2a3b4c05");
    private static final UUID GIANT_SPEED = UUID.fromString("7f1c2b3a-1111-4a5b-8c9d-0e1f2a3b4c06");

    // ------------------------------------------------------------------
    // Asignar tipo al aparecer
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Zombie)) return;
        Zombie zombie = (Zombie) event.getEntity();

        // Los goals no se guardan en el mundo, así que se agregan cada vez que el zombi entra.
        if (Config.NOISE_ENABLED.get()) {
            zombie.goalSelector.addGoal(5, new InvestigateNoiseGoal(zombie));
        }

        // Solo zombis normales (no husks, ahogados ni aldeanos zombi) y que aún no tengan tipo.
        if (zombie.getType() != EntityType.ZOMBIE) return;
        CompoundTag data = zombie.getPersistentData();
        if (data.contains(TAG)) return;

        double factor = Mth.lerp(HordeHandler.progress(event.getLevel()), Config.SPECIAL_DAY1_FACTOR.get(), 1.0D);
        String type = zombie.isBaby() ? NORMAL : roll(zombie.getRandom(), factor);
        data.putString(TAG, type);
        apply(zombie, type);
    }

    private static String roll(RandomSource random, double factor) {
        double r = random.nextDouble();
        double acc = Config.GIANT_CHANCE.get() * factor;
        if (r < acc) return GIANT;
        acc += Config.EXPLOSIVE_CHANCE.get() * factor;
        if (r < acc) return EXPLOSIVE;
        acc += Config.ACID_CHANCE.get() * factor;
        if (r < acc) return ACID;
        acc += Config.RUNNER_CHANCE.get() * factor;
        if (r < acc) return RUNNER;
        return NORMAL;
    }

    private static void apply(Zombie zombie, String type) {
        switch (type) {
            case RUNNER -> {
                addMod(zombie, Attributes.MOVEMENT_SPEED, RUNNER_SPEED, "apoc_runner_speed", 0.55D, AttributeModifier.Operation.MULTIPLY_BASE);
                addMod(zombie, Attributes.MAX_HEALTH, RUNNER_HEALTH, "apoc_runner_health", -8.0D, AttributeModifier.Operation.ADDITION);
                zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                zombie.setDropChance(EquipmentSlot.FEET, 0.0F);
                name(zombie, "Corredor");
            }
            case GIANT -> {
                addMod(zombie, Attributes.MAX_HEALTH, GIANT_HEALTH, "apoc_giant_health", 40.0D, AttributeModifier.Operation.ADDITION);
                addMod(zombie, Attributes.ATTACK_DAMAGE, GIANT_DAMAGE, "apoc_giant_damage", 4.0D, AttributeModifier.Operation.ADDITION);
                addMod(zombie, Attributes.KNOCKBACK_RESISTANCE, GIANT_KNOCKBACK, "apoc_giant_knockback", 0.7D, AttributeModifier.Operation.ADDITION);
                addMod(zombie, Attributes.MOVEMENT_SPEED, GIANT_SPEED, "apoc_giant_speed", -0.15D, AttributeModifier.Operation.MULTIPLY_BASE);
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
                zombie.setDropChance(EquipmentSlot.CHEST, 0.0F);
                name(zombie, "Gigante");
            }
            case EXPLOSIVE -> {
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
                zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
                name(zombie, "Explosivo");
            }
            case ACID -> {
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SLIME_BLOCK));
                zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
                name(zombie, "Ácido");
            }
            default -> {
            }
        }
        zombie.setHealth(zombie.getMaxHealth());
    }

    private static void addMod(Zombie zombie, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = zombie.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) == null) {
            instance.addPermanentModifier(new AttributeModifier(id, name, amount, op));
        }
    }

    /** El nombre solo se ve cuando apuntas al zombi con la mira. */
    private static void name(Zombie zombie, String label) {
        zombie.setCustomName(Component.literal(label));
        zombie.setCustomNameVisible(false);
    }

    // ------------------------------------------------------------------
    // Efectos al morir / golpear / soltar objetos
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        Zombie zombie = (Zombie) event.getEntity();
        if (!(zombie.level() instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) zombie.level();
        String type = zombie.getPersistentData().getString(TAG);

        if (EXPLOSIVE.equals(type)) {
            Level.ExplosionInteraction mode = Config.EXPLOSIVE_BREAKS_BLOCKS.get()
                    ? Level.ExplosionInteraction.MOB
                    : Level.ExplosionInteraction.NONE;
            level.explode(null, zombie.getX(), zombie.getY() + 1.0D, zombie.getZ(), 2.5F, mode);
        } else if (ACID.equals(type)) {
            AreaEffectCloud cloud = new AreaEffectCloud(level, zombie.getX(), zombie.getY(), zombie.getZ());
            cloud.setRadius(2.5F);
            cloud.setDuration(120);
            cloud.setRadiusPerTick(-0.01F);
            cloud.setFixedColor(0x66CC33);
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            level.addFreshEntity(cloud);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Zombie)) return;
        if (!ACID.equals(attacker.getPersistentData().getString(TAG))) return;
        if (event.getEntity() instanceof Player) {
            ((Player) event.getEntity()).addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        Zombie zombie = (Zombie) event.getEntity();
        if (!(zombie.level() instanceof ServerLevel)) return;
        String type = zombie.getPersistentData().getString(TAG);
        RandomSource r = zombie.getRandom();

        ItemStack extra = ItemStack.EMPTY;
        switch (type) {
            case RUNNER -> extra = new ItemStack(Items.LEATHER, 1 + r.nextInt(2));
            case GIANT -> extra = new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(3));
            case EXPLOSIVE -> extra = new ItemStack(Items.GUNPOWDER, 1 + r.nextInt(3));
            case ACID -> extra = new ItemStack(Items.SLIME_BALL, 1 + r.nextInt(2));
            default -> {
            }
        }
        if (!extra.isEmpty()) {
            event.getDrops().add(new ItemEntity(zombie.level(), zombie.getX(), zombie.getY(), zombie.getZ(), extra));
        }
        if (GIANT.equals(type) && r.nextFloat() < 0.15F) {
            event.getDrops().add(new ItemEntity(zombie.level(), zombie.getX(), zombie.getY(), zombie.getZ(),
                    new ItemStack(Items.GOLDEN_APPLE)));
        }
    }
}
