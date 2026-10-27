package com.apocalypseplus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Hace que un zombi sin objetivo camine hacia el último ruido que oyó.
 * El ruido lo guarda NoiseHandler en los datos persistentes del zombi.
 */
public class InvestigateNoiseGoal extends Goal {

    private final Zombie mob;
    private Vec3 target;
    private int repathTimer;

    public InvestigateNoiseGoal(Zombie mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(NoiseHandler.KEY_TIME)) return false;
        if (mob.level().getGameTime() > data.getLong(NoiseHandler.KEY_TIME)) {
            clearNoise();
            return false;
        }
        target = new Vec3(data.getDouble(NoiseHandler.KEY_X), data.getDouble(NoiseHandler.KEY_Y), data.getDouble(NoiseHandler.KEY_Z));
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() != null || target == null) return false;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(NoiseHandler.KEY_TIME)) return false;
        if (mob.level().getGameTime() > data.getLong(NoiseHandler.KEY_TIME)) return false;
        return mob.position().distanceToSqr(target) > 9.0D;
    }

    @Override
    public void start() {
        repathTimer = 0;
    }

    @Override
    public void tick() {
        if (--repathTimer > 0) return;
        repathTimer = 20;

        // El pathfinding de un zombi tiene alcance limitado: si el ruido está lejos, se avanza por tramos.
        Vec3 to = target.subtract(mob.position());
        double dist = to.length();
        double tx;
        double ty;
        double tz;
        if (dist > 24.0D) {
            Vec3 step = mob.position().add(to.scale(24.0D / dist));
            tx = step.x;
            tz = step.z;
            ty = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(tx), Mth.floor(tz));
        } else {
            tx = target.x;
            ty = target.y;
            tz = target.z;
        }
        mob.getNavigation().moveTo(tx, ty, tz, 1.0D);
    }

    @Override
    public void stop() {
        if (target != null && mob.position().distanceToSqr(target) <= 9.0D) {
            clearNoise();
        }
        mob.getNavigation().stop();
    }

    private void clearNoise() {
        CompoundTag data = mob.getPersistentData();
        data.remove(NoiseHandler.KEY_TIME);
        data.remove(NoiseHandler.KEY_X);
        data.remove(NoiseHandler.KEY_Y);
        data.remove(NoiseHandler.KEY_Z);
    }
}
