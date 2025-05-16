package net.ian.claims;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ian.claims.command.ClaimCommand;
import net.ian.claims.event.BlockBreakHandler;
import net.ian.claims.event.PlayerMovementTracker;
import net.ian.claims.util.ClaimManager;

public class IanClaim implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ClaimCommand.register(dispatcher);
        });
        PlayerMovementTracker.register();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ClaimManager.saveClaims();
        }));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ClaimManager.setServer(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ClaimManager.onServerTick();
        });
        BlockBreakHandler.register();
    }
}