package net.ian.claims.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.ian.claims.data.LandClaim;
import net.ian.claims.util.ClaimManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ClaimCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("claimland")
                .then(CommandManager.literal("16x16")
                        .executes(context -> claimLand(context, 16, null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> claimLand(context, 16, EntityArgumentType.getPlayer(context, "player"))))
                )
                .then(CommandManager.literal("32x32")
                        .executes(context -> claimLand(context, 32, null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> claimLand(context, 32, EntityArgumentType.getPlayer(context, "player"))))
                )
        );
    }

    private static int claimLand(CommandContext<ServerCommandSource> context, int size, ServerPlayerEntity targetPlayer) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        ServerPlayerEntity owner = targetPlayer != null ? targetPlayer : player;
        int x = player.getBlockX();
        int z = player.getBlockZ();

        if (!ClaimManager.canClaimArea(x, z, size)) {
            player.sendMessage(Text.literal("§cThis area is already claimed!"), false);
            return 0;
        }

        LandClaim claim = new LandClaim(
                owner.getUuid().toString(),
                x - (size/2),
                z - (size/2),
                size
        );

        if (!ClaimManager.addClaim(claim)) {
            player.sendMessage(Text.literal("§cFailed to claim land (system error)"), false);
            return 0;
        }

        if (owner == player) {
            player.sendMessage(Text.literal(String.format(
                    "§aSuccessfully claimed %dx%d land at [%d, %d]",
                    size, size, x, z
            )), false);
        } else {
            player.sendMessage(Text.literal(String.format(
                    "§aClaimed %dx%d land at [%d, %d] for %s",
                    size, size, x, z, owner.getName().getString()
            )), false);
            owner.sendMessage(Text.literal(String.format(
                    "§a%s claimed %dx%d land for you at [%d, %d]",
                    player.getName().getString(), size, size, x, z
            )), false);
        }

        return Command.SINGLE_SUCCESS;
    }
}