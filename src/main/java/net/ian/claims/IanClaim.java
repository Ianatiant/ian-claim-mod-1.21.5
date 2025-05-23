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
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.ian.claims.util.ClaimManager.server;
public class IanClaim implements ModInitializer, DedicatedServerModInitializer {
    public static final String MOD_ID = "Ian-Claims";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);;

    @Override
    public void onInitialize() {

        // Client-side initialization if needed
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ClaimCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ClaimManager.initialize(server);
            server.sendMessage(Text.literal("[IanClaims] Server configuration loading."));

        });
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer ->{
            server.sendMessage(Text.literal("[IanClaims] Server stop detected, Saving claims."));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer ->{
            server.sendMessage(Text.literal("[IanClaims] Mod is ready to use."));
        });


            ClaimEvents.register();
            ClaimCommand.registerCommand();
            LOGGER.info("[IanClaims] Loaded Successfully.");

    }

    @Override
    public void onInitializeServer() {
        ClaimManager.initialize(server);
        System.out.println("[IanClaims] Server detected.");
    }
}