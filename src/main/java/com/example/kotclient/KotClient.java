package com.example.kotclient;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Mod("kotclient")
public class KotClient {

    // ===================== НАСТРОЙКИ =====================
    static final String CLIENT_NAME = "Kot Client";

    // цвета (ARGB)
    static final int BG      = 0xB8202230;  // фон панелей
    static final int HEADER  = 0xD02A2D42;  // заголовок панели
    static final int CELL    = 0x66000000;  // ячейки
    static final int ACCENT  = 0xFF8B7CFF;  // акцентный цвет
    static final int TEXT    = 0xFFFFFFFF;
    static final int DIM     = 0xFFA9ADC4;
    static final int OFF     = 0xFF6E7188;

    // позиции (в GUI-координатах)
    static final int WATERMARK_X = 4,  WATERMARK_Y = 4;
    static final int INVENTORY_X = 4,  INVENTORY_Y = 38;
    static final int PANEL_TOP_Y = 4;     // Cooldowns и Active potions
    // =====================================================

    // Модули HUD. Каждый можно включить/выключить клавишей (Настройки -> Управление -> Kot Client)
    enum Feature {
        WATERMARK("Watermark"),
        INVENTORY("Inventory"),
        HOTKEYS("Hotkeys"),
        POTIONS("Active potions"),
        COOLDOWNS("Cooldowns"),
        TARGET("Target HUD");

        final String title;
        boolean enabled = true;
        KeyBinding key;

        Feature(String title) { this.title = title; }
    }

    // Target HUD
    private LivingEntity target;
    private LivingEntity prevTarget;
    private long targetTime;
    private float hpAnim;

    // Cooldowns
    private Item[] allItems;

    public KotClient() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(FMLClientSetupEvent e) {
        for (Feature f : Feature.values()) {
            f.key = new KeyBinding("Kot Client: " + f.title, -1, CLIENT_NAME);   // -1 = не назначена
            ClientRegistry.registerKeyBinding(f.key);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        for (Feature f : Feature.values()) {
            if (f.key == null) continue;
            while (f.key.consumeClick()) f.enabled = !f.enabled;
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        MatrixStack ms = e.getMatrixStack();
        float pt = e.getPartialTicks();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        if (Feature.WATERMARK.enabled) drawWatermark(mc, ms);
        if (Feature.INVENTORY.enabled) drawInventory(mc, ms);
        if (Feature.HOTKEYS.enabled)   drawHotkeys(mc, ms, sw, sh);
        if (Feature.COOLDOWNS.enabled) drawCooldowns(mc, ms, sw, pt);
        if (Feature.POTIONS.enabled)   drawPotions(mc, ms, sw);
        if (Feature.TARGET.enabled)    drawTarget(mc, ms, sw, sh);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }

    // ===================== РИСОВАНИЕ =====================

    // прямоугольник со скруглёнными (ступенчато) углами
    private static void rrect(MatrixStack ms, int x, int y, int w, int h, int color) {
        AbstractGui.fill(ms, x + 2, y, x + w - 2, y + h, color);
        AbstractGui.fill(ms, x, y + 2, x + w, y + h - 2, color);
        AbstractGui.fill(ms, x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    private static void panel(MatrixStack ms, Minecraft mc, int x, int y, int w, int h, String title) {
        rrect(ms, x, y, w, h, BG);
        rrect(ms, x, y, w, 14, HEADER);
        AbstractGui.fill(ms, x + 3, y + 4, x + 5, y + 10, ACCENT);
        mc.font.drawShadow(ms, title, x + 9, y + 3, TEXT);
    }

    // плашка с текстом, возвращает её ширину
    private static int pill(MatrixStack ms, Minecraft mc, int x, int y, String text) {
        int w = mc.font.width(text) + 10;
        rrect(ms, x, y, w, 13, HEADER);
        mc.font.drawShadow(ms, text, x + 5, y + 3, TEXT);
        return w;
    }

    private void drawWatermark(Minecraft mc, MatrixStack ms) {
        int x = WATERMARK_X, y = WATERMARK_Y;

        // [K] Kot Client
        int w1 = mc.font.width(CLIENT_NAME) + 21;
        rrect(ms, x, y, w1, 13, HEADER);
        rrect(ms, x + 2, y + 2, 9, 9, ACCENT);
        mc.font.drawShadow(ms, "K", x + 5, y + 3, TEXT);
        mc.font.drawShadow(ms, CLIENT_NAME, x + 15, y + 3, TEXT);

        // ник
        pill(ms, mc, x + w1 + 3, y, mc.player.getName().getString());

        // ping + время
        NetworkPlayerInfo info = mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
        int ping = info != null ? info.getLatency() : 0;
        int w2 = pill(ms, mc, x, y + 16, ping + " Ping");
        pill(ms, mc, x + w2 + 3, y + 16, new SimpleDateFormat("HH:mm").format(new Date()));
    }

    private void drawInventory(Minecraft mc, MatrixStack ms) {
        int x = INVENTORY_X, y = INVENTORY_Y;
        int w = 9 * 18 + 6, h = 14 + 3 * 18 + 5;
        panel(ms, mc, x, y, w, h, "Inventory");

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int ix = x + 3 + c * 18, iy = y + 17 + r * 18;
                rrect(ms, ix, iy, 17, 17, CELL);
                ItemStack st = mc.player.inventory.getItem(9 + r * 9 + c);
                if (!st.isEmpty()) {
                    mc.getItemRenderer().renderAndDecorateItem(st, ix + 1, iy + 1);
                    mc.getItemRenderer().renderGuiItemDecorations(mc.font, st, ix + 1, iy + 1);
                }
            }
        }
        RenderSystem.enableBlend();
    }

    private void drawHotkeys(Minecraft mc, MatrixStack ms, int sw, int sh) {
        Feature[] fs = Feature.values();
        int w = 100;
        for (Feature f : fs) {
            w = Math.max(w, mc.font.width(f.title) + mc.font.width(keyName(f)) + 24);
        }
        int h = 14 + fs.length * 11 + 4;
        int x = sw - w - 4;
        int y = Math.max(60, sh / 2 - 60);
        panel(ms, mc, x, y, w, h, "Hotkeys");

        for (int i = 0; i < fs.length; i++) {
            Feature f = fs[i];
            int ry = y + 17 + i * 11;
            String k = keyName(f);
            mc.font.draw(ms, f.title, x + 6, ry, f.enabled ? TEXT : OFF);
            mc.font.draw(ms, k, x + w - 6 - mc.font.width(k), ry, f.enabled ? ACCENT : OFF);
        }
    }

    private static String keyName(Feature f) {
        if (f.key == null || f.key.isUnbound()) return "-";
        return f.key.getTranslatedKeyMessage().getString();
    }

    private void drawPotions(Minecraft mc, MatrixStack ms, int sw) {
        Collection<EffectInstance> effects = mc.player.getActiveEffects();
        int rows = effects.size();
        int w = 110;
        int h = 14 + (rows > 0 ? rows * 11 + 4 : 0);
        int x = sw / 2 + 8, y = PANEL_TOP_Y;
        panel(ms, mc, x, y, w, h, "Active potions");

        int i = 0;
        for (EffectInstance ei : effects) {
            int ry = y + 17 + i * 11;
            int col = 0xFF000000 | ei.getEffect().getColor();
            AbstractGui.fill(ms, x + 6, ry + 1, x + 11, ry + 6, col);

            String time = ei.isNoCounter() ? "**" : formatTicks(ei.getDuration());
            int tw = mc.font.width(time);
            String name = ei.getEffect().getDisplayName().getString() + " " + (ei.getAmplifier() + 1);
            name = mc.font.plainSubstrByWidth(name, w - 14 - tw - 10);
            mc.font.draw(ms, name, x + 14, ry, TEXT);
            mc.font.draw(ms, time, x + w - 6 - tw, ry, DIM);
            i++;
        }
    }

    private static String formatTicks(int ticks) {
        int s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private void drawCooldowns(Minecraft mc, MatrixStack ms, int sw, float pt) {
        if (allItems == null) allItems = ForgeRegistries.ITEMS.getValues().toArray(new Item[0]);

        // собираем предметы, у которых сейчас идёт кулдаун (в том числе серверные)
        List<Item> active = new ArrayList<>();
        List<Float> pcts = new ArrayList<>();
        for (Item it : allItems) {
            float p = mc.player.getCooldowns().getCooldownPercent(it, pt);
            if (p > 0f) { active.add(it); pcts.add(p); if (active.size() >= 6) break; }
        }

        int rows = active.size();
        int w = 110;
        int h = 14 + (rows > 0 ? rows * 18 + 3 : 0);
        int x = sw / 2 - w - 8, y = PANEL_TOP_Y;
        panel(ms, mc, x, y, w, h, "Cooldowns");

        for (int i = 0; i < rows; i++) {
            ItemStack st = new ItemStack(active.get(i));
            int ry = y + 17 + i * 18;
            mc.getItemRenderer().renderAndDecorateItem(st, x + 3, ry);
            String name = mc.font.plainSubstrByWidth(st.getHoverName().getString(), w - 28);
            mc.font.draw(ms, name, x + 23, ry + 1, TEXT);
            int bw = w - 29;
            AbstractGui.fill(ms, x + 23, ry + 11, x + 23 + bw, ry + 14, CELL);
            AbstractGui.fill(ms, x + 23, ry + 11, x + 23 + (int) (bw * pcts.get(i)), ry + 14, ACCENT);
        }
        RenderSystem.enableBlend();
    }

    private void drawTarget(Minecraft mc, MatrixStack ms, int sw, int sh) {
        Entity pick = mc.crosshairPickEntity;
        if (pick instanceof LivingEntity && pick != mc.player) {
            target = (LivingEntity) pick;
            targetTime = System.currentTimeMillis();
        }
        if (target == null || !target.isAlive() || System.currentTimeMillis() - targetTime > 2500) {
            target = null;
            prevTarget = null;
            return;
        }

        float hp = target.getHealth();
        float max = Math.max(1f, target.getMaxHealth());
        if (target != prevTarget) { hpAnim = hp; prevTarget = target; }
        hpAnim += (hp - hpAnim) * 0.12f;

        int w = 124, h = 32;
        int x = sw / 2 + 14, y = sh / 2 + 14;
        rrect(ms, x, y, w, h, BG);

        int hx = x + 4, hy = y + 4;
        if (target instanceof AbstractClientPlayerEntity) {
            RenderSystem.color4f(1f, 1f, 1f, 1f);
            mc.getTextureManager().bind(((AbstractClientPlayerEntity) target).getSkinTextureLocation());
            AbstractGui.blit(ms, hx, hy, 20, 20, 8.0F, 8.0F, 8, 8, 64, 64);    // лицо
            AbstractGui.blit(ms, hx, hy, 20, 20, 40.0F, 8.0F, 8, 8, 64, 64);   // второй слой (шапка)
        } else {
            rrect(ms, hx, hy, 20, 20, CELL);
        }

        String name = mc.font.plainSubstrByWidth(target.getName().getString(), w - 34);
        mc.font.drawShadow(ms, name, x + 28, y + 5, TEXT);
        mc.font.draw(ms, "HP: " + (Math.round(hp * 10f) / 10f), x + 28, y + 15, DIM);

        float ratio = Math.max(0f, Math.min(1f, hpAnim / max));
        int bx = x + 28, by = y + h - 6, bw = w - 34;
        int color = 0xFF000000 | ((int) (255 * (1f - ratio)) << 16) | ((int) (220 * ratio) << 8) | 0x30;
        AbstractGui.fill(ms, bx, by, bx + bw, by + 3, CELL);
        AbstractGui.fill(ms, bx, by, bx + (int) (bw * ratio), by + 3, color);
        RenderSystem.enableBlend();
    }
}
