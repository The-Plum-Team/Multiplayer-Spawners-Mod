package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps players who are not in Creative from changing a spawner's mob with a spawn egg when the
 * server turns that off ({@code spawnEggsChangeSpawners}). A spawner is one block for the whole
 * server, so the change would reach every player.
 *
 * <p>Only the server decides: the client never changes the spawner itself, it waits for the
 * server's block update.
 */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$keepSpawnerMob(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ModConfig.SERVER.spawnEggsChangeSpawners.get()
                || !(context.getPlayer() instanceof ServerPlayer player) || player.isCreative()
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.multiplayerspawners.spawn_eggs_creative_only")
                .withStyle(ChatFormatting.RED), true);
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
