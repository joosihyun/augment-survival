package com.augmentsurvival.client.screen;

import com.augmentsurvival.model.AugmentRarity;
import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.network.ClientStateCache;
import com.augmentsurvival.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

public class AugmentSelectScreen extends Screen {
    private static final long CLICK_LOCK_MS = 500L; // 오클릭 방지 0.5초
    private long openedAtMs;

    private final int cardWidth = 135;
    private final int cardHeight = 185;
    private final int cardGap = 14;

    public AugmentSelectScreen() {
        super(Text.literal("증강 선택"));
    }

    @Override
    protected void init() {
        this.openedAtMs = System.currentTimeMillis();

        int centerX = this.width / 2;
        int bottomY = this.height - 36;

        // 리롤 버튼 (15% 확률로 등급 승급)
        String rerollLabel = ClientStateCache.rerollsRemaining > 0
                ? "§e리롤 (" + ClientStateCache.rerollsRemaining + "회) §7[15% 등급승급]"
                : "§8리롤 소진됨";
        ButtonWidget rerollBtn = ButtonWidget.builder(Text.literal(rerollLabel), button -> {
            if (isClickLocked() || ClientStateCache.rerollsRemaining <= 0) return;
            ClientPlayNetworking.send(ModPackets.REROLL_AUGMENT_C2S, PacketByteBufs.empty());
        }).dimensions(centerX - 155, bottomY, 175, 22).build();
        rerollBtn.active = ClientStateCache.rerollsRemaining > 0;
        this.addDrawableChild(rerollBtn);

        // 창 숨기기(접기) 버튼 -> 닫은 후 G키로 다시 오픈
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§7창 숨기기(접기) [G키로 복귀]"), button -> {
            if (isClickLocked()) return;
            this.close();
        }).dimensions(centerX + 30, bottomY, 155, 22).build());
    }

    private boolean isClickLocked() {
        return System.currentTimeMillis() - openedAtMs < CLICK_LOCK_MS;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isClickLocked()) {
            return true; // 0.5초 오클릭 방지
        }

        if (button == 0) {
            List<AugmentType> choices = ClientStateCache.pendingChoices;
            int totalW = choices.size() * cardWidth + Math.max(0, choices.size() - 1) * cardGap;
            int startX = (this.width - totalW) / 2;
            int startY = (this.height - cardHeight) / 2 - 10;

            for (int i = 0; i < choices.size(); i++) {
                int cx = startX + i * (cardWidth + cardGap);
                if (mouseX >= cx && mouseX <= cx + cardWidth && mouseY >= startY && mouseY <= startY + cardHeight) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(i);
                    ClientPlayNetworking.send(ModPackets.SELECT_AUGMENT_C2S, buf);
                    this.close();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, "§6§l증강을 선택하세요", this.width / 2, 18, 0xFFFFFF);
        if (isClickLocked()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "§c오클릭 방지 보호 중...", this.width / 2, 31, 0xFF6666);
        }

        List<AugmentType> choices = ClientStateCache.pendingChoices;
        int totalW = choices.size() * cardWidth + Math.max(0, choices.size() - 1) * cardGap;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - cardHeight) / 2 - 10;

        for (int i = 0; i < choices.size(); i++) {
            AugmentType aug = choices.get(i);
            AugmentRarity rarity = aug.getRarity();
            int cx = startX + i * (cardWidth + cardGap);
            boolean hovered = !isClickLocked() && mouseX >= cx && mouseX <= cx + cardWidth && mouseY >= startY && mouseY <= startY + cardHeight;

            // 카드 배경 및 등급별 테두리 색상
            context.fill(cx, startY, cx + cardWidth, startY + cardHeight, rarity.getBackgroundColor());
            int borderColor = hovered ? 0xFFFFFFFF : rarity.getBorderColor();
            context.drawBorder(cx, startY, cardWidth, cardHeight, borderColor);
            context.drawBorder(cx + 1, startY + 1, cardWidth - 2, cardHeight - 2, rarity.getBorderColor());

            // 등급 배지
            String rarityTag = rarity.getFormattingCode() + "[" + rarity.getDisplayName() + "]" + (aug.isActiveSkill() ? " §c[액티브]" : "");
            context.drawCenteredTextWithShadow(this.textRenderer, rarityTag, cx + cardWidth / 2, startY + 10, 0xFFFFFF);

            // 바닐라 아이템 아이콘 (중앙 상단)
            int iconX = cx + (cardWidth - 16) / 2;
            int iconY = startY + 28;
            context.fill(iconX - 4, iconY - 4, iconX + 20, iconY + 20, 0x66000000);
            context.drawItem(new ItemStack(aug.getIconItem()), iconX, iconY);

            // 증강 이름
            context.drawCenteredTextWithShadow(this.textRenderer, "§l" + aug.getDisplayName(), cx + cardWidth / 2, startY + 54, 0xFFFFFF);

            // 시너지 태그
            String synText = aug.getSynergies().stream()
                    .map(SynergyType::getDisplayName)
                    .collect(Collectors.joining(" / "));
            context.drawCenteredTextWithShadow(this.textRenderer, "§b#" + synText, cx + cardWidth / 2, startY + 68, 0x77DDFF);

            // 설명 줄바꿈 렌더링
            List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(aug.getDescription()), cardWidth - 16);
            int textY = startY + 86;
            for (OrderedText line : wrapped) {
                context.drawTextWithShadow(this.textRenderer, line, cx + 8, textY, 0xE0E0E0);
                textY += 10;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
