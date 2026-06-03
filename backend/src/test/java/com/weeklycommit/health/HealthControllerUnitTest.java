package com.weeklycommit.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit-level coverage of the readiness branches that the integration test cannot
 * reach: a valid connection yields UP, an invalid one and a datasource failure
 * both yield 503 DOWN without leaking the cause.
 */
class HealthControllerUnitTest {

    @Test
    void reportsUpWhenConnectionIsValid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);

        var response = new HealthController(dataSource).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void reportsDownWhenConnectionIsInvalid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        var response = new HealthController(dataSource).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }

    @Test
    void reportsDownWhenDatasourceThrows() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("boom"));

        var response = new HealthController(dataSource).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }
}
