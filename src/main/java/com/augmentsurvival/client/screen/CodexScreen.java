package com.augmentsurvival.client.screen;

import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.network.ClientStateCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

public class CodexScreen extends Screen {
    private AugmentType selectedAugment = AugmentType.values()[0];

    public CodexScreen() {
        super(Text.literal("증강 도감"));
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("닫기"), button -> this.close())
                .dimensions(this.width / 2 - 45, this.height - 30, 90, 20).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            AugmentType[] all = AugmentType.values();
            int cols = 5;
            int cellSize = 36;
            int gap = 8;
            int startX = 24;
            int startY = 44;

            for (int i = 0; i < all.length; i++) {
                int r = i / cols;
                int c = i % cols;
                int x = startX + c * (cellSize + gap);
                int y = startY + r * (cellSize + gap);
                if (mouseX >= x && mouseX <= x + cellSize && mouseY >= y && mouseY <= y + cellSize) {
                    selectedAugment = all[i];
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, "§b§l[증강 도감] §7미발견 / 후보 등장 / 획득 완료", this.width / 2, 16, 0xFFFFFF);

        AugmentType[] all = AugmentType.values();
        int cols = 5;
        int cellSize = 36;
        int gap = 8;
        int startX = 24;
        int startY = 44;

        for (int i = 0; i < all.length; i++) {
            AugmentType aug = all[i];
            int r = i / cols;
            int c = i % cols;
            int x = startX + c * (cellSize + gap);
            int y = startY + r * (cellSize + gap);

            boolean obtained = ClientStateCache.everObtained.contains(aug);
            boolean seen = ClientStateCache.seenInPool.contains(aug);

            int bg = obtained ? 0xCC1E3820 : (seen ? 0xCC2E2818 : 0xCC181818);
            int border = (aug == selectedAugment) ? 0xFFFFFFFF : (obtained ? aug.getRarity().getBorderColor() : (seen ? 0xFF888855 : 0xFF444444));

            context.fill(x, y, x + cellSize, y + cellSize, bg);
            context.drawBorder(x, y, cellSize, cellSize, border);

            ItemStack icon = (obtained || seen) ? new ItemStack(aug.getIconItem()) : new ItemStack(Items.GRAY_DYE);
            context.drawItem(icon, x + 10, y + 10);
        }

        // 우측 상세 정보 패널
        int panelX = 250;
        int panelY = 44;
        int panelW = Math.max(150, this.width - panelX - 20);
        int panelH = 165;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC12141E);
        context.drawBorder(panelX, panelY, panelW, panelH, 0xFF5599FF);

        boolean obtained = ClientStateCache.everObtained.contains(selectedAugment);
        boolean seen = ClientStateCache.seenInPool.contains(selectedAugment);

        if (!obtained && !seen) {
            context.drawTextWithShadow(this.textRenderer, "§7§l??? (미발견 증강)", panelX + 10, panelY + 12, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer, "§8등급: " + selectedAugment.getRarity().getDisplayName(), panelX + 10, panelY + 28, 0x888888);
            context.drawTextWithShadow(this.textRenderer, "§7게임 플레이 중 레벨업 후보로 등장하면 해금됩니다.", panelX + 10, panelY + 48, 0x999999);
        } else {
            String statusBadge = obtained ? "§a[획득 완료]" : "§e[후보 등장 - 미보유]";
            context.drawItem(new ItemStack(selectedAugment.getIconItem()), panelX + 10, panelY + 10);
            context.drawTextWithShadow(this.textRenderer, selectedAugment.getRarity().getFormattingCode() + "§l" + selectedAugment.getDisplayName(), panelX + 32, panelY + 10, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, statusBadge, panelX + 32, panelY + 22, 0xFFFFFF);

            String synStr = selectedAugment.getSynergies().stream()
                    .map(SynergyType::getDisplayName)
                    .collect(Collectors.joining(", "));
            context.drawTextWithShadow(this.textRenderer, "§b시너지: §f" + synStr, panelX + 10, panelY + 40, 0xFFFFFF);

            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(selectedAugment.getDescription()), panelW - 20);
            int ly = panelY + 56;
            for (OrderedText line : lines) {
                context.drawTextWithShadow(this.textRenderer, line, panelX + 10, ly, 0xDDDDDD);
                ly += 11;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
