// ServerPlayerGameModeMixin.java
package com.theplumteam.multiplayerspawners.mixin;

import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.Notices;
import com.theplumteam.multiplayerspawners.util.OneShotSpawners;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerPlayer player;

    @Unique
    private static final String DUNGEON_PICKAXE_ID = "composite_material:dungeon_pickaxe";

    @Unique
    private boolean multiplayerspawners$isDungeonPickaxe(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return DUNGEON_PICKAXE_ID.equals(itemId.toString());
    }

    @Unique
    private void multiplayerspawners$dropEmptySpawner(Level level, BlockPos pos, ServerPlayer player) {
        ItemStack spawnerItem = new ItemStack(Blocks.SPAWNER);
        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, spawnerItem);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
        Constants.debug("Dropped empty spawner for player {} at {} using dungeon pickaxe", player.getName().getString(), pos);
    }

    /**
     * Disables the spawner for the player who hit it and, with group disabling, for the players
     * near it who still had it active, tells the players who see it, and sends the notices.
     * Every hit does this, also on a spawner already disabled for the player or for others: a
     * player who came near since is disabled as well.
     *
     * @return whether it was newly disabled for the player who hit it
     */
    @Unique
    private boolean multiplayerspawners$disable(Level level, BlockPos pos, ServerPlayer breaker) {
        boolean newForBreaker = !SpawnerStateManager.isSpawnerDisabledForPlayer(level, pos, breaker.getName().getString());
        boolean changed = newForBreaker;
        if (newForBreaker) {
            SpawnerStateManager.disableSpawnerForPlayer(level, pos, breaker.getName().getString());
            Constants.debug("Spawner at {} has been disabled by player {}", pos, breaker.getName().getString());
        }

        int playersAffected = 0;
        if (ModConfig.SERVER.enableGroupDisabling.get()) {
            AABB searchArea = new AABB(pos).inflate(ModConfig.SERVER.groupDisablingRadius.get());
            for (Player nearby : level.getEntitiesOfClass(Player.class, searchArea)) {
                if (nearby instanceof ServerPlayer serverPlayer && serverPlayer != breaker && !serverPlayer.isSpectator()
                        && !SpawnerStateManager.isSpawnerDisabledForPlayer(level, pos, serverPlayer.getName().getString())) {
                    SpawnerStateManager.disableSpawnerForPlayer(level, pos, serverPlayer.getName().getString());
                    playersAffected++;
                    Notices.send(serverPlayer, Component.literal("A spawner in this area has been disabled by "
                            + breaker.getName().getString() + ".").withStyle(ChatFormatting.GOLD));
                }
            }
        }
        changed |= playersAffected > 0;

        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = Set.copyOf(accessor.multiplayerspawners$getDisabledPlayers());
            boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                    && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();
            if (changed) {
                NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos, disabledPlayers, treatedAsVanilla);
            } else {
                // Nothing changed: the player who hit it gets its state again all the same.
                NetworkHandler.getInstance().sendSpawnerStateToPlayer(breaker, pos, disabledPlayers, false, treatedAsVanilla);
            }
        }
        if (playersAffected > 0) {
            Notices.send(breaker, Component.literal("Group disabling: Spawner disabled for " + playersAffected
                    + " nearby player(s).").withStyle(ChatFormatting.GOLD));
        }
        return newForBreaker;
    }

    /** Tells the player when the spawner they disabled becomes active again, if it ever does. */
    @Unique
    private static void multiplayerspawners$sendRespawnNotice(ServerPlayer player) {
        int minutes = ModConfig.SERVER.spawnerRespawnMinutes.get();
        if (minutes > 0) {
            Notices.send(player, Component.literal("It will be active for you again in " + minutes
                    + (minutes == 1 ? " minute." : " minutes.")).withStyle(ChatFormatting.GRAY));
        }
    }

    @Unique
    private void multiplayerspawners$dropSpawnerRewards(Level level, BlockPos pos, ServerPlayer player) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof SpawnerBlock) {
            blockState.getBlock().playerWillDestroy(level, pos, blockState, player);
            blockState.getBlock().dropResources(blockState, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        }
    }

    @Unique
    private static ClickEvent multiplayerspawners$runCommand(String command) {
        //? if >=1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    @Unique
    private static HoverEvent multiplayerspawners$showText(Component text) {
        //? if >=1.21.5 {
        return new HoverEvent.ShowText(text);
        //?} else {
        /*return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        *///?}
    }

    @Unique
    private void multiplayerspawners$sendClickableBreakMessage(ServerPlayer player, BlockPos pos, boolean includeBreakLink) {
        MutableComponent explanationMessage = Component.literal("The spawner is disabled for you and nearby players. Other players will have it active so they can enjoy the dungeon.").withStyle(ChatFormatting.LIGHT_PURPLE);

        if (includeBreakLink) {
            explanationMessage.append(Component.literal(" "));
            MutableComponent clickablePart = Component.literal("[Click here to break it]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(multiplayerspawners$runCommand("/multiplayerspawners allow_break " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(multiplayerspawners$showText(Component.literal("Click to break this spawner").withStyle(ChatFormatting.YELLOW)))
                    );
            explanationMessage.append(clickablePart);
        }
        player.sendSystemMessage(explanationMessage);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onAttemptDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level world = this.player.level();
        BlockState blockState = world.getBlockState(pos);

        // A survival player mining a single-use spawner uses it up for themselves only.
        if (OneShotSpawners.onMined(world, pos, this.player)) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, blockState));
            cir.setReturnValue(false);
            return;
        }

        // Only spawners with the vanilla spawner logic (or a subclass, as Apothic Spawners'): other
        // mods' spawner blocks with their own block entity, such as Brass Amber Battle Towers' tower
        // spawners, break as their mod intends.
        if (blockState.getBlock() instanceof SpawnerBlock && world.getBlockEntity(pos) instanceof SpawnerBlockEntity) {
            boolean allowBreaking = ModConfig.SERVER.allowSpawnerBreakMessage.get();
            boolean isCreative = this.player.isCreative();
            int requiredAttempts = ModConfig.SERVER.requiredBreakAttempts.get();

            if (ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get()) {
                if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                    if (accessor.multiplayerspawners$isPlayerPlaced()) {
                        if (multiplayerspawners$isDungeonPickaxe(this.player.getMainHandItem())) {
                            multiplayerspawners$dropEmptySpawner(world, pos, this.player);
                        }
                        return;
                    }
                }
            }

            // A tool made to move spawners (item tag multiplayerspawners:breaks_spawners) breaks it like
            // vanilla, for everyone: the tool's own mod decides what drops.
            if (ModConfig.SERVER.breakWithSpawnerTools.get() && this.player.getMainHandItem().is(SpawnerStateManager.BREAKS_SPAWNERS)) {
                Constants.debug("Player {} breaks the spawner at {} with a spawner tool", this.player.getName().getString(), pos);
                return;
            }

            boolean canBreak = allowBreaking || isCreative;
            boolean firstHit = !SpawnerStateManager.isSpawnerDisabledByAnyone(world, pos);
            if (firstHit) {
                // The first player to disable it gets what breaking it would have given.
                if (multiplayerspawners$isDungeonPickaxe(this.player.getMainHandItem())) {
                    multiplayerspawners$dropEmptySpawner(world, pos, this.player);
                }
                multiplayerspawners$dropSpawnerRewards(world, pos, this.player);
            }
            boolean newForPlayer = multiplayerspawners$disable(world, pos, this.player);

            if (!newForPlayer) {
                // A further hit: the break link.
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
            } else if (firstHit) {
                multiplayerspawners$sendRespawnNotice(this.player);
                // Two hits are the default: one-hit mode shows the break link at once.
                if (requiredAttempts == 1) {
                    multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
                }
            } else {
                // Others had disabled it already.
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
                Notices.send(player, Component.literal("It will no longer spawn creatures while you are nearby.").withStyle(ChatFormatting.RED));
                multiplayerspawners$sendRespawnNotice(this.player);
            }
            // The server keeps the block (the client did not predict its break).
            cir.setReturnValue(false);
        }
    }
}
