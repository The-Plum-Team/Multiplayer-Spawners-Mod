package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.event.CommandHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * What mining a spawner does. A player's own mining reaches {@link #hit} from
 * ServerPlayerGameModeMixin. Other mods break blocks for a player without it, firing only their
 * loader's block break event, which passes them to {@link #onModBreak}: the extra blocks of an area
 * tool (Tinkers' Construct's hammers), a spell that breaks blocks (Ars Nouveau's Break, cast with a
 * fake player that carries the caster's UUID), a machine's fake player. An explosion reaches
 * {@link #onExplosion} with the blocks it is about to destroy.
 */
public final class SpawnerBreaking {

    private static final String DUNGEON_PICKAXE_ID = "composite_material:dungeon_pickaxe";

    /**
     * The blocks players are mining through ServerPlayerGameMode right now, whose break events are
     * their own. An entry of an earlier tick is stale: another mod cancelled that break before it
     * returned.
     */
    private static final Deque<Mining> MINING = new ArrayDeque<>();

    private record Mining(Player player, BlockPos pos, long gameTime) {}

    private SpawnerBreaking() {}

    /** ServerPlayerGameMode lets the player's break of this block go on (vanilla decides the rest). */
    public static void enterGameModeBreak(ServerPlayer player, BlockPos pos) {
        long now = player.level().getGameTime();
        MINING.removeIf(mining -> mining.gameTime() != now);
        MINING.push(new Mining(player, pos.immutable(), now));
    }

    public static void leaveGameModeBreak() {
        MINING.poll();
    }

    /**
     * A block break event: a mod breaking a block for a player, or the player's own mining, which
     * {@link #hit} has decided already.
     *
     * @return whether the break must be cancelled: the block stays
     */
    public static boolean onModBreak(Level level, BlockPos pos, Player breaker) {
        if (level.isClientSide() || breaker == null) {
            return false;
        }
        for (Mining mining : MINING) {
            if (mining.player() == breaker && mining.pos().equals(pos) && mining.gameTime() == level.getGameTime()) {
                return false;
            }
        }
        return keepFor(level, pos, breaker);
    }

    /**
     * An explosion about to destroy {@code blocks}: the spawners among them stay. One that a player
     * caused (TNT they lit, a fireball they sent back) is a hit for that player, like a mod's break;
     * any other one leaves the spawner as it is. A spawner that breaks like a vanilla one still breaks.
     */
    public static void onExplosion(Level level, Explosion explosion, List<BlockPos> blocks) {
        if (level.isClientSide()) {
            return;
        }
        LivingEntity cause = destroysBlocks(explosion) ? explosion.getIndirectSourceEntity() : null;
        blocks.removeIf(pos -> {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SpawnerBlock) && !OneShotSpawners.isOneShot(state)) {
                return false;
            }
            return keepFor(level, pos, cause instanceof Player player ? player : null);
        });
    }

    /** Whether an explosion destroys the blocks it reaches (a wind charge only triggers them). */
    private static boolean destroysBlocks(Explosion explosion) {
        //? if >=1.21 {
        Explosion.BlockInteraction interaction = explosion.getBlockInteraction();
        return interaction == Explosion.BlockInteraction.DESTROY || interaction == Explosion.BlockInteraction.DESTROY_WITH_DECAY;
        //?} else {
        /*return explosion.interactsWithBlocks();
        *///?}
    }

    /**
     * A block that another mod or an explosion breaks for {@code breaker}, or for nobody (null).
     *
     * @return whether the block stays
     */
    private static boolean keepFor(Level level, BlockPos pos, Player breaker) {
        ServerPlayer player = breaker == null ? null : owner(level, breaker);
        if (player != null) {
            boolean kept = hit(player, level, pos, false);
            if (kept) {
                Constants.debug("Kept the spawner at {} that a mod or an explosion broke for {}", pos, player.getName().getString());
            }
            return kept;
        }
        // Nobody to disable it for (a machine, a creeper): it stays as it is, unless it breaks like vanilla.
        BlockState state = level.getBlockState(pos);
        if (OneShotSpawners.isOneShot(state)) {
            return true;
        }
        return isMultiplayerSpawner(level, pos) && !isPlacedTreatedAsVanilla(level, pos);
    }

    /**
     * The player a break is for: the breaker, or the online player whose UUID a mod's fake player
     * carries. Null for a fake player of nobody online in that level.
     */
    private static ServerPlayer owner(Level level, Player breaker) {
        if (level.getServer() == null) {
            return null;
        }
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(breaker.getUUID());
        return online != null && online.level() == level ? online : null;
    }

    /**
     * A player's hit on the block at pos.
     *
     * @param predicted whether the player's client showed the block breaking (its own mining): the
     *                  effects then go to the others only
     * @return whether the block stays (the caller cancels the break)
     */
    public static boolean hit(ServerPlayer player, Level world, BlockPos pos, boolean predicted) {
        BlockState blockState = world.getBlockState(pos);
        Player effectsSource = predicted ? player : null;

        // A survival player mining a single-use spawner uses it up for themselves only.
        if (OneShotSpawners.onMined(world, pos, player)) {
            world.levelEvent(effectsSource, Constants.BREAK_EFFECTS, pos, Block.getId(blockState));
            player.connection.send(new ClientboundBlockUpdatePacket(pos, blockState));
            return true;
        }

        if (!isMultiplayerSpawner(world, pos)) {
            return false;
        }
        boolean allowBreaking = ModConfig.SERVER.allowSpawnerBreakMessage.get();
        boolean isCreative = player.isCreative();
        int requiredAttempts = ModConfig.SERVER.requiredBreakAttempts.get();

        if (isPlacedTreatedAsVanilla(world, pos)) {
            if (isDungeonPickaxe(player.getMainHandItem())) {
                dropEmptySpawner(world, pos, player);
            }
            return false;
        }

        // A tool made to move spawners (item tag multiplayerspawners:breaks_spawners) breaks it like
        // vanilla, for everyone: the tool's own mod decides what drops.
        if (ModConfig.SERVER.breakWithSpawnerTools.get() && player.getMainHandItem().is(SpawnerStateManager.BREAKS_SPAWNERS)) {
            Constants.debug("Player {} breaks the spawner at {} with a spawner tool", player.getName().getString(), pos);
            return false;
        }

        boolean canBreak = allowBreaking || isCreative;
        boolean firstHit = !SpawnerStateManager.isSpawnerDisabledByAnyone(world, pos);
        if (firstHit) {
            // The first player to disable it gets what breaking it would have given.
            if (isDungeonPickaxe(player.getMainHandItem())) {
                dropEmptySpawner(world, pos, player);
            }
            // Breaking's own effects included: the players around see and hear it break.
            dropSpawnerRewards(world, pos, player);
            if (!predicted) {
                // Those effects leave out the player, whose client did not show them this time.
                player.connection.send(new ClientboundLevelEventPacket(Constants.BREAK_EFFECTS, pos, Block.getId(blockState), false));
            }
        } else {
            world.levelEvent(effectsSource, Constants.BREAK_EFFECTS, pos, Block.getId(blockState));
        }
        boolean newForPlayer = disable(world, pos, player);

        if (!newForPlayer) {
            // A further hit: the break link.
            sendClickableBreakMessage(player, pos, canBreak);
        } else if (firstHit) {
            sendRespawnNotice(player);
            // Two hits are the default: one-hit mode shows the break link at once.
            if (requiredAttempts == 1) {
                sendClickableBreakMessage(player, pos, canBreak);
            }
        } else {
            // Others had disabled it already.
            sendClickableBreakMessage(player, pos, canBreak);
            Notices.send(player, Component.translatable("message.multiplayerspawners.no_longer_spawns").withStyle(ChatFormatting.RED));
            sendRespawnNotice(player);
        }
        return true;
    }

    /**
     * A spawner with the vanilla spawner logic (or a subclass, as Apothic Spawners'): other mods'
     * spawner blocks with their own block entity, such as Brass Amber Battle Towers' tower spawners,
     * break as their mod intends.
     */
    private static boolean isMultiplayerSpawner(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SpawnerBlock && level.getBlockEntity(pos) instanceof SpawnerBlockEntity;
    }

    private static boolean isPlacedTreatedAsVanilla(Level level, BlockPos pos) {
        return ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get()
                && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity
                && ((SpawnerDataAccessor) spawnerBlockEntity.getSpawner()).multiplayerspawners$isPlayerPlaced();
    }

    private static boolean isDungeonPickaxe(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return DUNGEON_PICKAXE_ID.equals(itemId.toString());
    }

    private static void dropEmptySpawner(Level level, BlockPos pos, ServerPlayer player) {
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
    private static boolean disable(Level level, BlockPos pos, ServerPlayer breaker) {
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
                    Notices.send(serverPlayer, Component.translatable("message.multiplayerspawners.disabled_by", breaker.getName()).withStyle(ChatFormatting.GOLD));
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
            Notices.send(breaker, Component.translatable("message.multiplayerspawners.group_disabled", playersAffected).withStyle(ChatFormatting.GOLD));
        }
        return newForBreaker;
    }

    /** Tells the player when the spawner they disabled becomes active again, if it ever does. */
    private static void sendRespawnNotice(ServerPlayer player) {
        int minutes = ModConfig.SERVER.spawnerRespawnMinutes.get();
        if (minutes > 0) {
            Notices.send(player, Component.translatable(minutes == 1 ? "message.multiplayerspawners.respawn_in.minute" : "message.multiplayerspawners.respawn_in.minutes", minutes).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void dropSpawnerRewards(Level level, BlockPos pos, ServerPlayer player) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof SpawnerBlock) {
            blockState.getBlock().playerWillDestroy(level, pos, blockState, player);
            blockState.getBlock().dropResources(blockState, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        }
    }

    private static ClickEvent runCommand(String command) {
        //? if >=1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    private static HoverEvent showText(Component text) {
        //? if >=1.21.5 {
        return new HoverEvent.ShowText(text);
        //?} else {
        /*return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        *///?}
    }

    private static void sendClickableBreakMessage(ServerPlayer player, BlockPos pos, boolean includeBreakLink) {
        MutableComponent explanationMessage = Component.translatable("message.multiplayerspawners.disabled").withStyle(ChatFormatting.LIGHT_PURPLE);

        if (includeBreakLink) {
            explanationMessage.append(Component.literal(" "));
            MutableComponent clickablePart = Component.translatable("message.multiplayerspawners.break_link")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(runCommand("/multiplayerspawners allow_break " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(showText(Component.translatable("message.multiplayerspawners.break_link.hover").withStyle(ChatFormatting.YELLOW)))
                    );
            explanationMessage.append(clickablePart);
        }
        // Operators also get the settings menu, where they can change how spawners behave.
        if (CommandHandler.isGamemaster(player.createCommandSourceStack()) && NetworkHandler.getInstance().canOpenSettings(player)) {
            explanationMessage.append(Component.literal(" "));
            explanationMessage.append(Component.translatable("message.multiplayerspawners.settings_link")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withBold(true)
                            .withClickEvent(runCommand("/multiplayerspawners settings"))
                            .withHoverEvent(showText(Component.translatable("message.multiplayerspawners.settings_link.hover").withStyle(ChatFormatting.YELLOW)))
                    ));
        }
        player.sendSystemMessage(explanationMessage);
    }
}
