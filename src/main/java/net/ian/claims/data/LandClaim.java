package net.ian.claims.data;

import java.util.Objects;

public class LandClaim {
    private final String ownerUUID;
    private final int x1, z1, x2, z2;
    private final int size;

    public LandClaim(String ownerUUID, int x, int z, int size) {
        this.ownerUUID = ownerUUID;
        this.size = size;
        this.x1 = x;
        this.z1 = z;
        this.x2 = x + size - 1;
        this.z2 = z + size - 1;
    }

    // Getter methods
    public String getOwnerUUID() {
        return ownerUUID;
    }

    public int getX1() {
        return x1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getZ2() {
        return z2;
    }

    public int getSize() {
        return size;
    }

    public boolean contains(int x, int z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LandClaim landClaim = (LandClaim) o;
        return x1 == landClaim.x1 &&
                z1 == landClaim.z1 &&
                x2 == landClaim.x2 &&
                z2 == landClaim.z2 &&
                Objects.equals(ownerUUID, landClaim.ownerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUUID, x1, z1, x2, z2);
    }
}