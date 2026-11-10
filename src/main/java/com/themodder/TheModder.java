package com.themodder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.themodder.data.ModData;
import com.themodder.data.PlayerData;
import com.themodder.net.Payloads;
import com.themodder.power.Powers;
import com.themodder.power.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class TheModder implements ModInitializer {
    public static final String MOD_ID = "themodder";

    @Override
    public void onInitialize() {
        Payloads.register();

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ActionC2S.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            handleAction(p, payload.action(), payload.arg());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(TheModder::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Scheduler.clear());

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity sp && !(entity instanceof PlayerEntity)) {
                addXp(sp, Math.max(2, (int) (entity.getMaxHealth() / 2)));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("modder")
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        PlayerData d = ModData.get(ctx.getSource().getServer()).of(p.getUuid());
                        ctx.getSource().sendFeedback(() -> Text.literal("§6[The Modder] §fNível " + d.level
                                + " | XP " + d.xp + "/" + PlayerData.xpNeeded(d.level)
                                + " | Pontos " + d.points), false);
                        return 1;
                    })
                    .then(CommandManager.literal("fx")
                            .then(CommandManager.argument("nivel", IntegerArgumentType.integer(1, 3))
                                    .executes(ctx -> {
                                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                        ModData md = ModData.get(ctx.getSource().getServer());
                                        int v = IntegerArgumentType.getInteger(ctx, "nivel");
                                        md.of(p.getUuid()).fx = v;
                                        md.markDirty();
                                        ctx.getSource().sendFeedback(() -> Text.literal("§6[The Modder] §fEfeitos: "
                                                + (v == 1 ? "leve" : v == 2 ? "normal" : "ultra")), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("xp")
                            .requires(src -> src.hasPermissionLevel(2))
                            .then(CommandManager.argument("quantidade", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                        addXp(p, IntegerArgumentType.getInteger(ctx, "quantidade"));
                                        return 1;
                                    }))));
        });
    }

    private static void tick(MinecraftServer server) {
        Scheduler.tick();
        ModData data = ModData.get(server);
        boolean syncNow = server.getTicks() % 10 == 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerData d = data.of(p.getUuid());
            for (int i = 0; i < Powers.COUNT; i++) if (d.cd[i] > 0) d.cd[i]--;
            double max = PlayerData.maxMana(d.level);
            if (d.mana < max) {
                d.mana = Math.min(max, d.mana + 0.1 + d.level * 0.01);
                data.markDirty();
            }
            if (syncNow) sync(p);
        }
    }

    private static void handleAction(ServerPlayerEntity p, int action, int arg) {
        ModData data = ModData.get(p.getServer());
        PlayerData d = data.of(p.getUuid());
        switch (action) {
            case 0 -> {
                int id = d.selected;
                int lv = d.powers[id];
                if (lv <= 0) {
                    p.sendMessage(Text.literal("§cPoder bloqueado! Abra o menu (K) e gaste pontos."), true);
                } else if (d.cd[id] > 0) {
                    p.sendMessage(Text.literal("§e" + Powers.NAMES[id] + " em recarga..."), true);
                } else if (d.mana < Powers.MANA[id]) {
                    p.sendMessage(Text.literal("§9Mana insuficiente!"), true);
                } else {
                    Powers.cast(p, id, lv, d.fx);
                    d.mana -= Powers.MANA[id];
                    d.cd[id] = Powers.COOLDOWN[id];
                    data.markDirty();
                    sync(p);
                }
            }
            case 1 -> {
                if (arg >= 0 && arg < Powers.COUNT && d.powers[arg] > 0) {
                    d.selected = arg;
                    data.markDirty();
                    sync(p);
                }
            }
            case 2 -> {
                if (arg >= 0 && arg < Powers.COUNT && d.points > 0 && d.powers[arg] < Powers.MAX_LEVEL) {
                    d.powers[arg]++;
                    d.points--;
                    if (d.powers[arg] == 1) d.selected = arg;
                    data.markDirty();
                    sync(p);
                }
            }
            default -> {}
        }
    }

    public static void addXp(ServerPlayerEntity p, int amount) {
        ModData data = ModData.get(p.getServer());
        PlayerData d = data.of(p.getUuid());
        d.xp += amount;
        while (d.xp >= PlayerData.xpNeeded(d.level)) {
            d.xp -= PlayerData.xpNeeded(d.level);
            d.level++;
            d.points++;
            d.mana = PlayerData.maxMana(d.level);
            p.sendMessage(Text.literal("§6[The Modder] §fSubiu para o nível §e" + d.level
                    + "§f! +1 ponto de habilidade (K)"), false);
            Powers.playSound(p.getServerWorld(), p.getX(), p.getY(), p.getZ(), "entity.player.levelup", 1f, 1f);
        }
        data.markDirty();
        sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        PlayerData d = ModData.get(p.getServer()).of(p.getUuid());
        ServerPlayNetworking.send(p, new Payloads.SyncS2C(
                d.level, d.xp, d.points, (int) d.mana, d.selected, d.powers.clone(), d.cd.clone()));
    }
}
