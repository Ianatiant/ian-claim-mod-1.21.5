package net.ian.claims.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimNotifier {
    private static final Map<UUID, BlockPos> lastPositions = new HashMap<>();

    public static void checkAndNotify(ServerPlayerEntity player) {
        BlockPos currentPos = player.getBlockPos();
        BlockPos lastPos = lastPositions.get(player.getUuid());

        if (lastPos == null || lastPos.getSquaredDistance(currentPos) > 16*16) {
            ClaimManager.getClaimAt(player).ifPresent(claim -> {
                player.sendMessage(
                        Text.literal("§6[IanClaims]§a Entered Land '")
                                .formatted(Formatting.YELLOW)
                                .append(Text.literal(claim.getLandName())
                                        .formatted(Formatting.GOLD, Formatting.BOLD))
                                .append(Text.literal("' owned by ")
                                        .formatted(Formatting.YELLOW))
                                .append(Text.literal(ClaimManager.getOwnerName(claim))
                                        .formatted(Formatting.GOLD)),
                        false
                );
            });
            lastPositions.put(player.getUuid(), currentPos);
        }
    }
}