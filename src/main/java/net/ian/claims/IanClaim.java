package net.ian.claims;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ian.claims.command.ClaimCommand;
import net.ian.claims.util.ClaimEvents;
import net.ian.claims.util.ClaimManager;
import net.ian.claims.util.ClaimNotifier;
import net.minecraft.server.MinecraftServer;
import static net.ian.claims.util.ClaimManager.server;

public class IanClaim implements ModInitializer, DedicatedServerModInitializer {

    @Override
    public void onInitialize() {
        // Client-side initialization if needed
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ClaimCommand.register(dispatcher);
        });
        // Register tick event for notifications
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getPlayerManager().getPlayerList().forEach(ClaimNotifier::checkAndNotify);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ClaimManager.initialize(server);
            System.out.println("[IanClaims] Server started - claims system ready");
        });
            ClaimEvents.register();
            System.out.println("[IanClaims] Ian's Land Claims mod loaded!");
    }

    @Override
    public void onInitializeServer() {
        ClaimManager.initialize(server);
        System.out.println("[IanClaims] Server started - claims system ready");
    }
}