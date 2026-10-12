package ru.cgf.hunt;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class SurvivorGuideScreen extends Screen {
    private int page = 0;

    public SurvivorGuideScreen() {
        super(Text.literal("Памятка выжившего"));
    }

    @Override
    protected void init() {
        int buttonWidth = 210;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Понятно, начать!"), button -> {
            if (this.client != null) this.client.setScreen(null);
        }).dimensions(this.width / 2 - buttonWidth / 2, this.height - 29, buttonWidth, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF080D14);
    }

    private void panel(DrawContext c, int x, int y, int w, int h, int accent) {
        c.fill(x, y, x + w, y + h, 0xF20C131D);
        c.fill(x, y, x + w, y + 2, accent);
        c.drawBorder(x, y, w, h, 0xFF34465A);
    }

    private void heading(DrawContext c, String title, int x, int y, int color) {
        c.drawTextWithShadow(this.textRenderer, title, x, y, color);
    }

    private void line(DrawContext c, String text, int x, int y, int color) {
        c.drawText(this.textRenderer, text, x, y, color, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF080D14);
        int margin = 12;
        int gap = 8;
        int availableW = this.width - margin * 2;
        int colW = (availableW - gap * 2) / 3;
        int top = 12;
        int bottom = this.height - 38;
        int rowGap = 8;
        int rowH = (bottom - top - rowGap * 2) / 3;
        if (colW < 145 || rowH < 90) {
            // Always show readable tips between the title and the bottom button.
            int left = 12;
            int right = this.width - 12;
            int contentTop = 34;
            int contentBottom = this.height - 42;
            context.fill(left, contentTop, right, contentBottom, 0xF20C131D);
            context.drawBorder(left, contentTop, right - left, Math.max(1, contentBottom - contentTop), 0xFF34465A);
            context.fill(left, contentTop, right, contentTop + 2, 0xFF5E9FCB);
            String[] tips = {
                "ЦЕЛЬ: почини 5 генераторов и открой ворота.",
                "РЕМОНТ: ПКМ по генератору.",
                "Мини-игра появляется в случайный момент.",
                "ДОСКИ: перепрыгивай их, маньяк может ломать.",
                "КАПКАНЫ: избегай их — они опасны.",
                "ШКАФЫ: ПКМ — спрятаться, SHIFT — выйти.",
                "ПОСЛЕ РЕМОНТА: маньяк видит подсказку 4 сек.",
                "Выжившие рядом светятся 2 сек. в радиусе 5 блоков."
            };
            int available = Math.max(1, contentBottom - contentTop - 14);
            int step = Math.max(10, Math.min(18, available / tips.length));
            int y = contentTop + 8;
            for (String tip : tips) {
                if (y + 9 > contentBottom - 3) break;
                line(context, tip, left + 8, y, 0xFFE6EDF5);
                y += step;
            }
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        heading(context, "ПАМЯТКА ВЫЖИВШЕГО", margin, 2, 0xFFFFD36A);
        int[] xs = {margin, margin + colW + gap, margin + (colW + gap) * 2};
        int[] ys = {top + 15, top + 15 + rowH + rowGap, top + 15 + (rowH + rowGap) * 2};
        int h = rowH;

        // Goal / teamwork
        panel(context, xs[0], ys[0], colW, h, 0xFF5E9FCB);
        heading(context, "ТВОЯ ЦЕЛЬ", xs[0] + 9, ys[0] + 8, 0xFF8DD9FF);
        context.drawItem(new ItemStack(HuntMod.TOOLBOX_ITEM), xs[0] + 9, ys[0] + 28);
        line(context, "Чини генераторы и", xs[0] + 32, ys[0] + 31, 0xFFE6EDF5);
        line(context, "избегай маньяка.", xs[0] + 32, ys[0] + 44, 0xFFE6EDF5);
        line(context, "Помогай союзникам.", xs[0] + 9, ys[0] + 62, 0xFFB6C4D4);
        line(context, "Вместе легче выжить.", xs[0] + 9, ys[0] + 75, 0xFFB6C4D4);

        // Generator mini game
        panel(context, xs[1], ys[0], colW, h, 0xFF35C58A);
        heading(context, "ГЕНЕРАТОР", xs[1] + 9, ys[0] + 8, 0xFF72E8B5);
        context.drawItem(new ItemStack(HuntMod.GENERATOR_ITEM), xs[1] + 9, ys[0] + 28);
        line(context, "ПКМ — начать ремонт.", xs[1] + 32, ys[0] + 31, 0xFFE6EDF5);
        line(context, "Мини-игра появляется", xs[1] + 9, ys[0] + 51, 0xFFE6EDF5);
        line(context, "в случайный момент.", xs[1] + 9, ys[0] + 64, 0xFFE6EDF5);
        context.fill(xs[1] + 9, ys[0] + 82, xs[1] + colW - 9, ys[0] + 89, 0xFF293847);
        context.fill(xs[1] + 9, ys[0] + 82, xs[1] + (colW - 18) * 2 / 3 + 9, ys[0] + 89, 0xFF31D18A);

        // Pallets
        panel(context, xs[2], ys[0], colW, h, 0xFFFFB84D);
        heading(context, "ДОСКИ", xs[2] + 9, ys[0] + 8, 0xFFFFC96D);
        context.drawItem(new ItemStack(HuntMod.PALLET_ITEM), xs[2] + 9, ys[0] + 28);
        line(context, "Перепрыгни доску,", xs[2] + 32, ys[0] + 31, 0xFFE6EDF5);
        line(context, "чтобы оторваться.", xs[2] + 32, ys[0] + 44, 0xFFE6EDF5);
        line(context, "Маньяк ломает её", xs[2] + 9, ys[0] + 62, 0xFFB6C4D4);
        line(context, "примерно за 2 сек.", xs[2] + 9, ys[0] + 75, 0xFFB6C4D4);

        // Traps
        panel(context, xs[0], ys[1], colW, h, 0xFFE34D4D);
        heading(context, "КАПКАНЫ", xs[0] + 9, ys[1] + 8, 0xFFFF7777);
        context.drawItem(new ItemStack(HuntMod.TRAP_ITEM), xs[0] + 9, ys[1] + 28);
        line(context, "Попадание отнимает", xs[0] + 32, ys[1] + 31, 0xFFE6EDF5);
        line(context, "жизнь и замедляет.", xs[0] + 32, ys[1] + 44, 0xFFE6EDF5);
        line(context, "Маньяк может поднять", xs[0] + 9, ys[1] + 62, 0xFFB6C4D4);
        line(context, "установленный капкан.", xs[0] + 9, ys[1] + 75, 0xFFB6C4D4);

        // Locker
        panel(context, xs[1], ys[1], colW, h, 0xFFB65CFF);
        heading(context, "ШКАФЫ", xs[1] + 9, ys[1] + 8, 0xFFD5A0FF);
        context.drawItem(new ItemStack(HuntMod.LOCKER_ITEM), xs[1] + 9, ys[1] + 28);
        line(context, "ПКМ по шкафу —", xs[1] + 32, ys[1] + 31, 0xFFE6EDF5);
        line(context, "спрятаться внутри.", xs[1] + 32, ys[1] + 44, 0xFFE6EDF5);
        line(context, "Нажми SHIFT, чтобы", xs[1] + 9, ys[1] + 62, 0xFFB6C4D4);
        line(context, "выйти из шкафа.", xs[1] + 9, ys[1] + 75, 0xFFB6C4D4);

        // Killer tools
        panel(context, xs[2], ys[1], colW, h, 0xFFFF6B55);
        heading(context, "ЕСЛИ РЯДОМ МАНЬЯК", xs[2] + 9, ys[1] + 8, 0xFFFF9988);
        line(context, "Не беги по прямой.", xs[2] + 9, ys[1] + 31, 0xFFE6EDF5);
        line(context, "Используй доски и", xs[2] + 9, ys[1] + 44, 0xFFE6EDF5);
        line(context, "окружение для побега.", xs[2] + 9, ys[1] + 57, 0xFFE6EDF5);
        line(context, "Дубина оглушает его", xs[2] + 9, ys[1] + 75, 0xFFB6C4D4);

        // Generator alert
        panel(context, xs[0], ys[2], colW, h, 0xFFFF4D4D);
        heading(context, "ШУМ ПОСЛЕ РЕМОНТА", xs[0] + 9, ys[2] + 8, 0xFFFF7777);
        line(context, "Маньяк видит подсказку", xs[0] + 9, ys[2] + 31, 0xFFE6EDF5);
        line(context, "о генераторе 4 секунды.", xs[0] + 9, ys[2] + 44, 0xFFE6EDF5);
        line(context, "Игроки рядом светятся", xs[0] + 9, ys[2] + 62, 0xFFFFD36A);
        line(context, "2 секунды в радиусе 5 м.", xs[0] + 9, ys[2] + 75, 0xFFFFD36A);

        // Heal / help
        panel(context, xs[1], ys[2], colW, h, 0xFF43D17D);
        heading(context, "КОМАНДНАЯ РАБОТА", xs[1] + 9, ys[2] + 8, 0xFF72E8B5);
        line(context, "Помогай раненым игрокам.", xs[1] + 9, ys[2] + 31, 0xFFE6EDF5);
        line(context, "Отвлекай маньяка, пока", xs[1] + 9, ys[2] + 44, 0xFFE6EDF5);
        line(context, "союзники чинят генератор.", xs[1] + 9, ys[2] + 57, 0xFFE6EDF5);
        line(context, "Не бросай команду!", xs[1] + 9, ys[2] + 75, 0xFF72E8B5);

        // Finish
        panel(context, xs[2], ys[2], colW, h, 0xFFB65CFF);
        heading(context, "ВЫЖИВАНИЕ — КОМАНДА", xs[2] + 9, ys[2] + 8, 0xFFD5A0FF);
        line(context, "Чини 5 генераторов,", xs[2] + 9, ys[2] + 31, 0xFFE6EDF5);
        line(context, "открывай ворота и беги.", xs[2] + 9, ys[2] + 44, 0xFFE6EDF5);
        line(context, "Следи за союзниками.", xs[2] + 9, ys[2] + 62, 0xFFB6C4D4);
        line(context, "Удачи. Не попадись!", xs[2] + 9, ys[2] + 75, 0xFFFFD36A);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}