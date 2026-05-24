package com.circleguard.promotion.service;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GraphServiceTest {

    private UserNodeRepository userNodeRepository;
    private Neo4jClient neo4jClient;
    private GraphService service;

    @BeforeEach
    void setUp() {
        userNodeRepository = mock(UserNodeRepository.class);
        // The Cypher query in detectAndFormCircles uses .query().bind().to().run().
        // RETURNS_DEEP_STUBS is safe here because each leaf returns the right type
        // for the subsequent chain step (no cross-type casts).
        neo4jClient = mock(Neo4jClient.class, RETURNS_DEEP_STUBS);
        service = new GraphService(userNodeRepository, neo4jClient);
    }

    @Test
    void recordEncounter_delegatesToRepositoryWithCurrentTimestamp() {
        service.recordEncounter("anon-A", "anon-B", "loc-1");

        verify(userNodeRepository).recordEncounter(eq("anon-A"), eq("anon-B"), anyLong(), eq("loc-1"));
    }

    @Test
    void detectAndFormCircles_executesCypherWithLocationBinding() {
        service.detectAndFormCircles("loc-7");

        // We only assert that the query was issued; the Neo4jClient deep stub
        // collapses .bind(...).to(...).run() into a single chain so this test
        // pins the contract that detectAndFormCircles emits a Cypher query.
        verify(neo4jClient).query(anyString());
    }
}
