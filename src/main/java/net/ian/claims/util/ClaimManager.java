package net.ian.claims.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.ian.claims.data.LandClaim;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {
    // Persistent storage
    private static final File CLAIMS_FILE = new File("./config/ian_claims/claims.json");
    private static final File NAME_CACHE_FILE = new File("./config/ian_claims/name_cache.json");
    private static List<LandClaim> claims = new ArrayList<>();
    private static final Map<UUID, String> nameCache = new HashMap<>();
    private static final Gson GSON = new Gson();

    // Player tracking
    private static final Map<String, Set<UUID>> playersInClaims = new ConcurrentHashMap<>();
    private static MinecraftServer server;

    // Initialize on class load
    static {
        loadAllData();
    }

    /* ========== SERVER REFERENCE ========== */
    public static void setServer(MinecraftServer server) {
        ClaimManager.server = server;
    }

    /* ========== DATA PERSISTENCE ========== */
    private static void loadAllData() {
        loadClaims();
        loadNameCache();
    }

    private static void loadClaims() {
        if (!CLAIMS_FILE.exists()) return;

        try (Reader reader = new FileReader(CLAIMS_FILE)) {
            List<LandClaim> loaded = GSON.fromJson(reader, new TypeToken<List<LandClaim>>(){}.getType());
            if (loaded != null) {
                claims = loaded;
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to load claims: " + e.getMessage());
        }
    }

    private static void loadNameCache() {
        if (!NAME_CACHE_FILE.exists()) return;

        try (Reader reader = new FileReader(NAME_CACHE_FILE)) {
            Map<UUID, String> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, String>>(){}.getType());
            if (loaded != null) {
                nameCache.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to load name cache: " + e.getMessage());
        }
    }

    public static void saveAllData() {
        saveClaims();
        saveNameCache();
    }

    public static void saveClaims() {
        try {
            CLAIMS_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(CLAIMS_FILE)) {
                GSON.toJson(claims, writer);
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to save claims: " + e.getMessage());
        }
    }

    private static void saveNameCache() {
        try {
            NAME_CACHE_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(NAME_CACHE_FILE)) {
                GSON.toJson(nameCache, writer);
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to save name cache: " + e.getMessage());
        }
    }

    /* ========== CLAIM MANAGEMENT ========== */
    public static boolean addClaim(LandClaim claim) {
        if (hasOverlappingClaims(claim)) {
            return false;
        }
        claims.add(claim);
        saveClaims();
        return true;
    }

    public static boolean removeClaim(LandClaim claim) {
        boolean removed = claims.remove(claim);
        if (removed) {
            saveClaims();
        }
        return removed;
    }

    public static List<LandClaim> getPlayerClaims(String playerUUID) {
        return claims.stream()
                .filter(claim -> claim.getOwnerUUID().equals(playerUUID))
                .toList();
    }

    /* ========== CLAIM VALIDATION ========== */
    public static boolean canClaimArea(int x, int z, int size) {
        int x1 = x - size/2;
        int z1 = z - size/2;
        int x2 = x1 + size - 1;
        int z2 = z1 + size - 1;
        return !hasOverlappingClaims(x1, z1, x2, z2);
    }

    private static boolean hasOverlappingClaims(LandClaim newClaim) {
        return hasOverlappingClaims(newClaim.getX1(), newClaim.getZ1(),
                newClaim.getX2(), newClaim.getZ2());
    }

    private static boolean hasOverlappingClaims(int x1, int z1, int x2, int z2) {
        return claims.stream().anyMatch(claim ->
                rangesOverlap(x1, x2, claim.getX1(), claim.getX2()) &&
                        rangesOverlap(z1, z2, claim.getZ1(), claim.getZ2())
        );
    }

    private static boolean rangesOverlap(int a1, int a2, int b1, int b2) {
        return a1 <= b2 && a2 >= b1;
    }

    /* ========== PLAYER NAME RESOLUTION ========== */
    private static void cachePlayerName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.isEmpty()) {
            nameCache.put(uuid, name);
            // Don't save immediately to reduce I/O
        }
    }

    private static String resolveOwnerName(String ownerUUID) {
        try {
            UUID uuid = UUID.fromString(ownerUUID);

            // 1. Check online players first
            if (server != null) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    String name = player.getName().getString();
                    cachePlayerName(uuid, name);
                    return name;
                }
            }

            // 2. Check our persistent cache
            String cachedName = nameCache.get(uuid);
            if (cachedName != null) {
                return cachedName;
            }

        } catch (IllegalArgumentException e) {
            System.err.println("[Claims] Invalid UUID format: " + ownerUUID);
        }

        // 3. Final fallback
        return "[" + ownerUUID.substring(0, 8) + "]";
    }

    /* ========== PLAYER TRACKING ========== */
    public static void checkPlayerEntry(ServerPlayerEntity player) {
        // Cache this player's name immediately
        cachePlayerName(player.getUuid(), player.getName().getString());

        // Process claim entry notifications
        for (LandClaim claim : claims) {
            if (claim.contains(player.getBlockX(), player.getBlockZ())) {
                String claimKey = claim.getOwnerUUID() + ":" + claim.getX1() + ":" + claim.getZ1();
                Set<UUID> playersInClaim = playersInClaims.computeIfAbsent(claimKey, k -> new HashSet<>());

                if (playersInClaim.add(player.getUuid())) {
                    notifyEnteringPlayer(player, claim);
                }
            }
        }
    }

    private static void notifyEnteringPlayer(ServerPlayerEntity player, LandClaim claim) {
        if (player.getUuid().toString().equals(claim.getOwnerUUID())) return;

        String ownerName = resolveOwnerName(claim.getOwnerUUID());

        player.sendMessage(Text.literal(
                String.format("§6[§dClaims§6] §eThis land belongs to §b%s§e (%dx%d at §a[%d, %d]§e)",
                        ownerName,
                        claim.getSize(),
                        claim.getSize(),
                        claim.getX1(),
                        claim.getZ1())
        ), false);
    }

    public static void checkPlayerExit(ServerPlayerEntity player) {
        playersInClaims.values().forEach(set -> set.remove(player.getUuid()));
    }

    /* ========== UTILITY METHODS ========== */
    public static Optional<LandClaim> getClaimAt(int x, int z) {
        return claims.stream()
                .filter(claim -> claim.contains(x, z))
                .findFirst();
    }

    public static Optional<LandClaim> getClaimAt(int x, int z, String ownerUUID) {
        return claims.stream()
                .filter(claim -> claim.contains(x, z) && claim.getOwnerUUID().equals(ownerUUID))
                .findFirst();
    }

    public static List<LandClaim> getAllClaims() {
        return Collections.unmodifiableList(claims);
    }

    public static void onServerTick() {
        // Auto-save name cache every 30 minutes (36000 ticks)
        if (server != null && server.getTicks() % 36000 == 0) {
            saveNameCache();
        }
    }
}