package net.ian.claims.util;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ClaimPreview {
    private static final double PARTICLE_HEIGHT = 1.2; // Particles float slightly higher
    private static final int PARTICLES_PER_BLOCK = 2; // Particles per block segment
    private static final float PARTICLE_SPEED = 0.01f; // Slower, more visible particles

    private final World world;
    private final BlockPos corner1;
    private final BlockPos corner2;

    public ClaimPreview(World world, BlockPos corner1, BlockPos corner2) {
        this.world = world;
        this.corner1 = corner1;
        this.corner2 = corner2;
    }

    public void show(ServerPlayerEntity player) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        int y = player.getBlockPos().getY();

        // Calculate spacing based on claim size
        int xSteps = Math.max(1, (maxX - minX) / 8);
        int zSteps = Math.max(1, (maxZ - minZ) / 8);

        // Show full perimeter with increased density
        for (int x = minX; x <= maxX; x += xSteps) {
            spawnParticles(player, new Vec3d(x + 0.5, y + PARTICLE_HEIGHT, minZ + 0.5));
            spawnParticles(player, new Vec3d(x + 0.5, y + PARTICLE_HEIGHT, maxZ + 0.5));
        }

        for (int z = minZ; z <= maxZ; z += zSteps) {
            spawnParticles(player, new Vec3d(minX + 0.5, y + PARTICLE_HEIGHT, z + 0.5));
            spawnParticles(player, new Vec3d(maxX + 0.5, y + PARTICLE_HEIGHT, z + 0.5));
        }

        // Add corner highlights
        spawnParticleCluster(player, new Vec3d(minX + 0.5, y + PARTICLE_HEIGHT, minZ + 0.5));
        spawnParticleCluster(player, new Vec3d(minX + 0.5, y + PARTICLE_HEIGHT, maxZ + 0.5));
        spawnParticleCluster(player, new Vec3d(maxX + 0.5, y + PARTICLE_HEIGHT, minZ + 0.5));
        spawnParticleCluster(player, new Vec3d(maxX + 0.5, y + PARTICLE_HEIGHT, maxZ + 0.5));
    }

    private void spawnParticles(ServerPlayerEntity player, Vec3d pos) {
        player.getServerWorld().spawnParticles(
                ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                PARTICLES_PER_BLOCK,
                0.15,  // x spread
                0.15,  // y spread
                0.15,  // z spread
                PARTICLE_SPEED
        );
    }

    private void spawnParticleCluster(ServerPlayerEntity player, Vec3d pos) {
        for (int i = 0; i < 8; i++) {
            player.getServerWorld().spawnParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE, // More visible corner markers
                    pos.getX(),
                    pos.getY() + 0.2 * i,
                    pos.getZ(),
                    1,
                    0.05,
                    0.05,
                    0.05,
                    0.02
            );
        }
    }
}