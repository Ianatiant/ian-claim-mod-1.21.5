package net.ian.claims.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.ian.claims.data.LandClaim;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.ian.claims.util.ClaimManager.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClaimEvents {
    //private static final Map<String, IronGolemEntity> claimGolems = new HashMap<>();

    public static void register() {

        // Block breaking protection
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!ClaimManager.canModifyAt(serverPlayer, pos.getX(), pos.getZ())) {
                    player.sendMessage(Text.literal("§cYou can't break blocks here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Block placement protection
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                BlockPos pos = hitResult.getBlockPos();
                if (!ClaimManager.canModifyAt(serverPlayer, pos.getX(), pos.getZ())) {
                    player.sendMessage(Text.literal("§cYou can't place blocks here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Item usage protection (simplified without TypedActionResult)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!ClaimManager.canModifyAt(serverPlayer,
                        player.getBlockX(), player.getBlockZ())) {
                    player.sendMessage(Text.literal("§cYou can't use items here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Entity interaction protection
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!ClaimManager.canModifyAt(serverPlayer,
                        entity.getBlockX(), entity.getBlockZ())) {
                    player.sendMessage(Text.literal("§cYou can't attack entities here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });
        // 1. PREVENT EXPLOSION DAMAGE AT SOURCE
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof CreeperEntity creeper &&
                    ClaimManager.isInClaim(creeper.getBlockPos()) &&
                    source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                return false; // Block explosion damage
            }
            return true;
        });

        // 2. PREVENT FUSE IGNITION (Modern approach)
        ServerLivingEntityEvents.MOB_CONVERSION.register((original, converted, keepEquipment) -> {
            if (original instanceof CreeperEntity creeper &&
                    ClaimManager.isInClaim(creeper.getBlockPos())) {
                // Modern way to stop fuse without FUSE_TIME
                creeper.setFuseSpeed(-1); // Disables fuse progression
                creeper.setTarget(null); // Makes creeper passive
            }

        });

        // 3. ACTIVE PROTECTION (Tick-based)
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            world.getEntitiesByType(EntityType.CREEPER,
                            entity -> ClaimManager.isInClaim(entity.getBlockPos()))
                    .forEach(creeper -> {
                        // Triple protection
                        creeper.setFuseSpeed(-1);
                        creeper.setTarget(null);


                        // Emergency removal if fuse somehow started
                        if (creeper.getFuseSpeed() > 0) {
                            creeper.discard();
                        }
                    });
            world.getEntitiesByType(EntityType.ZOMBIE,zombieEntity -> ClaimManager.isInClaim(zombieEntity.getBlockPos()))
                    .forEach(zombieEntity -> {
                        zombieEntity.setTarget(null);
                        zombieEntity.setCustomName(Text.literal("Kevin"));
                        zombieEntity.setCustomNameVisible(true);
                    });
            world.getEntitiesByType(EntityType.PHANTOM,phantomEntity -> ClaimManager.isInClaim(phantomEntity.getBlockPos()))
                    .forEach(phantomEntity -> {
                        phantomEntity.setHealth(0);
                    });
            world.getEntitiesByType(EntityType.SKELETON,skeletonEntity -> ClaimManager.isInClaim(skeletonEntity.getBlockPos()))
                    .forEach(skeletonEntity -> {
                        skeletonEntity.setHealth(0);

                    });
            world.getEntitiesByType(EntityType.PILLAGER,pillagerEntity -> ClaimManager.isInClaim(pillagerEntity.getBlockPos()))
                    .forEach(pillagerEntity -> {
                        pillagerEntity.setHealth(0);
                    });
            world.getEntitiesByType(EntityType.WITCH,witchEntity -> ClaimManager.isInClaim(witchEntity.getBlockPos()))
                    .forEach(witchEntity -> {
                        witchEntity.setAttacking(false);
                        witchEntity.setHealth(0);

                    });

        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getPlayerManager().getPlayerList().forEach(player -> {
                ClaimEffects.applyClaimEffects(player);
                ClaimNotifier.checkAndNotify(player);
            });
        });


    }

}