package com.theplumteam.multiplayerspawners.util;

import java.util.Map;
import java.util.Set;

public interface SpawnerDataAccessor {
    Set<String> multiplayerspawners$getDisabledPlayers();
    void multiplayerspawners$setDisabledPlayers(Set<String> players);

    /** When each player disabled the spawner, in game time. */
    Map<String, Long> multiplayerspawners$getDisabledTimes();

    // Player-placed spawner tracking
    boolean multiplayerspawners$isPlayerPlaced();
    void multiplayerspawners$setPlayerPlaced(boolean value);
}