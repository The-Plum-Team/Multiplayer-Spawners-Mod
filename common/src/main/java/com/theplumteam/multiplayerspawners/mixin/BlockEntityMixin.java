package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.util.OneShotData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.HashSet;
import java.util.Set;

/**
 * Stores {@link OneShotData} with the block entity, under "MultiplayerSpawnersOneShot", through the
 * methods a chunk saves and loads its block entities with ({@code saveWithFullMetadata} and
 * {@code loadStatic}), which take a plain compound tag on every version.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements OneShotData {

    @Unique
    private static final String multiplayerspawners$DATA = "MultiplayerSpawnersOneShot";
    @Unique
    private static final String multiplayerspawners$USED_BY = "UsedBy";
    @Unique
    private static final String multiplayerspawners$PRISTINE = "Pristine";

    /** Created when first needed: every block entity of the world carries this field. */
    @Unique
    private Set<String> multiplayerspawners$usedBy;

    @Unique
    private CompoundTag multiplayerspawners$pristine;

    @Override
    public Set<String> multiplayerspawners$getUsedBy() {
        if (this.multiplayerspawners$usedBy == null) {
            this.multiplayerspawners$usedBy = new HashSet<>();
        }
        return this.multiplayerspawners$usedBy;
    }

    @Override
    public CompoundTag multiplayerspawners$getPristine() {
        return this.multiplayerspawners$pristine;
    }

    @Override
    public void multiplayerspawners$setPristine(CompoundTag pristine) {
        this.multiplayerspawners$pristine = pristine;
    }

    //? if >=1.21 {
    @Inject(method = "saveWithFullMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void multiplayerspawners$onSave(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
    //?} else {
    /*@Inject(method = "saveWithFullMetadata()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void multiplayerspawners$onSave(CallbackInfoReturnable<CompoundTag> cir) {
    *///?}
        if ((this.multiplayerspawners$usedBy == null || this.multiplayerspawners$usedBy.isEmpty()) && this.multiplayerspawners$pristine == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        ListTag usedBy = new ListTag();
        this.multiplayerspawners$getUsedBy().forEach(player -> usedBy.add(StringTag.valueOf(player)));
        data.put(multiplayerspawners$USED_BY, usedBy);
        if (this.multiplayerspawners$pristine != null) {
            data.put(multiplayerspawners$PRISTINE, this.multiplayerspawners$pristine.copy());
        }
        cir.getReturnValue().put(multiplayerspawners$DATA, data);
    }

    //? if >=1.21 {
    @Inject(method = "loadStatic(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("RETURN"))
    private static void multiplayerspawners$onLoad(BlockPos pos, BlockState state, CompoundTag tag, HolderLookup.Provider registries, CallbackInfoReturnable<BlockEntity> cir) {
    //?} else {
    /*@Inject(method = "loadStatic(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("RETURN"))
    private static void multiplayerspawners$onLoad(BlockPos pos, BlockState state, CompoundTag tag, CallbackInfoReturnable<BlockEntity> cir) {
    *///?}
        if (!(cir.getReturnValue() instanceof OneShotData oneShot)) {
            return;
        }
        //? if >=1.21.5 {
        tag.getCompound(multiplayerspawners$DATA).ifPresent(data -> {
            ListTag usedBy = data.getListOrEmpty(multiplayerspawners$USED_BY);
            for (int i = 0; i < usedBy.size(); i++) {
                usedBy.getString(i).ifPresent(oneShot.multiplayerspawners$getUsedBy()::add);
            }
            data.getCompound(multiplayerspawners$PRISTINE).ifPresent(oneShot::multiplayerspawners$setPristine);
        });
        //?} else {
        /*if (tag.contains(multiplayerspawners$DATA, 10)) {
            CompoundTag data = tag.getCompound(multiplayerspawners$DATA);
            ListTag usedBy = data.getList(multiplayerspawners$USED_BY, 8);
            for (int i = 0; i < usedBy.size(); i++) {
                oneShot.multiplayerspawners$getUsedBy().add(usedBy.getString(i));
            }
            if (data.contains(multiplayerspawners$PRISTINE, 10)) {
                oneShot.multiplayerspawners$setPristine(data.getCompound(multiplayerspawners$PRISTINE));
            }
        }
        *///?}
    }
}
