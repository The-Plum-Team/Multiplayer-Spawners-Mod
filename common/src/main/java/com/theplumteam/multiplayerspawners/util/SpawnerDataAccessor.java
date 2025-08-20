package com.theplumteam.multiplayerspawners.util;

import java.util.Set;

public interface SpawnerDataAccessor {
    Set<String> multiplayerspawners$getDisabledPlayers();
    void multiplayerspawners$setDisabledPlayers(Set<String> players);
}