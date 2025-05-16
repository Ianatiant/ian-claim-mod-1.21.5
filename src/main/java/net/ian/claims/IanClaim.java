package net.ian.claims;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.ian.claims.command.ClaimCommand;
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
    }
}