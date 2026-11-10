package com.themodder.client;

import com.themodder.data.PlayerData;
import com.themodder.net.Payloads;
import com.themodder.power.Powers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class TheModderClient implements ClientModInitializer {
    private static KeyBinding useKey;
    private static KeyBinding nextKey;
    private static KeyBinding menuKey;
    private static final Random RND = new Random();

    @Override
    public void onInitializeClient() {
        useKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.themodder.use", GLFW.GLFW_KEY_R, "category.themodder"));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.themodder.next", GLFW.GLFW_KEY_V, "category.themodder"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.themodder.menu", GLFW.GLFW_KEY_K, "category.themodder"));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncS2C.ID, (payload, context) -> {
            ClientState.level = payload.level();
            ClientState.xp = payload.xp();
            ClientState.points = payload.points();
            ClientState.mana = payload.mana();
            ClientState.selected = payload.selected();
            ClientState.powers = payload.powers();
            ClientState.cd = payload.cd();
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.FxS2C.ID, (payload, context) -> {
            if (payload.shakeTicks() > 0 && payload.shakeStrength() > 0) {
                ClientState.shakeTicks = Math.max(ClientState.shakeTicks, payload.shakeTicks());
                ClientState.shakeMax = Math.max(1, ClientState.shakeTicks);
                ClientState.shakeStrength = Math.max(ClientState.shakeStrength, payload.shakeStrength());
            }
            if (payload.flashTicks() > 0) {
                ClientState.flashTicks = payload.flashTicks();
                ClientState.flashMax = payload.flashTicks();
                ClientState.flashColor = payload.flashColor();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // desfaz o tremor do tick anterior e aplica o novo
            if (ClientState.lastYaw != 0 || ClientState.lastPitch != 0) {
                client.player.setYaw(client.player.getYaw() - ClientState.lastYaw);
                client.player.setPitch(client.player.getPitch() - ClientState.lastPitch);
                ClientState.lastYaw = 0;
                ClientState.lastPitch = 0;
            }
            if (ClientState.shakeTicks > 0) {
                float s = ClientState.shakeStrength * 0.12f * (ClientState.shakeTicks / (float) ClientState.shakeMax);
                ClientState.lastYaw = (RND.nextFloat() - 0.5f) * 2 * s;
                ClientState.lastPitch = (RND.nextFloat() - 0.5f) * 2 * s;
                client.player.setYaw(client.player.getYaw() + ClientState.lastYaw);
                client.player.setPitch(client.player.getPitch() + ClientState.lastPitch);
                ClientState.shakeTicks--;
                if (ClientState.shakeTicks == 0) ClientState.shakeStrength = 0;
            }
            if (ClientState.flashTicks > 0) ClientState.flashTicks--;

            while (useKey.wasPressed()) {
                if (client.currentScreen == null) ClientPlayNetworking.send(new Payloads.ActionC2S(0, 0));
            }
            while (nextKey.wasPressed()) {
                for (int step = 1; step <= Powers.COUNT; step++) {
                    int id = (ClientState.selected + step) % Powers.COUNT;
                    if (ClientState.powers[id] > 0) {
                        ClientPlayNetworking.send(new Payloads.ActionC2S(1, id));
                        break;
                    }
                }
            }
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ModderScreen());
            }
        });

        HudRenderCallback.EVENT.register(TheModderClient::renderHud);
    }

    private static void bar(DrawContext ctx, int x, int y, int w, int h, double frac, int color) {
        frac = Math.max(0, Math.min(1, frac));
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        ctx.fill(x, y, x + (int) (w * frac), y + h, color);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        // flash de tela
        if (ClientState.flashTicks > 0) {
            int alpha = (int) (170 * (ClientState.flashTicks / (float) ClientState.flashMax));
            ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(),
                    (alpha << 24) | (ClientState.flashColor & 0xFFFFFF));
        }

        int x = 8, y = 8;
        int sel = ClientState.selected;
        ctx.drawTextWithShadow(mc.textRenderer, "§6Nv " + ClientState.level
                + (ClientState.points > 0 ? " §e(+" + ClientState.points + " pontos - K)" : ""), x, y, 0xFFFFFF);
        bar(ctx, x, y + 12, 100, 5, ClientState.xp / (double) PlayerData.xpNeeded(ClientState.level), 0xFFFFAA00);
        bar(ctx, x, y + 22, 100, 5, ClientState.mana / (double) PlayerData.maxMana(ClientState.level), 0xFF3399FF);
        String cdText = ClientState.cd[sel] > 0
                ? " §c" + String.format("%.1f", ClientState.cd[sel] / 20.0) + "s" : " §aPRONTO";
        ctx.drawTextWithShadow(mc.textRenderer,
                "§f" + Powers.NAMES[sel] + " (Nv " + ClientState.powers[sel] + ")" + cdText, x, y + 32, 0xFFFFFF);

        // barra de poderes
        for (int i = 0; i < Powers.COUNT; i++) {
            int bx = x + i * 17, by = y + 46;
            boolean unlocked = ClientState.powers[i] > 0;
            ctx.fill(bx - 1, by - 1, bx + 15, by + 15, i == sel ? 0xFFFFFFFF : 0xAA000000);
            ctx.fill(bx, by, bx + 14, by + 14, 0xFF202020);
            if (unlocked) {
                double ready = 1.0 - ClientState.cd[i] / (double) Powers.COOLDOWN[i];
                ready = Math.max(0, Math.min(1, ready));
                ctx.fill(bx, by + 14 - (int) (14 * ready), bx + 14, by + 14, 0xFF000000 | Powers.COLORS[i]);
            }
        }
    }
}
