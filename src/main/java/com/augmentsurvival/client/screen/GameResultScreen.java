package com.augmentsurvival.client.screen;

import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.network.ClientStateCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Map;

public class GameResultScreen extends Screen {
    private final long elapsedTicks;

    public GameResultScreen(long elapsedTicks) {
        super(Text.literal("증강 야생 - 결과 요약"));
        this.elapsedTicks = elapsedTicks;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a확인"), button -> this.close())
                .dimensions(this.width / 2 - 50, this.height - 32, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        int centerX = this.width / 2;

        long totalSeconds = elapsedTicks / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        context.drawCenteredTextWithShadow(this.textRenderer, "§6§l★ 목표 클리어! 결과 요약 ★", centerX, 16, 0xFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§e달성 목표: §f" + ClientStateCache.currentGoal.getDisplayName() + "   §b클리어 시간: §f" + minutes + "분 " + seconds + "초",
                centerX, 32, 0xFFFFFF);

        // 좌측: 획득한 증강 목록
        int leftX = 24;
        int y = 54;
        context.drawTextWithShadow(this.textRenderer, "§a[획득한 증강 (" + ClientStateCache.ownedAugments.size() + "개)]", leftX, y, 0xFFFFFF);
        y += 14;
        for (AugmentType aug : ClientStateCache.ownedAugments) {
            context.drawItem(new ItemStack(aug.getIconItem()), leftX, y - 3);
            context.drawTextWithShadow(this.textRenderer,
                    aug.getRarity().getFormattingCode() + "[" + aug.getRarity().getDisplayName() + "] §f" + aug.getDisplayName(),
                    leftX + 20, y + 1, 0xFFFFFF);
            y += 18;
        }

        // 우측: 달성한 시너지 요약
        int rightX = centerX + 10;
        int sy = 54;
        context.drawTextWithShadow(this.textRenderer, "§b[달성 시너지]", rightX, sy, 0xFFFFFF);
        sy += 14;
        for (Map.Entry<SynergyType, Integer> entry : ClientStateCache.synergies.entrySet()) {
            SynergyType syn = entry.getKey();
            int count = entry.getValue();
            context.drawTextWithShadow(this.textRenderer,
                    "§e" + syn.getDisplayName() + " (" + count + "스택): §7" + syn.getActiveTierSummary(count),
                    rightX, sy, 0xFFFFFF);
            sy += 14;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
