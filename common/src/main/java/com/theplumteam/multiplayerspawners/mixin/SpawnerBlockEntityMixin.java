package com.theplumteam.multiplayerspawners.mixin;

import com.google.common.collect.Sets;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.6 {
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stores, with each spawner, who disabled it and whether a player placed it. The data lives in
 * the spawner's own block entity data under "MultiplayerSpawnersData", so it moves with it.
 */
@Mixin(BaseSpawner.class)
public class SpawnerBlockEntityMixin implements SpawnerDataAccessor {

    @Unique
    private static final String multiplayerspawners$DATA = "MultiplayerSpawnersData";
    @Unique
    private static final String multiplayerspawners$DISABLED_PLAYERS = "DisabledPlayers";
    @Unique
    private static final String multiplayerspawners$PLAYER_PLACED = "IsPlayerPlaced";
    @Unique
    private static final String multiplayerspawners$DISABLED_TIMES = "DisabledAt";

    @Unique
    private final Set<String> multiplayerspawners$disabledPlayers = Sets.newHashSet();

    @Unique
    private final Map<String, Long> multiplayerspawners$disabledTimes = new HashMap<>();

    @Unique
    private boolean multiplayerspawners$isPlayerPlaced = false;

    @Override
    public Set<String> multiplayerspawners$getDisabledPlayers() {
        return this.multiplayerspawners$disabledPlayers;
    }

    @Override
    public void multiplayerspawners$setDisabledPlayers(Set<String> players) {
        this.multiplayerspawners$disabledPlayers.clear();
        this.multiplayerspawners$disabledPlayers.addAll(players);
    }

    @Override
    public Map<String, Long> multiplayerspawners$getDisabledTimes() {
        return this.multiplayerspawners$disabledTimes;
    }

    @Override
    public boolean multiplayerspawners$isPlayerPlaced() {
        return this.multiplayerspawners$isPlayerPlaced;
    }

    @Override
    public void multiplayerspawners$setPlayerPlaced(boolean value) {
        this.multiplayerspawners$isPlayerPlaced = value;
    }

    @Unique
    private boolean multiplayerspawners$hasData() {
        return !this.multiplayerspawners$disabledPlayers.isEmpty() || this.multiplayerspawners$isPlayerPlaced;
    }

    //? if >=1.21.6 {
    @Inject(method = "load", at = @At("RETURN"))
    private void multiplayerspawners$onLoad(Level level, BlockPos pos, ValueInput input, CallbackInfo ci) {
        this.multiplayerspawners$disabledPlayers.clear();
        this.multiplayerspawners$disabledTimes.clear();
        this.multiplayerspawners$isPlayerPlaced = false;
        input.child(multiplayerspawners$DATA).ifPresent(data -> {
            data.listOrEmpty(multiplayerspawners$DISABLED_PLAYERS, Codec.STRING).forEach(this.multiplayerspawners$disabledPlayers::add);
            data.child(multiplayerspawners$DISABLED_TIMES).ifPresent(times -> this.multiplayerspawners$disabledPlayers.forEach(
                    player -> times.getLong(player).ifPresent(time -> this.multiplayerspawners$disabledTimes.put(player, time))));
            this.multiplayerspawners$isPlayerPlaced = data.getBooleanOr(multiplayerspawners$PLAYER_PLACED, false);
        });
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void multiplayerspawners$onSave(ValueOutput output, CallbackInfo ci) {
        if (multiplayerspawners$hasData()) {
            ValueOutput data = output.child(multiplayerspawners$DATA);
            if (!this.multiplayerspawners$disabledPlayers.isEmpty()) {
                ValueOutput.TypedOutputList<String> players = data.list(multiplayerspawners$DISABLED_PLAYERS, Codec.STRING);
                this.multiplayerspawners$disabledPlayers.forEach(players::add);
                ValueOutput times = data.child(multiplayerspawners$DISABLED_TIMES);
                this.multiplayerspawners$disabledTimes.forEach(times::putLong);
            }
            if (this.multiplayerspawners$isPlayerPlaced) {
                data.putBoolean(multiplayerspawners$PLAYER_PLACED, true);
            }
        }
    }
    //?} else {
    /*@Inject(method = "load(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void multiplayerspawners$onLoad(Level level, BlockPos pos, CompoundTag nbt, CallbackInfo ci) {
        this.multiplayerspawners$disabledPlayers.clear();
        this.multiplayerspawners$disabledTimes.clear();
        this.multiplayerspawners$isPlayerPlaced = false;
        //? if >=1.21.5 {
        nbt.getCompound(multiplayerspawners$DATA).ifPresent(data -> {
            ListTag players = data.getListOrEmpty(multiplayerspawners$DISABLED_PLAYERS);
            for (int i = 0; i < players.size(); i++) {
                players.getString(i).ifPresent(this.multiplayerspawners$disabledPlayers::add);
            }
            data.getCompound(multiplayerspawners$DISABLED_TIMES).ifPresent(times -> this.multiplayerspawners$disabledPlayers.forEach(
                    player -> times.getLong(player).ifPresent(time -> this.multiplayerspawners$disabledTimes.put(player, time))));
            this.multiplayerspawners$isPlayerPlaced = data.getBooleanOr(multiplayerspawners$PLAYER_PLACED, false);
        });
        //?} else {
        /^if (nbt.contains(multiplayerspawners$DATA, 10)) {
            CompoundTag data = nbt.getCompound(multiplayerspawners$DATA);
            ListTag players = data.getList(multiplayerspawners$DISABLED_PLAYERS, 8);
            for (int i = 0; i < players.size(); i++) {
                this.multiplayerspawners$disabledPlayers.add(players.getString(i));
            }
            CompoundTag times = data.getCompound(multiplayerspawners$DISABLED_TIMES);
            for (String player : this.multiplayerspawners$disabledPlayers) {
                if (times.contains(player, 4)) {
                    this.multiplayerspawners$disabledTimes.put(player, times.getLong(player));
                }
            }
            this.multiplayerspawners$isPlayerPlaced = data.getBoolean(multiplayerspawners$PLAYER_PLACED);
        }
        ^///?}
    }

    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void multiplayerspawners$onSave(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
        if (multiplayerspawners$hasData()) {
            CompoundTag data = new CompoundTag();
            if (!this.multiplayerspawners$disabledPlayers.isEmpty()) {
                ListTag players = new ListTag();
                for (String playerName : this.multiplayerspawners$disabledPlayers) {
                    players.add(StringTag.valueOf(playerName));
                }
                data.put(multiplayerspawners$DISABLED_PLAYERS, players);
                CompoundTag times = new CompoundTag();
                this.multiplayerspawners$disabledTimes.forEach(times::putLong);
                data.put(multiplayerspawners$DISABLED_TIMES, times);
            }
            if (this.multiplayerspawners$isPlayerPlaced) {
                data.putBoolean(multiplayerspawners$PLAYER_PLACED, true);
            }
            nbt.put(multiplayerspawners$DATA, data);
        }
    }
    *///?}
}
