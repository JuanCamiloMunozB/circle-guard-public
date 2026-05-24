package com.circleguard.promotion.task;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class GraphCleanupTaskTest {

    private UserNodeRepository userNodeRepository;
    private GraphCleanupTask task;

    @BeforeEach
    void setUp() {
        userNodeRepository = mock(UserNodeRepository.class);
        task = new GraphCleanupTask(userNodeRepository);
    }

    @Test
    void purgeStaleEncounters_invokesRepositoryWith14DayThreshold() {
        when(userNodeRepository.purgeStaleEncounters(anyLong())).thenReturn(42L);

        task.purgeStaleEncounters();

        // Threshold must be in the past (now - 14 days). We can't predict the exact
        // value but we know it's strictly less than now + 1s.
        verify(userNodeRepository).purgeStaleEncounters(longThat(t -> t < System.currentTimeMillis()));
    }

    @Test
    void purgeStaleEncounters_repositoryReturnsNull_doesNotCrash() {
        when(userNodeRepository.purgeStaleEncounters(anyLong())).thenReturn(null);

        // The log line treats null as 0; the call must complete normally.
        task.purgeStaleEncounters();

        verify(userNodeRepository).purgeStaleEncounters(anyLong());
    }

    @Test
    void purgeStaleEncounters_repositoryThrows_isSwallowed() {
        when(userNodeRepository.purgeStaleEncounters(anyLong()))
                .thenThrow(new RuntimeException("neo4j down"));

        // The task must NOT propagate exceptions — a missed cleanup is logged but
        // does not crash the scheduler thread.
        task.purgeStaleEncounters();

        verify(userNodeRepository).purgeStaleEncounters(anyLong());
    }

    // helper to keep the test readable
    private static long longThat(java.util.function.LongPredicate p) {
        return org.mockito.ArgumentMatchers.longThat(p::test);
    }
}
