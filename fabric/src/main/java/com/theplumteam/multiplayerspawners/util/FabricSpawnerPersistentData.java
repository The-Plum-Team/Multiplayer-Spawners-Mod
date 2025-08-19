package com.theplumteam.multiplayerspawners.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric implementation of spawner persistent data using SavedData.
 * This class handles saving and loading spawner states to/from NBT data
 * that persists across server restarts and world reloads.
 */
public class FabricSpawnerPersistentData extends SpawnerPersistentData {

    private static final String DATA_NAME = "multiplayerspawners_spawner_data";
    private final SavedData savedData;

    private FabricSpawnerPersistentData(SavedData savedData) {
        this.savedData = savedData;
    }

    /**
     * Gets or creates the spawner persistent data for the given server
     * @param server The minecraft server instance
     * @return The persistent data instance
     */
    public static FabricSpawnerPersistentData getOrCreate(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();

        // FIXED: Use the vanilla/Forge computeIfAbsent signature which is more robust
        // This avoids relying on the Fabric-specific SavedData.Factory class.
        return storage.computeIfAbsent(
                FabricSpawnerPersistentDataImpl::load,
                FabricSpawnerPersistentDataImpl::new,
                DATA_NAME
        ).getWrapper();
    }

    @Override
    public void setDirty() {
        if (savedData != null) {
            savedData.setDirty();
        }
    }

    /**
     * Internal implementation class that extends SavedData and handles NBT serialization
     */
    private static class FabricSpawnerPersistentDataImpl extends SavedData {
        private final FabricSpawnerPersistentData wrapper;

        public FabricSpawnerPersistentDataImpl() {
            this.wrapper = new FabricSpawnerPersistentData(this);
        }

        public FabricSpawnerPersistentData getWrapper() {
            return wrapper;
        }

        /**
         * Loads data from NBT when the world starts up
         */
        public static FabricSpawnerPersistentDataImpl load(CompoundTag tag) {
            FabricSpawnerPersistentDataImpl data = new FabricSpawnerPersistentDataImpl();

            CompoundTag disabledSpawnersTag = tag.getCompound("disabled_spawners");
            for (String key : disabledSpawnersTag.getAllKeys()) {
                ListTag playerList = disabledSpawnersTag.getList(key, 8); // 8 is StringTag type
                if (!playerList.isEmpty()) {
                    ConcurrentHashMap.KeySetView<String, Boolean> players = ConcurrentHashMap.newKeySet();
                    for (int i = 0; i < playerList.size(); i++) {
                        players.add(playerList.getString(i));
                    }
                    data.wrapper.disabledSpawners.put(key, players);
                }
            }
            return data;
        }

        /**
         * Saves data to NBT when the world shuts down
         */
        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag disabledSpawnersTag = new CompoundTag();
            for (Map.Entry<String, ConcurrentHashMap.KeySetView<String, Boolean>> entry : wrapper.disabledSpawners.entrySet()) {
                ListTag playerList = new ListTag();
                entry.getValue().forEach(playerName -> playerList.add(StringTag.valueOf(playerName)));
                if (!playerList.isEmpty()) {
                    disabledSpawnersTag.put(entry.getKey(), playerList);
                }
            }
            tag.put("disabled_spawners", disabledSpawnersTag);
            return tag;
        }
    }
}