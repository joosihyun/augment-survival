package com.augmentsurvival.client;

import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.network.ClientStateCache;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.Map;

public class AugmentHudOverlay implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // 1. 좌측 상단: 게임 목표 & 달성률 + 보유 시너지 HUD
        int x = 8;
        int y = 8;

        if (ClientStateCache.gameActive) {
            int boxWidth = 175;
            context.fill(x - 3, y - 3, x + boxWidth, y + 36, 0xAA101018);
            context.drawBorder(x - 3, y - 3, boxWidth + 3, 39, 0xFFFFAA00);

            context.drawItem(new ItemStack(ClientStateCache.currentGoal.getIconItem()), x, y);
            String roleTag = ClientStateCache.isParticipant ? "§a[참여자]" : "§7[관전자]";
            context.drawTextWithShadow(textRenderer, "§e목표: §f" + ClientStateCache.currentGoal.getDisplayName(), x + 20, y + 1, 0xFFFFFF);

            float ratio = Math.min(1.0f, (float) ClientStateCache.currentProgress / ClientStateCache.targetProgress);
            int percent = Math.round(ratio * 100.0f);
            String progStr = ClientStateCache.currentProgress + "/" + ClientStateCache.targetProgress + " (" + percent + "%) " + roleTag;
            context.drawTextWithShadow(textRenderer, progStr, x + 20, y + 12, 0xCCCCCC);

            // 진행도 바
            int barX = x;
            int barY = y + 25;
            int barW = boxWidth - 6;
            context.fill(barX, barY, barX + barW, barY + 5, 0xFF333333);
            context.fill(barX, barY, barX + Math.round(barW * ratio), barY + 5, 0xFF55FF55);

            y += 44;

            // 대기 중인 증강 선택 알림 (창 숨기기 상태일 때)
            if (!ClientStateCache.pendingChoices.isEmpty()) {
                context.fill(x - 3, y - 2, x + boxWidth, y + 13, 0xCC3B2400);
                context.drawBorder(x - 3, y - 2, boxWidth + 3, 15, 0xFFFFD700);
                context.drawTextWithShadow(textRenderer, "§6§l! 증강 선택 대기 중 §f[G키로 열기]", x + 2, y + 2, 0xFFFFFF);
                y += 18;
            }

            // 좌측 상단: 시너지 목록 및 스택 수
            if (!ClientStateCache.synergies.isEmpty()) {
                int synHeight = 14 + ClientStateCache.synergies.size() * 14;
                context.fill(x - 3, y - 2, x + boxWidth, y + synHeight, 0x99121622);
                context.drawTextWithShadow(textRenderer, "§b[활성 시너지]", x, y + 1, 0xFFFFFF);
                y += 13;

                for (Map.Entry<SynergyType, Integer> entry : ClientStateCache.synergies.entrySet()) {
                    SynergyType syn = entry.getKey();
                    int count = entry.getValue();
                    String tierColor = count >= 4 ? "§6" : (count == 3 ? "§d" : (count == 2 ? "§a" : "§7"));
                    String label = tierColor + syn.getDisplayName() + " [" + count + "/4]";
                    context.drawTextWithShadow(textRenderer, label, x + 4, y + 1, syn.getColor());
                    y += 14;
                }
            }
        } else {
            context.fill(x - 3, y - 3, x + 170, y + 14, 0x99101018);
            context.drawTextWithShadow(textRenderer, "§7증강 야생 대기 중 (/augment start)", x, y + 1, 0xCCCCCC);
        }

        // 2. 우측 하단: 액티브 필살기 슬롯 (최대 4개) & 쿨타임 HUD
        if (!ClientStateCache.activeSlots.isEmpty()) {
            int slotSize = 26;
            int gap = 4;
            int count = ClientStateCache.activeSlots.size();
            int totalW = count * slotSize + (count - 1) * gap;
            int startX = screenWidth - totalW - 10;
            int startY = screenHeight - slotSize - 24;

            context.drawTextWithShadow(textRenderer, "§e필살기 [R발동 / V전환]", startX, startY - 11, 0xFFFFFF);

            for (int i = 0; i < count; i++) {
                ClientStateCache.ActiveSlotEntry slot = ClientStateCache.activeSlots.get(i);
                int sx = startX + i * (slotSize + gap);
                boolean selected = (i == ClientStateCache.selectedActiveIndex);

                context.fill(sx, startY, sx + slotSize, startY + slotSize, selected ? 0xCC2D2208 : 0xAA14141C);
                context.drawBorder(sx, startY, slotSize, slotSize, selected ? 0xFFFFD700 : 0xFF777777);

                // 바닐라 아이템 아이콘 렌더링
                context.drawItem(new ItemStack(slot.augment().getIconItem()), sx + 5, startY + 5);

                if (slot.cooldownTicks() > 0) {
                    context.fill(sx + 1, startY + 1, sx + slotSize - 1, startY + slotSize - 1, 0x99000000);
                    String cdText = String.format("%.0fs", Math.ceil(slot.cooldownTicks() / 20.0));
                    context.drawCenteredTextWithShadow(textRenderer, cdText, sx + slotSize / 2, startY + 9, 0xFFFF5555);
                } else {
                    context.drawCenteredTextWithShadow(textRenderer, "§aR", sx + slotSize - 5, startY + 16, 0x55FF55);
                }
            }
        }
    }
}
