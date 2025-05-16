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
                        .executes(context -> claimLand(context, 16))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> claimLand(context, 16))
                        )
                )
                .then(CommandManager.literal("32x32")
                        .executes(context -> claimLand(context, 32))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> claimLand(context, 32))
                        )
                )
        );
    }

    private static int claimLand(CommandContext<ServerCommandSource> context, int size) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        int x = player.getBlockX();
        int z = player.getBlockZ();

        LandClaim claim = new LandClaim(
                player.getUuid().toString(),
                x - (size/2),
                z - (size/2),
                size
        );

        ClaimManager.addClaim(claim);

        player.sendMessage(Text.literal(
                String.format("Successfully claimed %dx%d area at %d, %d",
                        size, size, x, z)
        ), false);

        return Command.SINGLE_SUCCESS;
    }
}