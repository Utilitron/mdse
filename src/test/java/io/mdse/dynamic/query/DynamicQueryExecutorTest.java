package io.mdse.dynamic.query;

import io.mdse.dynamic.model.DynamicRecord;
import io.mdse.dynamic.repository.DynamicRepository;
import io.mdse.metadata.exception.MetadataNotFoundException;
import io.mdse.metadata.registry.SchemaRegistry;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.SortDirection;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.service.CoercionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicQueryExecutorTest {
    
    @Mock
    private DataSource dataSource;
    @Mock
    private SchemaRegistry schemaRegistry;
    @Mock
    private DynamicRepository repository;
    @Mock
    private CoercionService coercionService;
    
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;
    
    @InjectMocks
    private DynamicQueryExecutor executor;
    
    private static final String TABLE_NAME = "users";
    private static final String QUALIFIED_NAME = "public.users";
    
    @BeforeEach
    void setUp() throws Exception {
        // --- TableSchema mock ---
        TableSchema schema = mock(TableSchema.class);
        lenient().when(schema.getQualifiedName()).thenReturn(QUALIFIED_NAME);
        
        // Ensure any filter column name resolves to a non-null ColumnSchema mock.
        // This prevents null being passed around and ensures coercion is invoked.
        ColumnSchema anyColumn = mock(ColumnSchema.class);
        lenient().when(schema.getColumn(anyString())).thenReturn(anyColumn);
        
        // --- Registry ---
        lenient().when(schemaRegistry.getRequired(TABLE_NAME)).thenReturn(schema);
        
        // --- Database connections ---
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
        
        // --- Coercion service stub: pass through the raw value unchanged ---
        lenient().when(coercionService.coerce(any(), any(ColumnSchema.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
    
    @Test
    void search_shouldBuildSelectStarWithNoClauses() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        DynamicRecord mockRow = mock(DynamicRecord.class);
        when(resultSet.next()).thenReturn(true, false);
        when(repository.mapRow(any(TableSchema.class), eq(resultSet))).thenReturn(mockRow);
        
        List<DynamicRecord> results = executor.search(TABLE_NAME, spec);
        
        assertThat(results).containsExactly(mockRow);
        verify(connection).prepareStatement("SELECT * FROM " + QUALIFIED_NAME);
        verify(preparedStatement, never()).setObject(anyInt(), any());
    }
    
    @Test
    void search_shouldBuildWhereClauseWithMultipleFilters() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        spec.getFilters().add(new QuerySpecification.Filter("name", "=", "John"));
        spec.getFilters().add(new QuerySpecification.Filter("age", ">", 30));
        
        when(resultSet.next()).thenReturn(false);
        
        executor.search(TABLE_NAME, spec);
        
        verify(connection).prepareStatement("SELECT * FROM " + QUALIFIED_NAME + " WHERE name = ? AND age > ?");
        
        ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(preparedStatement, times(2)).setObject(indexCaptor.capture(), valueCaptor.capture());
        assertThat(indexCaptor.getAllValues()).containsExactly(1, 2);
        assertThat(valueCaptor.getAllValues()).containsExactly("John", 30);
    }
    
    @Test
    void search_shouldBuildOrderByClause() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        spec.getSorts().add(new QuerySpecification.Sort("name", SortDirection.ASC));
        spec.getSorts().add(new QuerySpecification.Sort("created_at", SortDirection.DESC));
        
        when(resultSet.next()).thenReturn(false);
        
        executor.search(TABLE_NAME, spec);
        
        verify(connection).prepareStatement("SELECT * FROM " + QUALIFIED_NAME + " ORDER BY name ASC, created_at DESC");
    }
    
    @Test
    void search_shouldApplyPagination() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        spec.setPage(2);
        spec.setSize(10);
        
        when(resultSet.next()).thenReturn(false);
        
        executor.search(TABLE_NAME, spec);
        
        verify(connection).prepareStatement("SELECT * FROM " + QUALIFIED_NAME + " LIMIT ? OFFSET ?");
        verify(preparedStatement).setObject(1, 10);
        verify(preparedStatement).setObject(2, 20);
    }
    
    @Test
    void search_shouldCombineAllClauses() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        spec.getFilters().add(new QuerySpecification.Filter("active", "=", true));
        spec.getSorts().add(new QuerySpecification.Sort("id", SortDirection.ASC));
        spec.setPage(0);
        spec.setSize(5);
        
        when(resultSet.next()).thenReturn(false);
        
        executor.search(TABLE_NAME, spec);
        
        verify(connection).prepareStatement("SELECT * FROM " + QUALIFIED_NAME + " WHERE active = ? ORDER BY id ASC LIMIT ? OFFSET ?");
        verify(preparedStatement).setObject(1, true);
        verify(preparedStatement).setObject(2, 5);
        verify(preparedStatement).setObject(3, 0);
    }
    
    @Test
    void search_shouldReturnMappedRowsFromRepository() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        DynamicRecord row1 = mock(DynamicRecord.class);
        DynamicRecord row2 = mock(DynamicRecord.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(repository.mapRow(any(TableSchema.class), eq(resultSet)))
                .thenReturn(row1)
                .thenReturn(row2);
        
        List<DynamicRecord> results = executor.search(TABLE_NAME, spec);
        
        assertThat(results).containsExactly(row1, row2);
    }
    
    @Test
    void search_shouldThrowRuntimeExceptionOnSQLException() throws Exception {
        when(preparedStatement.executeQuery()).thenThrow(new SQLException("connection lost"));
        
        assertThatThrownBy(() -> executor.search(TABLE_NAME, new QuerySpecification()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Query failed")
                .hasMessageContaining("SELECT * FROM");
    }
    
    @Test
    void search_shouldPropagateExceptionFromSchemaRegistry() {
        when(schemaRegistry.getRequired(TABLE_NAME)).thenThrow(new MetadataNotFoundException("Unknown table"));
        
        assertThatThrownBy(() -> executor.search(TABLE_NAME, new QuerySpecification()))
                .isInstanceOf(MetadataNotFoundException.class)
                .hasMessage("Unknown table");
        
        verifyNoInteractions(dataSource);
    }
    
    @Test
    void count_shouldReturnZeroWhenNoRows() throws Exception {
        when(resultSet.next()).thenReturn(false);
        
        long count = executor.count(TABLE_NAME, new QuerySpecification());
        
        assertThat(count).isEqualTo(0L);
        verify(connection).prepareStatement("SELECT COUNT(*) FROM " + QUALIFIED_NAME);
    }
    
    @Test
    void count_shouldReturnCountFromResultSet() throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(42L);
        
        long count = executor.count(TABLE_NAME, new QuerySpecification());
        
        assertThat(count).isEqualTo(42L);
    }
    
    @Test
    void count_shouldBuildWhereClauseWithFilters() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        spec.getFilters().add(new QuerySpecification.Filter("deleted", "=", false));
        spec.getFilters().add(new QuerySpecification.Filter("type", "=", "ADMIN"));
        when(resultSet.next()).thenReturn(false);
        
        executor.count(TABLE_NAME, spec);
        
        verify(connection).prepareStatement("SELECT COUNT(*) FROM " + QUALIFIED_NAME + " WHERE deleted = ? AND type = ?");
        verify(preparedStatement).setObject(1, false);
        verify(preparedStatement).setObject(2, "ADMIN");
    }
    
    @Test
    void count_shouldIgnoreSortsAndPagination() throws Exception {
        QuerySpecification spec = new QuerySpecification();
        spec.getFilters().add(new QuerySpecification.Filter("x", ">", 5));
        spec.getSorts().add(new QuerySpecification.Sort("x", SortDirection.DESC));
        spec.setPage(10);
        spec.setSize(100);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(7L);
        
        long count = executor.count(TABLE_NAME, spec);
        
        assertThat(count).isEqualTo(7L);
        
        // The SQL must not contain ORDER BY, LIMIT, OFFSET
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).doesNotContain("ORDER BY", "LIMIT", "OFFSET");
    }
    
    @Test
    void count_shouldThrowRuntimeExceptionOnSQLException() throws Exception {
        when(preparedStatement.executeQuery()).thenThrow(new SQLException("db error"));
        
        assertThatThrownBy(() -> executor.count(TABLE_NAME, new QuerySpecification()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Count query failed");
    }
}