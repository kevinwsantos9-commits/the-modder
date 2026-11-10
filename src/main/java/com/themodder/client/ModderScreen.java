package com.themodder.client;

import com.themodder.data.PlayerData;
import com.themodder.net.Payloads;
import com.themodder.power.Powers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ModderScreen extends Screen {
    private final ButtonWidget[] selectBtns = new ButtonWidget[Powers.COUNT];
    private final ButtonWidget[] upBtns = new ButtonWidget[Powers.COUNT];

    public ModderScreen() {
        super(Text.literal("The Modder"));
    }

    private int top() {
        return this.height / 2 - 62;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int colW = 184;
        for (int i = 0; i < Powers.COUNT; i++) {
            final int id = i;
            int col = i % 2, row = i / 2;
            int x = cx - colW - 4 + col * (colW + 8);
            int y = top() + row * 26;
            selectBtns[i] = addDrawableChild(ButtonWidget.builder(Text.empty(),
                    b -> ClientPlayNetworking.send(new Payloads.ActionC2S(1, id)))
                    .dimensions(x, y, 160, 20).build());
            upBtns[i] = addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                    b -> ClientPlayNetworking.send(new Payloads.ActionC2S(2, id)))
                    .dimensions(x + 162, y, 22, 20).build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < Powers.COUNT; i++) {
            int lv = ClientState.powers[i];
            String label = (ClientState.selected == i ? "> " : "") + Powers.NAMES[i]
                    + (lv > 0 ? "  Nv " + lv : "  [bloq.]");
            selectBtns[i].setMessage(Text.literal(label));
            selectBtns[i].active = lv > 0;
            upBtns[i].active = ClientState.points > 0 && lv < Powers.MAX_LEVEL;
        }
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = top();
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§6§lTHE MODDER", cx, top - 40, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Nível " + ClientState.level + "  |  XP " + ClientState.xp + "/"
                        + PlayerData.xpNeeded(ClientState.level) + "  |  Pontos: " + ClientState.points,
                cx, top - 26, 0xFFFFFF);
        int sel = ClientState.selected;
        int by = top + 4 * 26 + 6;
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§e" + Powers.NAMES[sel] + "§f - " + Powers.DESC[sel],
                cx, by, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Mana: " + Powers.MANA[sel] + "   Recarga: " + (Powers.COOLDOWN[sel] / 20.0) + "s",
                cx, by + 12, 0xAADDFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "R = usar   V = trocar   K = menu   |   /modder fx 1-3 = qualidade dos efeitos",
                cx, by + 26, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
