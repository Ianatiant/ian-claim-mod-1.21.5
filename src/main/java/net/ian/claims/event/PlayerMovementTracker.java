package net.ian.claims.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ian.claims.util.ClaimManager;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

public class PlayerMovementTracker {
    private static final Map<UUID, int[]> lastPositions = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                int[] lastPos = lastPositions.get(uuid);
                int currentX = player.getBlockX();
                int currentZ = player.getBlockZ();

                if (lastPos == null || lastPos[0] != currentX || lastPos[1] != currentZ) {
                    ClaimManager.checkPlayerEntry(player);
                    lastPositions.put(uuid, new int[]{currentX, currentZ});
                }
            }
        });
    }
}