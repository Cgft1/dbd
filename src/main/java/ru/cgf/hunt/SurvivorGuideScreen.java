package ru.cgf.hunt;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class SurvivorGuideScreen extends Screen {
    public SurvivorGuideScreen() {
        super(Text.literal("Памятка выжившего"));
    }

    @Override
    protected void init() {
        int buttonWidth = 250;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Понятно, начать!"), button -> {
            if (this.client != null) this.client.setScreen(null);
        }).dimensions(this.width / 2 - buttonWidth / 2, this.height - 32, buttonWidth, 20).build());
    }

    // Screen normally draws a blurred world behind menus. This guide uses a clear, solid overlay instead.
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF17212A);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF17212A);
        int panelWidth = Math.min(500, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(8, (this.height - 262) / 2);
        context.fill(left, top, left + panelWidth, top + 257, 0xFF101820);
        context.drawBorder(left, top, panelWidth, 257, 0xFF6F8794);
        context.drawCenteredTextWithShadow(this.textRenderer, "ПАМЯТКА ВЫЖИВШЕГО", this.width / 2, top + 10, 0xFFFFD36A);

        int row1 = top + 34;
        context.fill(left + 10, row1, left + panelWidth - 10, row1 + 48, 0xFF26343D);
        context.drawItem(new ItemStack(HuntMod.GENERATOR_ITEM), left + 18, row1 + 12);
        context.drawTextWithShadow(this.textRenderer, "1. КАК ЧИНИТЬ ГЕНЕРАТОР", left + 44, row1 + 5, 0xFF72D6FF);
        context.drawText(this.textRenderer, "Подойди и нажми ПКМ по генератору.", left + 44, row1 + 19, 0xFFFFFFFF, false);
        context.drawText(this.textRenderer, "Стой рядом до завершения ремонта.", left + 44, row1 + 32, 0xFFCCCCCC, false);

        int row2 = top + 86;
        context.fill(left + 10, row2, left + panelWidth - 10, row2 + 48, 0xFF26343D);
        context.drawItem(new ItemStack(HuntMod.TRAP_ITEM), left + 18, row2 + 12);
        context.drawTextWithShadow(this.textRenderer, "2. КАПКАНЫ", left + 44, row2 + 5, 0xFFFF8585);
        context.drawText(this.textRenderer, "Попадание отнимает жизнь и обездвиживает.", left + 44, row2 + 19, 0xFFFFFFFF, false);
        context.drawText(this.textRenderer, "Маньяк может подбирать капканы.", left + 44, row2 + 32, 0xFFCCCCCC, false);

        int row3 = top + 138;
        context.fill(left + 10, row3, left + panelWidth - 10, row3 + 48, 0xFF26343D);
        context.drawItem(new ItemStack(HuntMod.LOCKER_ITEM), left + 18, row3 + 12);
        context.drawTextWithShadow(this.textRenderer, "3. ПРЯЧЬСЯ В ШКАФАХ", left + 44, row3 + 5, 0xFFB9F28C);
        context.drawText(this.textRenderer, "Нажми ПКМ по шкафу, чтобы спрятаться.", left + 44, row3 + 19, 0xFFFFFFFF, false);
        context.drawText(this.textRenderer, "Нажми SHIFT, чтобы выйти.", left + 44, row3 + 32, 0xFFCCCCCC, false);

        int row4 = top + 190;
        context.fill(left + 10, row4, left + panelWidth - 10, row4 + 48, 0xFF26343D);
        context.drawItem(new ItemStack(HuntMod.PALLET_ITEM), left + 18, row4 + 12);
        context.drawTextWithShadow(this.textRenderer, "4. ДОСКИ И ПОГОНЯ", left + 44, row4 + 5, 0xFFFFC36A);
        context.drawText(this.textRenderer, "Выживший: ПКМ по доскам — перепрыгнуть.", left + 44, row4 + 19, 0xFFFFFFFF, false);
        context.drawText(this.textRenderer, "Маньяк: ПКМ по доскам — сломать за 2 сек.", left + 44, row4 + 32, 0xFFCCCCCC, false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}