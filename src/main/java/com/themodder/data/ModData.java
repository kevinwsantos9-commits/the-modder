package com.themodder.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModData extends PersistentState {
    public final Map<UUID, PlayerData> players = new HashMap<>();

    public static final PersistentState.Type<ModData> TYPE =
            new PersistentState.Type<>(ModData::new, ModData::fromNbt, null);

    public static ModData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "themodder");
    }

    public PlayerData of(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound all = new NbtCompound();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            all.put(e.getKey().toString(), e.getValue().toNbt());
        }
        nbt.put("players", all);
        return nbt;
    }

    public static ModData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ModData data = new ModData();
        NbtCompound all = nbt.getCompound("players");
        for (String key : all.getKeys()) {
            try {
                data.players.put(UUID.fromString(key), PlayerData.fromNbt(all.getCompound(key)));
            } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }
}
