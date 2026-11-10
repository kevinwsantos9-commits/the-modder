package com.themodder.power;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Powers {
    public static final int COUNT = 8;
    public static final int MAX_LEVEL = 5;
    public static final String[] NAMES = {
            "Raio Veloz", "Dragão de Fogo", "Aura Sagrada", "Tempestade",
            "Barreira Arcana", "Terremoto", "Meteoro", "Tornado"
    };
    public static final String[] DESC = {
            "Avança como um raio, atravessando e empurrando inimigos.",
            "Feixe em espiral de fogo que queima tudo e explode no final.",
            "Aura dourada que cura você e aliados por perto.",
            "Nuvem negra que chama vários raios na mira.",
            "Esfera arcana que protege e repele monstros.",
            "Onda de choque que quebra o chão e arremessa blocos.",
            "Meteoro do céu: explosão, cratera e destroços.",
            "Furacão que se move, suga e arremessa inimigos."
    };
    public static final int[] MANA = {15, 25, 30, 40, 35, 45, 60, 55};
    public static final int[] COOLDOWN = {40, 80, 160, 200, 300, 200, 400, 360}; // ticks
    public static final int[] COLORS = {0x44AAFF, 0xFF7722, 0xFFDD55, 0xBB88FF, 0x44FFEE, 0xAA7744, 0xFF3311, 0xAAAAAA};

    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.3f, 0.7f, 1f), 1.3f);
    private static final DustParticleEffect ORANGE = new DustParticleEffect(new Vector3f(1f, 0.5f, 0.1f), 1.6f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.85f, 0.3f), 1.2f);
    private static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.3f, 1f, 0.95f), 1.0f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1f, 0.15f, 0.1f), 1.5f);
    private static final DustParticleEffect GRAY = new DustParticleEffect(new Vector3f(0.6f, 0.6f, 0.65f), 2.0f);
    private static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.2f, 0.2f, 0.25f), 3.5f);

    private Powers() {}

    public static void playSound(ServerWorld w, double x, double y, double z, String id, float vol, float pitch) {
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
        if (ev != null) w.playSound(null, x, y, z, ev, SoundCategory.PLAYERS, vol, pitch);
    }

    public static void cast(ServerPlayerEntity p, int id, int lv, int fx) {
        Cast c = new Cast(p, lv, fx);
        switch (id) {
            case 0 -> dash(c);
            case 1 -> dragon(c);
            case 2 -> aura(c);
            case 3 -> storm(c);
            case 4 -> barrier(c);
            case 5 -> quake(c);
            case 6 -> meteor(c);
            case 7 -> tornado(c);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ 0
    private static void dash(Cast c) {
        ServerPlayerEntity p = c.p;
        int lv = c.lv;
        Vec3d look = p.getRotationVector();
        p.setVelocity(look.multiply(1.0 + 0.3 * lv));
        p.velocityModified = true;
        p.fallDistance = 0;
        c.sound("entity.firework_rocket.blast", p.getX(), p.getY(), p.getZ(), 1f, 1.6f);
        c.part(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        c.fx(p.getPos(), 8, 3, 0x66AAFF, 6, 20);
        Set<UUID> hit = new HashSet<>();
        Scheduler.repeat(0, 14, t -> {
            Vec3d pos = p.getPos().add(0, 1, 0);
            c.part(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 16, 0.4, 0.6, 0.4, 0.35);
            c.part(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 4, 0.2, 0.4, 0.2, 0.03);
            c.part(BLUE, pos.x, pos.y, pos.z, 12, 0.35, 0.55, 0.35, 0);
            double a0 = t * 0.9;
            for (int k = 0; k < 6; k++) {
                double a = a0 + k * Math.PI / 3;
                c.part(ParticleTypes.ELECTRIC_SPARK, pos.x + Math.cos(a) * 0.9, pos.y, pos.z + Math.sin(a) * 0.9,
                        1, 0, 0, 0, 0);
            }
            for (LivingEntity e : c.near(pos, 2.5)) {
                if (hit.add(e.getUuid())) {
                    c.hurt(e, 3 + 2 * lv);
                    e.addVelocity(look.x * 0.8, 0.4, look.z * 0.8);
                    e.velocityModified = true;
                    c.part(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 15, 0.3, 0.3, 0.3, 0.4);
                }
            }
        });
    }

    // ------------------------------------------------------------------ 1
    private static void dragon(Cast c) {
        ServerPlayerEntity p = c.p;
        ServerWorld w = c.w;
        int lv = c.lv;
        Vec3d dir = p.getRotationVector();
        Vec3d start = p.getEyePos().add(dir.multiply(0.8)).subtract(0, 0.25, 0);
        double maxRange = 14 + 3 * lv;
        Vec3d far = start.add(dir.multiply(maxRange));
        BlockHitResult br = w.raycast(new RaycastContext(start, far, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        double range = br.getType() == HitResult.Type.MISS ? maxRange : Math.max(2, start.distanceTo(br.getPos()));
        Vec3d end = start.add(dir.multiply(range));
        Vec3d upRef = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d u = dir.crossProduct(upRef).normalize();
        Vec3d v = dir.crossProduct(u).normalize();
        int dur = 12;
        c.sound("entity.ender_dragon.shoot", p.getX(), p.getY(), p.getZ(), 1.2f, 0.9f);
        c.fx(p.getPos(), 6, 3, 0xFF8822, 6, 25);
        Scheduler.repeat(0, dur, t -> {
            double d0 = range * t / dur, d1 = range * (t + 1) / dur;
            for (double d = d0; d < d1; d += 0.35) {
                Vec3d ctr = start.add(dir.multiply(d));
                double r = 0.3 + 0.55 * (d / range);
                double a = d * 2.4 + t * 0.7;
                for (int k = 0; k < 2; k++) {
                    double aa = a + k * Math.PI;
                    Vec3d pt = ctr.add(u.multiply(Math.cos(aa) * r)).add(v.multiply(Math.sin(aa) * r));
                    c.part(k == 0 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
                c.part(ORANGE, ctr.x, ctr.y, ctr.z, 2, 0.12, 0.12, 0.12, 0);
            }
            if (t == 2) {
                for (LivingEntity e : c.in(new Box(start, end).expand(2.5))) {
                    Vec3d rel = e.getBoundingBox().getCenter().subtract(start);
                    double tt = rel.dotProduct(dir);
                    if (tt < 0 || tt > range + 1) continue;
                    if (rel.subtract(dir.multiply(tt)).length() < 1.8) {
                        e.setOnFireFor(5 + lv);
                        c.hurt(e, 5 + 2 * lv);
                    }
                }
            }
        });
        Scheduler.run(dur, () -> {
            c.part(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 3, 0.6, 0.6, 0.6, 0);
            c.part(ParticleTypes.LAVA, end.x, end.y, end.z, 20, 0.8, 0.5, 0.8, 0);
            c.part(ParticleTypes.FLAME, end.x, end.y, end.z, 40, 0.5, 0.5, 0.5, 0.25);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                c.part(ParticleTypes.FLAME, end.x, end.y, end.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.35);
            }
            c.sound("entity.generic.explode", end.x, end.y, end.z, 0.9f, 1.2f);
            c.fx(end, 8, 4, 0xFF6600, 5, 25);
            for (LivingEntity e : c.near(end, 3 + lv * 0.5)) {
                e.setOnFireFor(5 + lv);
                c.hurt(e, 3 + lv);
            }
        });
    }

    // ------------------------------------------------------------------ 2
    private static void aura(Cast c) {
        ServerPlayerEntity p = c.p;
        ServerWorld w = c.w;
        int lv = c.lv;
        float heal = 4 + 3 * lv;
        Box box = p.getBoundingBox().expand(6 + lv);
        for (PlayerEntity o : w.getEntitiesByClass(PlayerEntity.class, box, e -> e.isAlive())) {
            o.heal(heal);
            o.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100 + 20 * lv, 1));
        }
        c.sound("item.totem.use", p.getX(), p.getY(), p.getZ(), 0.5f, 1.4f);
        c.sound("block.beacon.power_select", p.getX(), p.getY(), p.getZ(), 1f, 1.2f);
        c.part(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.9, 0.5, 0.4);
        c.fx(p.getPos(), 0, 0, 0xFFDD66, 12, 20);
        double maxR = 3 + lv * 0.4;
        Scheduler.repeat(0, 40, t -> {
            Vec3d pos = p.getPos();
            double rr = 0.5 + (t % 20) / 20.0 * maxR;
            int n = Math.max(12, (int) (rr * 8 * c.m));
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                c.part(GOLD, pos.x + Math.cos(a) * rr, pos.y + 0.1, pos.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
            double h = (t % 40) / 40.0 * 2.8;
            for (int s = 0; s < 2; s++) {
                double a = t * 0.35 + s * Math.PI;
                c.part(ParticleTypes.END_ROD, pos.x + Math.cos(a), pos.y + h, pos.z + Math.sin(a), 1, 0, 0, 0, 0);
                c.part(ParticleTypes.HAPPY_VILLAGER, pos.x + Math.cos(a), pos.y + h, pos.z + Math.sin(a), 1, 0.1, 0.1, 0.1, 0);
            }
            c.part(GOLD, pos.x, pos.y + 1.5, pos.z, 6, 0.5, 1.4, 0.5, 0);
        });
    }

    // ------------------------------------------------------------------ 3
    private static void storm(Cast c) {
        int lv = c.lv;
        Vec3d target = c.target(32);
        int bolts = 3 + lv;
        double spread = 2.5 + lv * 0.6;
        int total = 14 + bolts * 5;
        c.sound("entity.lightning_bolt.thunder", target.x, target.y, target.z, 0.6f, 1.4f);
        Scheduler.repeat(0, total, t -> {
            for (int i = 0; i < 8; i++) {
                double a = c.rnd.nextDouble() * Math.PI * 2;
                double r = c.rnd.nextDouble() * (spread + 3);
                c.part(DARK, target.x + Math.cos(a) * r, target.y + 14 + c.rnd.nextDouble() * 1.5,
                        target.z + Math.sin(a) * r, 1, 0.3, 0.1, 0.3, 0.01);
            }
            c.part(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 14, target.z, 3, spread, 0.5, spread, 0.2);
        });
        for (int i = 0; i < bolts; i++) {
            double sx = target.x + (c.rnd.nextDouble() - 0.5) * 2 * spread;
            double sz = target.z + (c.rnd.nextDouble() - 0.5) * 2 * spread;
            Vec3d spot = new Vec3d(sx, c.surface(sx, sz), sz);
            int delay = 10 + i * 5;
            Scheduler.repeat(delay - 8, 8, k -> {
                double rr = 0.3 + 2.3 * (1 - k / 8.0);
                for (int j = 0; j < 14; j++) {
                    double a = j * Math.PI * 2 / 14;
                    c.part(ParticleTypes.ELECTRIC_SPARK, spot.x + Math.cos(a) * rr, spot.y + 0.2,
                            spot.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                }
            });
            Scheduler.run(delay, () -> strike(c, spot));
        }
    }

    private static void strike(Cast c, Vec3d spot) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(c.w);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(spot.x, spot.y, spot.z);
            bolt.setCosmetic(true);
            c.w.spawnEntity(bolt);
        }
        c.sound("entity.lightning_bolt.thunder", spot.x, spot.y, spot.z, 2f, 1f);
        c.fx(spot, 6, 6, 0xFFFFFF, 5, 40);
        BlockState gs = c.w.getBlockState(BlockPos.ofFloored(spot.x, spot.y - 1, spot.z));
        if (!gs.isAir()) {
            c.part(new BlockStateParticleEffect(ParticleTypes.BLOCK, gs), spot.x, spot.y + 0.3, spot.z, 40, 0.6, 0.3, 0.6, 0.4);
        }
        c.part(ParticleTypes.ELECTRIC_SPARK, spot.x, spot.y + 1, spot.z, 50, 1.2, 1.5, 1.2, 0.5);
        c.part(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.5, spot.z, 2, 0.3, 0.1, 0.3, 0);
        for (LivingEntity e : c.near(spot, 3.5)) {
            c.hurt(e, 6 + 2 * c.lv);
            e.setOnFireFor(3);
        }
    }

    // ------------------------------------------------------------------ 4
    private static void barrier(Cast c) {
        ServerPlayerEntity p = c.p;
        int lv = c.lv;
        int dur = (8 + 3 * lv) * 20;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, lv >= 3 ? 1 : 0));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, dur, lv / 2));
        c.sound("block.beacon.activate", p.getX(), p.getY(), p.getZ(), 1f, 1.3f);
        c.part(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.9, 0.6, 0.8);
        c.fx(p.getPos(), 0, 0, 0x55FFEE, 8, 20);
        Scheduler.repeat(0, dur, t -> {
            if (!p.isAlive() || p.isRemoved()) return;
            Vec3d pos = p.getPos().add(0, 1, 0);
            if (t % 2 == 0) {
                int n = Math.max(10, (int) (30 * c.m));
                double rot = t * 0.07;
                for (int i = 0; i < n; i++) {
                    double y = 1 - 2.0 * (i + 0.5) / n;
                    double rad = Math.sqrt(1 - y * y);
                    double phi = i * 2.399963 + rot;
                    c.part(CYAN, pos.x + Math.cos(phi) * rad * 1.7, pos.y + y * 1.7, pos.z + Math.sin(phi) * rad * 1.7,
                            1, 0, 0, 0, 0);
                }
                int n2 = Math.max(8, (int) (16 * c.m));
                for (int i = 0; i < n2; i++) {
                    double a = i * Math.PI * 2 / n2 + t * 0.18;
                    double rx = Math.cos(a) * 2.0, ry = Math.sin(a) * 2.0;
                    c.part(ParticleTypes.END_ROD, pos.x + rx, pos.y + ry * 0.57, pos.z + ry * 0.82, 1, 0, 0, 0, 0);
                }
            }
            if (t % 5 == 0) {
                for (LivingEntity e : c.near(pos, 2.8)) {
                    if (e instanceof HostileEntity) {
                        Vec3d away = e.getPos().subtract(p.getPos());
                        Vec3d flat = new Vec3d(away.x, 0, away.z);
                        flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : new Vec3d(1, 0, 0);
                        e.addVelocity(flat.x * 0.7, 0.25, flat.z * 0.7);
                        e.velocityModified = true;
                        c.hurt(e, 1 + lv / 2);
                        c.part(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 8, 0.3, 0.3, 0.3, 0.3);
                    }
                }
            }
        });
        Scheduler.run(dur, () -> {
            Vec3d pos = p.getPos().add(0, 1, 0);
            c.part(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 40, 0.4, 0.6, 0.4, 0.4);
            c.sound("block.beacon.deactivate", p.getX(), p.getY(), p.getZ(), 1f, 1.2f);
        });
    }

    // ------------------------------------------------------------------ 5
    private static void quake(Cast c) {
        ServerPlayerEntity p = c.p;
        ServerWorld w = c.w;
        int lv = c.lv;
        double R = Math.min(13, 5 + lv * 1.5);
        Vec3d o = p.getPos();
        BlockPos ob = p.getBlockPos();
        int dur = 10;
        int cap = Math.max(12, (int) (34 * c.m));
        int[] launched = {0};
        Set<UUID> hit = new HashSet<>();
        c.sound("entity.generic.explode", o.x, o.y, o.z, 1.3f, 0.5f);
        c.fx(o, 26, 9, 0x884422, 5, 40);
        c.part(ParticleTypes.EXPLOSION, o.x, o.y + 0.5, o.z, 2, 0.5, 0.1, 0.5, 0);
        Scheduler.repeat(0, dur, t -> {
            double r0 = R * t / dur, r1 = R * (t + 1) / dur;
            int pts = (int) ((r0 + r1) * 6 * c.m) + 8;
            for (int i = 0; i < pts; i++) {
                double a = c.rnd.nextDouble() * Math.PI * 2;
                double r = r0 + c.rnd.nextDouble() * (r1 - r0);
                double ca = Math.cos(a), sa = Math.sin(a);
                int bx = (int) Math.floor(o.x + ca * r), bz = (int) Math.floor(o.z + sa * r);
                BlockPos g = c.ground(bx, bz, ob.getY() - 1);
                if (g == null) continue;
                BlockState s = w.getBlockState(g);
                c.part(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), bx + 0.5, g.getY() + 1.1, bz + 0.5,
                        3, 0.3, 0.1, 0.3, 0.3);
                if (c.rnd.nextDouble() < 0.3) {
                    c.part(ParticleTypes.CLOUD, bx + 0.5, g.getY() + 1.1, bz + 0.5, 0, ca * 0.1, 0.12, sa * 0.1, 1.0);
                }
                if (launched[0] < cap && c.rnd.nextDouble() < 0.45) {
                    launched[0] += c.launch(g, ca * 0.18, 0.55 + c.rnd.nextDouble() * 0.5, sa * 0.18);
                }
            }
            for (LivingEntity e : c.near(o, r1 + 1.0)) {
                if (hit.add(e.getUuid())) {
                    double dx = e.getX() - o.x, dz = e.getZ() - o.z;
                    double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                    c.hurt(e, 4 + 2 * lv);
                    e.addVelocity(dx / d, 0.7, dz / d);
                    e.velocityModified = true;
                }
            }
        });
        // rachaduras radiais
        Scheduler.run(4, () -> {
            for (int k = 0; k < 7; k++) {
                double a = k * Math.PI * 2 / 7 + c.rnd.nextDouble() * 0.4;
                for (double d = 1.5; d < R; d += 0.7) {
                    int bx = (int) Math.floor(o.x + Math.cos(a) * d + (c.rnd.nextDouble() - 0.5) * 0.6);
                    int bz = (int) Math.floor(o.z + Math.sin(a) * d + (c.rnd.nextDouble() - 0.5) * 0.6);
                    BlockPos g = c.ground(bx, bz, ob.getY() - 1);
                    if (g == null) continue;
                    c.part(ParticleTypes.SMOKE, bx + 0.5, g.getY() + 1.05, bz + 0.5, 2, 0.15, 0.02, 0.15, 0.01);
                    c.part(ParticleTypes.FLAME, bx + 0.5, g.getY() + 1.05, bz + 0.5, 1, 0.1, 0.02, 0.1, 0.01);
                    if (launched[0] < cap + 20 && c.rnd.nextDouble() < 0.4) {
                        launched[0] += c.launch(g, 0, 0.3 + c.rnd.nextDouble() * 0.3, 0);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ 6
    private static void meteor(Cast c) {
        ServerPlayerEntity p = c.p;
        ServerWorld w = c.w;
        int lv = c.lv;
        Vec3d t = c.target(36);
        float power = 2.5f + 0.6f * lv;
        int fall = 24;
        int startY = Math.min(w.getTopY() - 2, (int) t.y + 48);
        double sy = startY;
        c.sound("entity.firework_rocket.launch", p.getX(), p.getY(), p.getZ(), 1.5f, 0.5f);
        FallingBlockEntity meteor = FallingBlockEntity.spawnFromBlock(w, BlockPos.ofFloored(t.x, sy, t.z),
                Blocks.MAGMA_BLOCK.getDefaultState());
        meteor.setNoGravity(true);
        meteor.setVelocity(Vec3d.ZERO);
        meteor.setPosition(t.x, sy, t.z);
        Scheduler.repeat(0, fall, k -> {
            double y = sy - (sy - t.y) * (k + 1) / fall;
            if (!meteor.isRemoved()) meteor.setPosition(t.x, y, t.z);
            c.part(ParticleTypes.FLAME, t.x, y + 1, t.z, 14, 0.5, 0.8, 0.5, 0.05);
            c.part(ParticleTypes.LARGE_SMOKE, t.x, y + 2, t.z, 8, 0.6, 1.2, 0.6, 0.02);
            c.part(ParticleTypes.LAVA, t.x, y + 1, t.z, 2, 0.4, 0.4, 0.4, 0);
            c.part(ORANGE, t.x, y + 1, t.z, 10, 0.7, 1.0, 0.7, 0);
            double rr = 1.5 + (power + 1.5) * (1.0 - k / (double) fall);
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20;
                c.part(RED, t.x + Math.cos(a) * rr, t.y + 0.2, t.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
            if (k > fall - 8) c.fx(t, 3, 2 + (k - (fall - 8)), 0, 0, 45);
        });
        Scheduler.run(fall, () -> {
            meteor.discard();
            impact(c, t, power);
        });
    }

    private static void impact(Cast c, Vec3d t, float power) {
        ServerWorld w = c.w;
        int lv = c.lv;
        w.createExplosion(c.p, t.x, t.y + 0.5, t.z, power, World.ExplosionSourceType.MOB);
        c.part(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 1, t.z, 1, 0, 0, 0, 0);
        c.part(ParticleTypes.FLASH, t.x, t.y + 1.5, t.z, 1, 0, 0, 0, 0);
        c.part(ParticleTypes.LAVA, t.x, t.y + 1, t.z, 60, 2.5, 1.0, 2.5, 0);
        c.part(ParticleTypes.FLAME, t.x, t.y + 1, t.z, 80, 1.5, 1.0, 1.5, 0.35);
        c.part(ParticleTypes.LARGE_SMOKE, t.x, t.y + 2, t.z, 50, 1.5, 1.5, 1.5, 0.15);
        c.sound("entity.generic.explode", t.x, t.y, t.z, 3f, 0.6f);
        c.fx(t, 30, 10, 0xFFCC88, 14, 60);
        c.crater(BlockPos.ofFloored(t), power * 0.9, power * 2.2, Math.max(15, (int) (45 * c.m)), 0.9, 0.35);
        double sw = power * 1.6;
        Scheduler.repeat(0, 14, k -> {
            double r = 1 + sw * k / 14.0;
            int n = (int) (r * 7 * c.m) + 12;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                double ca = Math.cos(a), sa = Math.sin(a);
                c.part(ParticleTypes.CLOUD, t.x + ca * r, t.y + 0.5, t.z + sa * r, 0, ca, 0.04, sa, 0.5);
                if (i % 2 == 0) c.part(ORANGE, t.x + ca * r, t.y + 0.4, t.z + sa * r, 1, 0, 0, 0, 0);
            }
        });
        for (LivingEntity e : c.near(t, power * 2.5)) {
            e.setOnFireFor(4 + lv);
            Vec3d away = e.getPos().subtract(t);
            Vec3d flat = new Vec3d(away.x, 0, away.z);
            flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : new Vec3d(1, 0, 0);
            e.addVelocity(flat.x * 0.9, 0.6, flat.z * 0.9);
            e.velocityModified = true;
        }
    }

    // ------------------------------------------------------------------ 7
    private static void tornado(Cast c) {
        ServerPlayerEntity p = c.p;
        ServerWorld w = c.w;
        int lv = c.lv;
        Vec3d look = p.getRotationVector();
        Vec3d f0 = new Vec3d(look.x, 0, look.z);
        Vec3d fwd = f0.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : f0.normalize();
        Vec3d start = c.target(28);
        double[] pos = {start.x, start.y, start.z};
        int dur = (4 + lv) * 20 + 30;
        double H = 11;
        c.sound("entity.breeze.wind_burst", start.x, start.y, start.z, 1.5f, 0.6f);
        c.fx(start, 12, 3, 0, 0, 30);
        Scheduler.repeat(0, dur, t -> {
            pos[0] += fwd.x * 0.12;
            pos[2] += fwd.z * 0.12;
            if (t % 4 == 0) pos[1] = c.surface(pos[0], pos[2]);
            BlockState gs = w.getBlockState(BlockPos.ofFloored(pos[0], pos[1] - 1, pos[2]));
            BlockStateParticleEffect debris = gs.isAir() ? null : new BlockStateParticleEffect(ParticleTypes.BLOCK, gs);
            int layers = (int) (20 * c.m) + 6;
            for (int i = 0; i < layers; i++) {
                double h = H * i / layers;
                double r = 0.7 + h * 0.33;
                double a = t * 0.55 + h * 1.3;
                for (int arm = 0; arm < 2; arm++) {
                    double aa = a + arm * Math.PI;
                    double ca = Math.cos(aa), sa = Math.sin(aa);
                    double x = pos[0] + ca * r, z = pos[2] + sa * r;
                    c.part(ParticleTypes.CLOUD, x, pos[1] + h, z, 0, -sa, 0.15, ca, 0.3);
                    if (i % 3 == 0) c.part(GRAY, x, pos[1] + h, z, 1, 0.1, 0.1, 0.1, 0);
                    if (debris != null && h < 5 && i % 2 == 0) c.part(debris, x, pos[1] + h, z, 1, 0.2, 0.2, 0.2, 0.05);
                }
            }
            Box box = new Box(pos[0] - 7, pos[1] - 1, pos[2] - 7, pos[0] + 7, pos[1] + H + 2, pos[2] + 7);
            for (LivingEntity e : c.in(box)) {
                double dx = pos[0] - e.getX(), dz = pos[2] - e.getZ();
                double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                if (d > 7) continue;
                double pull = d > 1.5 ? 0.09 : 0.0;
                double vx = dx / d * pull + (-dz / d) * 0.16;
                double vz = dz / d * pull + (dx / d) * 0.16;
                double vy = e.getY() < pos[1] + H ? 0.12 : 0.0;
                Vec3d nv = e.getVelocity().add(vx, vy, vz);
                if (nv.length() > 1.1) nv = nv.normalize().multiply(1.1);
                e.setVelocity(nv);
                e.velocityModified = true;
                if (t % 10 == 0) c.hurt(e, 1 + lv * 0.5f);
            }
        });
        Scheduler.run(dur, () -> {
            c.part(ParticleTypes.CLOUD, pos[0], pos[1] + 2, pos[2], 60, 1.5, 2.5, 1.5, 0.2);
            c.sound("entity.breeze.wind_burst", pos[0], pos[1], pos[2], 1.2f, 0.9f);
        });
    }
}
