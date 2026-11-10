package com.themodder.power;

import com.themodder.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;

/** Contexto de uma conjuração: jogador, mundo, nível do poder e qualidade dos efeitos. */
final class Cast {
    final ServerPlayerEntity p;
    final ServerWorld w;
    final int lv;
    final double m; // multiplicador de partículas
    final Random rnd = new Random();
    final boolean grief;

    Cast(ServerPlayerEntity p, int lv, int fx) {
        this.p = p;
        this.w = p.getServerWorld();
        this.lv = lv;
        this.m = fx <= 1 ? 0.5 : (fx >= 3 ? 1.8 : 1.0);
        this.grief = w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
    }

    <T extends ParticleEffect> void part(T eff, double x, double y, double z, int count,
                                         double dx, double dy, double dz, double speed) {
        int c = count <= 0 ? 0 : Math.max(1, (int) Math.round(count * m));
        for (ServerPlayerEntity v : w.getPlayers()) {
            double d2 = v.squaredDistanceTo(x, y, z);
            if (d2 > 96 * 96) continue;
            w.spawnParticles(v, eff, d2 > 28 * 28, x, y, z, c, dx, dy, dz, speed);
        }
    }

    void sound(String id, double x, double y, double z, float vol, float pitch) {
        Powers.playSound(w, x, y, z, id, vol, pitch);
    }

    /** Tremor de tela e flash de cor para quem está perto. */
    void fx(Vec3d c, int shakeTicks, int shakeStrength, int flashColor, int flashTicks, double radius) {
        for (ServerPlayerEntity v : w.getPlayers()) {
            double d = Math.sqrt(v.squaredDistanceTo(c));
            if (d > radius) continue;
            double f = 1.0 - d / radius;
            int s = (int) Math.round(shakeStrength * f);
            int ft = (flashTicks > 0 && d < radius * 0.6) ? flashTicks : 0;
            if (s <= 0 && ft <= 0) continue;
            ServerPlayNetworking.send(v, new Payloads.FxS2C(shakeTicks, s, flashColor, ft));
        }
    }

    List<LivingEntity> in(Box b) {
        return w.getEntitiesByClass(LivingEntity.class, b, e -> e != p && e.isAlive());
    }

    List<LivingEntity> near(Vec3d c, double r) {
        Box b = new Box(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        return w.getEntitiesByClass(LivingEntity.class, b,
                e -> e != p && e.isAlive() && e.squaredDistanceTo(c) <= r * r);
    }

    void hurt(LivingEntity e, float amount) {
        e.damage(w.getDamageSources().playerAttack(p), amount);
    }

    Vec3d target(double range) {
        HitResult h = p.raycast(range, 0f, false);
        Vec3d pos = h.getPos();
        if (h.getType() == HitResult.Type.MISS) {
            pos = new Vec3d(pos.x, surface(pos.x, pos.z), pos.z);
        }
        return pos;
    }

    double surface(double x, double z) {
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Acha o bloco de chão (sólido com espaço livre acima) perto da altura refY. */
    BlockPos ground(int x, int z, int refY) {
        for (int y = refY + 3; y >= refY - 6; y--) {
            BlockPos bp = new BlockPos(x, y, z);
            BlockState s = w.getBlockState(bp);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            if (s.getCollisionShape(w, bp).isEmpty()) continue;
            BlockPos up = bp.up();
            if (w.getBlockState(up).getCollisionShape(w, up).isEmpty()) return bp;
        }
        return null;
    }

    /** Arremessa um bloco do chão como bloco caindo (3D de verdade). */
    int launch(BlockPos bp, double vx, double vy, double vz) {
        if (!grief) return 0;
        BlockState s = w.getBlockState(bp);
        float hard = s.getHardness(w, bp);
        if (s.isAir() || hard < 0 || hard > 10 || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return 0;
        FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, bp, s);
        fb.setVelocity(vx, vy, vz);
        fb.velocityModified = true;
        return 1;
    }

    /** Explode o chão em anel entre rMin e rMax arremessando blocos para cima e para fora. */
    int crater(BlockPos center, double rMin, double rMax, int cap, double up, double out) {
        int n = 0;
        int ri = (int) Math.ceil(rMax);
        for (int dx = -ri; dx <= ri && n < cap; dx++) {
            for (int dz = -ri; dz <= ri && n < cap; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < rMin || d > rMax || rnd.nextDouble() > 0.55) continue;
                BlockPos g = ground(center.getX() + dx, center.getZ() + dz, center.getY());
                if (g == null) continue;
                BlockState s = w.getBlockState(g);
                double ux = dx / Math.max(d, 0.001), uz = dz / Math.max(d, 0.001);
                part(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), g.getX() + 0.5, g.getY() + 1.1,
                        g.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.3);
                n += launch(g, ux * out * (0.5 + rnd.nextDouble()), up * (0.6 + rnd.nextDouble() * 0.8),
                        uz * out * (0.5 + rnd.nextDouble()));
            }
        }
        return n;
    }
}
