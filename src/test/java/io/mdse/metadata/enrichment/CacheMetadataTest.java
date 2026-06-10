import io.mdse.metadata.enrichment.CacheMetadata;
import io.mdse.metadata.enrichment.CacheStrategy;
import io.mdse.metadata.enrichment.FetchPlanMetadata;
import io.mdse.metadata.enrichment.FetchStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CacheMetadata Tests")
public class CacheMetadataTest {
    
    @Test
    @DisplayName("Should build cache metadata with all properties")
    void testBuildCacheMetadata() {
        UUID schemaId = UUID.randomUUID();
        
        CacheMetadata metadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .ttl(Duration.ofMinutes(15))
                .strategy(CacheStrategy.READ_THROUGH)
                .maxEntries(1000)
                .invalidationTrigger("user_updated")
                .invalidationTrigger("user_deleted")
                .cacheNulls(true)
                .build();
        
        assertThat(metadata.getTableSchemaId()).isEqualTo(schemaId);
        assertThat(metadata.isCacheable()).isTrue();
        assertThat(metadata.getTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(metadata.getStrategy()).isEqualTo(CacheStrategy.READ_THROUGH);
        assertThat(metadata.getMaxEntries()).isEqualTo(1000);
        assertThat(metadata.getInvalidationTriggers())
                .contains("user_updated", "user_deleted");
        assertThat(metadata.isCacheNulls()).isTrue();
    }
    
    @Test
    @DisplayName("Should have default values")
    void testDefaultValues() {
        UUID schemaId = UUID.randomUUID();
        
        CacheMetadata metadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .build();
        
        assertThat(metadata.isCacheable()).isTrue(); // Default
        assertThat(metadata.isCacheNulls()).isFalse(); // Default
    }
}
