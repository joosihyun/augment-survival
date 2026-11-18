package com.augmentsurvival;

import com.augmentsurvival.command.AugmentCommand;
import com.augmentsurvival.logic.AugmentEventHandler;
import com.augmentsurvival.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AugmentSurvivalMod implements ModInitializer {
    public static final String MOD_ID = "augmentsurvival";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModPackets.registerServerReceivers();
        AugmentEventHandler.registerAll();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AugmentCommand.register(dispatcher)
        );
        LOGGER.info("Augment Survival (1.20.1 Fabric) initialized.");
    }
}
