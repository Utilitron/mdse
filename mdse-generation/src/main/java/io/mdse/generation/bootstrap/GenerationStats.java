package io.mdse.generation.bootstrap;

import lombok.Builder;
import lombok.Value;

/**
 * Statistics about a generation session
 */
@Value
@Builder
class GenerationStats {
    int totalTables;
    int generatedEntities;
    int totalFields;
    int totalRelationships;
    long compilationTimeMs;
    long totalTimeMs;
    long totalBytecodeSize;
}

