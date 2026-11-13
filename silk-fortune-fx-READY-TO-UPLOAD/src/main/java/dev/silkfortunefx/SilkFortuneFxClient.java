package dev.silkfortunefx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Client-only visual indicator for Silk Touch vs Fortune.
 *
 *  - Inventory / hotbar / chests: an animated frame drawn in the 1px border AROUND the item (never over the
 *    item's own pixels, so item textures and resource packs such as Enchantment Outlines are untouched).
 *      Silk Touch -> cyan light sweeping around the slot.
 *      Fortune    -> pulsing gold frame with twinkling white-gold corners.
 *  - In the world: particles drift off the hand of any player holding such an item.
 *      Silk Touch -> blue soul-flame wisps.   Fortune -> gold sparkles.
 *
 * Uses no mixins, no custom names, and no shared state, so it coexists with the Enchant Tag mod.
 */
public class SilkFortuneFxClient implements ClientModInitializer {

    enum Kind { SILK, FORTUNE }

    record Info(Kind kind, int level) {}

    @Override
    public void onInitializeClient() {
        // Called for every item drawn in a slot (inventory, hotbar, chests, ...).
        ExtractItemDecorationsCallback.EVENT.register(
                (graphics, font, stack, x, y) -> drawSlotFrame(graphics, stack, x, y));

        ClientTickEvents.END_CLIENT_TICK.register(SilkFortuneFxClient::spawnHandParticles);
    }

    // ------------------------------------------------------------------ detection

    static Info info(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Info found = scan(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        if (found == null) {
            // enchanted books
            found = scan(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
        }
        return found;
    }

    private static Info scan(ItemEnchantments enchants) {
        if (enchants.isEmpty()) return null;
        for (Holder<Enchantment> holder : enchants.keySet()) {
            if (holder.is(Enchantments.SILK_TOUCH)) return new Info(Kind.SILK, enchants.getLevel(holder));
        }
        for (Holder<Enchantment> holder : enchants.keySet()) {
            if (holder.is(Enchantments.FORTUNE)) return new Info(Kind.FORTUNE, enchants.getLevel(holder));
        }
        return null;
    }

    // ------------------------------------------------------------------ slot frame

    /** The frame is an 18x18 ring: the 16x16 item plus the 1px slot border around it. */
    private static final int SIZE = 18;
    private static final int SIDE = SIZE - 1;       // 17
    private static final int RING = 4 * SIDE;       // 68 pixels around the frame

    private static void drawSlotFrame(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        Info info = info(stack);
        if (info == null) return;

        long t = System.currentTimeMillis();
        int x0 = x - 1;
        int y0 = y - 1;

        if (info.kind() == Kind.SILK) {
            drawSilk(g, x0, y0, t);
        } else {
            drawFortune(g, x0, y0, t, info.level());
        }
    }

    private static int ringX(int p) {
        p = Math.floorMod(p, RING);
        if (p < SIDE) return p;
        if (p < 2 * SIDE) return SIDE;
        if (p < 3 * SIDE) return SIDE - (p - 2 * SIDE);
        return 0;
    }

    private static int ringY(int p) {
        p = Math.floorMod(p, RING);
        if (p < SIDE) return 0;
        if (p < 2 * SIDE) return p - SIDE;
        if (p < 3 * SIDE) return SIDE;
        return SIDE - (p - 3 * SIDE);
    }

    private static void pixel(GuiGraphicsExtractor g, int x0, int y0, int p, int argb) {
        int px = x0 + ringX(p);
        int py = y0 + ringY(p);
        g.fill(px, py, px + 1, py + 1, argb);
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Silk Touch: dim cyan ring with two bright comets chasing around it. */
    private static void drawSilk(GuiGraphicsExtractor g, int x0, int y0, long t) {
        int head = (int) ((t / 22L) % RING);
        for (int p = 0; p < RING; p++) {
            int d1 = Math.floorMod(head - p, RING);
            int d2 = Math.floorMod(head + RING / 2 - p, RING);
            int d = Math.min(d1, d2);

            int alpha = 90;            // always-visible base ring
            int rgb = 0x2FD8FF;        // cyan
            if (d < 16) {
                alpha = Math.max(alpha, 255 - d * 15);
                if (d < 3) rgb = 0xE8FFFF; // near-white comet head
            }
            pixel(g, x0, y0, p, argb(alpha, rgb));
        }
    }

    /** Fortune: breathing gold ring, white-gold twinkles on the four corners. */
    private static void drawFortune(GuiGraphicsExtractor g, int x0, int y0, long t, int level) {
        double speed = 1.0 + 0.15 * Math.min(level, 5);
        double pulse = 0.5 + 0.5 * Math.sin(t / 170.0 * speed);
        int ringAlpha = (int) (120 + 120 * pulse);

        for (int p = 0; p < RING; p++) {
            pixel(g, x0, y0, p, argb(ringAlpha, 0xFFC02E));
        }

        // Corners are ring indices 0, SIDE, 2*SIDE, 3*SIDE. Light a 3-pixel L along the ring at each.
        for (int i = 0; i < 4; i++) {
            long phase = (t / 120L + i * 3L) % 10L;
            if (phase < 4) {
                int bright = (int) (255 - phase * 40);
                int corner = i * SIDE;
                pixel(g, x0, y0, corner - 1, argb(bright, 0xFFF3B0));
                pixel(g, x0, y0, corner, argb(bright, 0xFFFFFF));
                pixel(g, x0, y0, corner + 1, argb(bright, 0xFFF3B0));
            }
        }
    }

    // ------------------------------------------------------------------ held-item particles

    private static void spawnHandParticles(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return;

        for (AbstractClientPlayer player : level.players()) {
            if (player.isInvisible() || player.isSpectator()) continue;
            spawn(level, player, player.getMainHandItem(), player.getMainArm());
            spawn(level, player, player.getOffhandItem(), player.getMainArm().getOpposite());
        }
    }

    private static void spawn(ClientLevel level, AbstractClientPlayer player, ItemStack stack, HumanoidArm arm) {
        Info info = info(stack);
        if (info == null) return;

        RandomSource random = level.getRandom();
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
        if (random.nextInt(moving ? 2 : 5) != 0) return;

        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;

        double x = player.getX() + rx * 0.38 * side + fx * 0.28 + (random.nextDouble() - 0.5) * 0.2;
        double y = player.getY() + 0.95 + (random.nextDouble() - 0.5) * 0.25;
        double z = player.getZ() + rz * 0.38 * side + fz * 0.28 + (random.nextDouble() - 0.5) * 0.2;

        if (info.kind() == Kind.SILK) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0, 0.02, 0.0);
        } else {
            level.addParticle(ParticleTypes.WAX_ON, x, y, z, 0.0, 0.03, 0.0);
        }
    }
}
