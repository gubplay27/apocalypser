package com.apocalypseplus;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Niebla constante en el Overworld.
 *
 * Para que NO se vea el borde de los chunks, la niebla termina siempre antes del límite de la distancia
 * de renderizado (85%), y usa forma de esfera en vez de cilindro. Así los chunks aparecen "dentro" de la niebla.
 * Con tormenta la niebla se cierra más.
 *
 * Solo cliente. Si usas shaders (Oculus/Iris), el shader controla la niebla y esto no tendrá efecto.
 */
@Mod.EventBusSubscriber(modid = ApocalypsePlus.MODID, value = Dist.CLIENT)
public class FogHandler {

    private static boolean shouldApply(net.minecraft.client.Camera camera) {
        if (!ClientConfig.FOG_ENABLED.get()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        if (mc.level.dimension() != Level.OVERWORLD) return false;
        if (camera.getFluidInCamera() != FogType.NONE) return false;      // agua, lava, nieve en polvo
        return !mc.player.hasEffect(MobEffects.BLINDNESS);                  // respeta la ceguera
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        if (!shouldApply(event.getCamera())) return;

        Minecraft mc = Minecraft.getInstance();
        float renderBlocks = mc.options.getEffectiveRenderDistance() * 16.0F;

        float far = Math.min(ClientConfig.FOG_DISTANCE.get().floatValue(), renderBlocks * 0.85F);
        float rain = mc.level.getRainLevel(1.0F);
        far *= 1.0F - 0.45F * rain; // más cerrada durante la tormenta
        far = Math.max(far, 12.0F);

        event.setNearPlaneDistance(far * ClientConfig.FOG_NEAR_RATIO.get().floatValue());
        event.setFarPlaneDistance(far);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // necesario para que se apliquen los valores
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!shouldApply(event.getCamera())) return;

        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();
        float gray = (r + g + b) / 3.0F;
        float k = ClientConfig.FOG_DESATURATION.get().floatValue();
        float brightness = ClientConfig.FOG_BRIGHTNESS.get().floatValue();

        // Se parte del color que ya trae el juego (oscuro de noche, claro de día) y se vuelve gris.
        event.setRed((r + (gray - r) * k) * brightness);
        event.setGreen((g + (gray - g) * k) * brightness);
        event.setBlue((b + (gray - b) * k) * brightness);
    }
}
