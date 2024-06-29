package net.hollowcube.luau.example;

import org.jetbrains.annotations.NotNull;

public interface World {

    // Properties

    @NotNull String name();


    // Functions

    @NotNull BlockType getBlock(@NotNull Vec3 pos);
    void setBlock(@NotNull Vec3 pos, @NotNull BlockType type);

    @NotNull Entity spawnEntity(@NotNull String entityType, @NotNull Vec3 pos, @NotNull Object properties);
}
