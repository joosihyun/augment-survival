package com.augmentsurvival;

import com.augmentsurvival.client.AugmentHudOverlay;
import com.augmentsurvival.client.screen.AugmentSelectScreen;
import com.augmentsurvival.client.screen.CodexScreen;
import com.augmentsurvival.client.screen.GameResultScreen;
import com.augmentsurvival.client.screen.GameStartScreen;
import com.augmentsurvival.network.ClientStateCache;
import com.augmentsurvival.network.ModPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class AugmentSurvivalClient implements ClientModInitializer {
    private static KeyBinding openUiKey;
    private static KeyBinding useActiveKey;
    private static KeyBinding cycleActiveKey;

    @Override
    public void onInitializeClient() {
        // HUD 렌더링 등록
        HudRenderCallback.EVENT.register(new AugmentHudOverlay());

        // 단축키 등록 (G: 선택창 복귀/도감, R: 액티브 필살기 발동, V: 액티브 슬롯 전환)
        openUiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.augmentsurvival.open_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.augmentsurvival"
        ));
        useActiveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.augmentsurvival.use_active",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.augmentsurvival"
        ));
        cycleActiveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.augmentsurvival.cycle_active",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.augmentsurvival"
        ));

        // S2C 패킷 수신 등록
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SYNC_STATE_S2C, (client, handler, buf, responseSender) -> {
            ClientStateCache.readFromBuf(buf);
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OPEN_AUGMENT_SELECT_S2C, (client, handler, buf, responseSender) -> {
            client.execute(() -> client.setScreen(new AugmentSelectScreen()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OPEN_START_GUI_S2C, (client, handler, buf, responseSender) -> {
            client.execute(() -> client.setScreen(new GameStartScreen()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OPEN_CODEX_GUI_S2C, (client, handler, buf, responseSender) -> {
            client.execute(() -> client.setScreen(new CodexScreen()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.GAME_RESULT_S2C, (client, handler, buf, responseSender) -> {
            long elapsedTicks = buf.readLong();
            client.execute(() -> client.setScreen(new GameResultScreen(elapsedTicks)));
        });

        // 클라이언트 틱 단축키 처리
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (openUiKey.wasPressed()) {
                if (!ClientStateCache.pendingChoices.isEmpty()) {
                    client.setScreen(new AugmentSelectScreen());
                } else {
                    client.setScreen(new CodexScreen());
                }
            }

            while (cycleActiveKey.wasPressed()) {
                ClientStateCache.cycleActiveSlot();
            }

            while (useActiveKey.wasPressed()) {
                if (!ClientStateCache.activeSlots.isEmpty()) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(ClientStateCache.selectedActiveIndex);
                    ClientPlayNetworking.send(ModPackets.USE_ACTIVE_SKILL_C2S, buf);
                }
            }
        });
    }
}
