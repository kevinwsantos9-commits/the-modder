package com.themodder.net;

import com.themodder.TheModder;
import com.themodder.power.Powers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class Payloads {
    private Payloads() {}

    /** action: 0 = usar poder, 1 = selecionar poder (arg = id), 2 = evoluir poder (arg = id) */
    public record ActionC2S(int action, int arg) implements CustomPayload {
        public static final CustomPayload.Id<ActionC2S> ID =
                new CustomPayload.Id<>(Identifier.of(TheModder.MOD_ID, "action"));
        public static final PacketCodec<RegistryByteBuf, ActionC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, ActionC2S::action,
                PacketCodecs.INTEGER, ActionC2S::arg,
                ActionC2S::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SyncS2C(int level, int xp, int points, int mana, int selected, int[] powers, int[] cd)
            implements CustomPayload {
        public static final CustomPayload.Id<SyncS2C> ID =
                new CustomPayload.Id<>(Identifier.of(TheModder.MOD_ID, "sync"));
        public static final PacketCodec<RegistryByteBuf, SyncS2C> CODEC =
                PacketCodec.of(SyncS2C::write, SyncS2C::read);

        private void write(RegistryByteBuf buf) {
            buf.writeInt(level);
            buf.writeInt(xp);
            buf.writeInt(points);
            buf.writeInt(mana);
            buf.writeInt(selected);
            for (int i = 0; i < Powers.COUNT; i++) buf.writeInt(powers[i]);
            for (int i = 0; i < Powers.COUNT; i++) buf.writeInt(cd[i]);
        }

        private static SyncS2C read(RegistryByteBuf buf) {
            int level = buf.readInt();
            int xp = buf.readInt();
            int points = buf.readInt();
            int mana = buf.readInt();
            int selected = buf.readInt();
            int[] powers = new int[Powers.COUNT];
            int[] cd = new int[Powers.COUNT];
            for (int i = 0; i < Powers.COUNT; i++) powers[i] = buf.readInt();
            for (int i = 0; i < Powers.COUNT; i++) cd[i] = buf.readInt();
            return new SyncS2C(level, xp, points, mana, selected, powers, cd);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Efeitos de tela: tremor e flash colorido. */
    public record FxS2C(int shakeTicks, int shakeStrength, int flashColor, int flashTicks) implements CustomPayload {
        public static final CustomPayload.Id<FxS2C> ID =
                new CustomPayload.Id<>(Identifier.of(TheModder.MOD_ID, "fx"));
        public static final PacketCodec<RegistryByteBuf, FxS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, FxS2C::shakeTicks,
                PacketCodecs.INTEGER, FxS2C::shakeStrength,
                PacketCodecs.INTEGER, FxS2C::flashColor,
                PacketCodecs.INTEGER, FxS2C::flashTicks,
                FxS2C::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ActionC2S.ID, ActionC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncS2C.ID, SyncS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(FxS2C.ID, FxS2C.CODEC);
    }
}
