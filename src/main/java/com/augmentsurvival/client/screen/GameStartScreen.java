package com.augmentsurvival.client.screen;

import com.augmentsurvival.model.GoalType;
import com.augmentsurvival.network.ClientStateCache;
import com.augmentsurvival.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class GameStartScreen extends Screen {
    private int selectedGoalIndex = 0; // 0 = 무작위 추첨, 1..N = GoalType.values()

    public GameStartScreen() {
        super(Text.literal("증강 야생 - 게임 설정"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 45;

        // 목표 선택 순환 버튼
        this.addDrawableChild(ButtonWidget.builder(getGoalButtonText(), button -> {
            selectedGoalIndex = (selectedGoalIndex + 1) % (GoalType.values().length + 1);
            button.setMessage(getGoalButtonText());
        }).dimensions(centerX - 110, topY, 220, 20).build());

        // 플레이어별 참여자 / 관전자 전환 버튼
        int rowY = topY + 38;
        for (ClientStateCache.LobbyPlayerEntry entry : ClientStateCache.lobbyPlayers) {
            boolean nextRoleParticipant = !entry.participant();
            Text roleLabel = Text.literal(entry.name() + " : " + (entry.participant() ? "§a참여자" : "§7관전자"));
            this.addDrawableChild(ButtonWidget.builder(roleLabel, button -> {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeUuid(entry.uuid());
                buf.writeBoolean(nextRoleParticipant);
                ClientPlayNetworking.send(ModPackets.SET_ROLE_C2S, buf);
            }).dimensions(centerX - 110, rowY, 220, 20).build());
            rowY += 24;
        }

        // 게임 시작 버튼
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a§l게임 시작"), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            boolean random = (selectedGoalIndex == 0);
            buf.writeBoolean(random);
            buf.writeString(random ? GoalType.ENDER_DRAGON.name() : GoalType.values()[selectedGoalIndex - 1].name());
            ClientPlayNetworking.send(ModPackets.START_GAME_C2S, buf);
            this.close();
        }).dimensions(centerX - 110, this.height - 42, 105, 22).build());

        // 도감 열기 버튼
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§b증강 도감"), button -> {
            if (this.client != null) {
                this.client.setScreen(new CodexScreen());
            }
        }).dimensions(centerX + 5, this.height - 42, 105, 22).build());
    }

    private Text getGoalButtonText() {
        if (selectedGoalIndex == 0) {
            return Text.literal("클리어 목표: §6무작위 추첨");
        }
        GoalType g = GoalType.values()[selectedGoalIndex - 1];
        return Text.literal("클리어 목표: §e" + g.getDisplayName());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, "§6§l[증강 야생] 로비 및 목표 설정", this.width / 2, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "§7클릭하여 참여자/관전자 역할을 전환할 수 있습니다.", this.width / 2, 70, 0xAAAAAA);

        if (selectedGoalIndex > 0) {
            GoalType g = GoalType.values()[selectedGoalIndex - 1];
            context.drawItem(new ItemStack(g.getIconItem()), this.width / 2 + 116, 47);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
