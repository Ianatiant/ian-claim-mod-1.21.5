package net.ian.claims.data;

import com.google.gson.annotations.SerializedName;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LandClaim {

    @SerializedName("owner_uuid")
    private String ownerUUID;

    @SerializedName("owner_name")
    private String ownerName;

    @SerializedName("land_name")
    private final String landName;

    private final int x1, z1, x2, z2;
    private final int size;

    @SerializedName("trusted_players")
    private final Set<String> trustedPlayers = new HashSet<>();

    public LandClaim(String ownerUUID,String ownerName, String landName, int x, int z, int size) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.landName = landName;
        this.size = size;
        this.x1 = x - size/2;
        this.z1 = z - size/2;
        this.x2 = x1 + size - 1;
        this.z2 = z1 + size - 1;
    }

    // Getters
    public String getOwnerName(){return ownerName;}
    public String getOwnerUUID() { return ownerUUID; }
    public String getLandName() { return landName; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    public int getSize() { return size; }

    /**
     * @return Set of trusted player UUIDs as strings
     */
    public Set<String> getTrustedPlayers() {
        return trustedPlayers;
    }

    /**
     * Adds a player to the trusted list
     * @param playerUUID The UUID of the player to trust
     * @return true if the player was added, false if already trusted
     */
    public boolean addTrustedPlayer(String playerUUID) {
        return trustedPlayers.add(playerUUID);
    }

    /**
     * Adds a player to the trusted list
     * @param playerUUID The UUID of the player to trust
     * @return true if the player was removed, false if wasn't trusted
     */
    public boolean removeTrustedPlayer(String playerUUID) {
        return trustedPlayers.remove(playerUUID);
    }

    /**
     * Checks if a player is trusted in this claim
     * @param playerUUID The UUID of the player to check
     * @return true if the player is trusted
     */
    public boolean isTrusted(String playerUUID) {
        return trustedPlayers.contains(playerUUID);
    }

    public boolean contains(int x, int z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    /**
     * Checks if a player can modify this claim (owner or trusted)
     * @param playerUUID The UUID of the player to check
     * @return true if the player has modification rights
     */
    public boolean canModify(String playerUUID) {
        return ownerUUID.equals(playerUUID) || isTrusted(playerUUID);
    }

    /**
     * Gets the center X coordinate of the claim
     */
    public int getCenterX() {
        return x1 + size/2;
    }

    /**
     * Gets the center Z coordinate of the claim
     */
    public int getCenterZ() {
        return z1 + size/2;
    }
    public void transferOwnership(String newOwnerUuid,String newOwnerName){
        setOwnerUUID(newOwnerUuid);
        setOwnerName(newOwnerName);
        //this.trusterPlayers.clear();
    }
    public void setOwnerUUID(String ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

}