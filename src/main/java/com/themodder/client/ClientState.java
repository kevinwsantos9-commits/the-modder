package com.themodder.client;

import com.themodder.power.Powers;

public final class ClientState {
    public static int level = 1, xp = 0, points = 0, mana = 0, selected = 0;
    public static int[] powers = new int[Powers.COUNT];
    public static int[] cd = new int[Powers.COUNT];

    // efeitos de tela
    public static int shakeTicks = 0, shakeMax = 1, shakeStrength = 0;
    public static int flashTicks = 0, flashMax = 1, flashColor = 0xFFFFFF;
    public static float lastYaw = 0, lastPitch = 0;

    private ClientState() {}
}
