package com.theplumteam.multiplayerspawners.mixin;

import com.google.common.collect.Sets;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(BaseSpawner.class)
public class SpawnerBlockEntityMixin implements SpawnerDataAccessor {

    @Unique
    private final Set<String> multiplayerspawners$disabledPlayers = Sets.newHashSet();

    @Override
    public Set<String> multiplayerspawners$getDisabledPlayers() {
        return this.multiplayerspawners$disabledPlayers;
    }

    @Override
    public void multiplayerspawners$setDisabledPlayers(Set<String> players) {
        this.multiplayerspawners$disabledPlayers.clear();
        this.multiplayerspawners$disabledPlayers.addAll(players);
    }

    // FIXED: The signature of the target `load` method is load(Level, BlockPos, CompoundTag).
    // We must match these parameters exactly for the mixin to apply correctly.
    // To be extra safe, we specify the full method signature in the 'method' attribute.
    @Inject(method = "load(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void multiplayerspawners$onLoad(Level level, BlockPos pos, CompoundTag nbt, CallbackInfo ci) {
        this.multiplayerspawners$disabledPlayers.clear();
        if (nbt.contains("MultiplayerSpawnersData", 10)) {
            CompoundTag ourTag = nbt.getCompound("MultiplayerSpawnersData");
            ListTag players = ourTag.getList("DisabledPlayers", 8); // 8 = String NBT Type
            for (int i = 0; i < players.size(); i++) {
                this.multiplayerspawners$disabledPlayers.add(players.getString(i));
            }
        }
    }

    // This method is correct because SpawnerBlockEntity calls the save(CompoundTag) version.
    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void multiplayerspawners$onSave(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
        if (!this.multiplayerspawners$disabledPlayers.isEmpty()) {
            CompoundTag ourTag = new CompoundTag();
            ListTag players = new ListTag();
            for (String playerName : this.multiplayerspawners$disabledPlayers) {
                players.add(StringTag.valueOf(playerName));
            }
            ourTag.put("DisabledPlayers", players);
            nbt.put("MultiplayerSpawnersData", ourTag);
        }
    }
}