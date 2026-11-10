package com.themodder.data;

import com.themodder.power.Powers;
import net.minecraft.nbt.NbtCompound;

public class PlayerData {
    public int level = 1;
    public int xp = 0;
    public int points = 1;
    public int selected = 0;
    public int fx = 2; // qualidade dos efeitos: 1 leve, 2 normal, 3 ultra
    public double mana = 50;
    public int[] powers = new int[Powers.COUNT];
    public int[] cd = new int[Powers.COUNT]; // não é salvo

    public PlayerData() {
        powers[0] = 1;
    }

    public static int xpNeeded(int level) {
        return 50 + 25 * (level - 1);
    }

    public static int maxMana(int level) {
        return 50 + 10 * (level - 1);
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("level", level);
        n.putInt("xp", xp);
        n.putInt("points", points);
        n.putInt("selected", selected);
        n.putInt("fx", fx);
        n.putDouble("mana", mana);
        n.putIntArray("powers", powers);
        return n;
    }

    public static PlayerData fromNbt(NbtCompound n) {
        PlayerData d = new PlayerData();
        d.level = Math.max(1, n.getInt("level"));
        d.xp = n.getInt("xp");
        d.points = n.getInt("points");
        d.selected = n.getInt("selected");
        d.fx = n.getInt("fx") == 0 ? 2 : Math.max(1, Math.min(3, n.getInt("fx")));
        d.mana = n.getDouble("mana");
        int[] p = n.getIntArray("powers");
        for (int i = 0; i < Math.min(p.length, d.powers.length); i++) d.powers[i] = p[i];
        if (d.selected < 0 || d.selected >= Powers.COUNT) d.selected = 0;
        return d;
    }
}
