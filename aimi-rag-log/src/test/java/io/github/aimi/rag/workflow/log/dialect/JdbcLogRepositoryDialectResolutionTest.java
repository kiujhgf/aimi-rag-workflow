package io.github.aimi.rag.workflow.log.dialect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JdbcLogRepositoryDialectResolutionTest {

    static Stream<Arguments> jdbcUrls() {
        return Stream.of(
                arguments("jdbc:h2:mem:test", H2LogRepositoryDialect.class),
                arguments("jdbc:mysql://localhost/test", MysqlLogRepositoryDialect.class),
                arguments("jdbc:mariadb://localhost/test", MysqlLogRepositoryDialect.class),
                arguments("jdbc:postgresql://localhost/test", PostgresLogRepositoryDialect.class),
                arguments("jdbc:sqlserver://localhost;databaseName=test", SqlServerLogRepositoryDialect.class),
                arguments("jdbc:oracle:thin:@localhost:1521:test", OracleLogRepositoryDialect.class),
                arguments("jdbc:sqlite:test.db", SqliteLogRepositoryDialect.class));
    }

    @ParameterizedTest
    @MethodSource("jdbcUrls")
    void shouldDetectDialectFromJdbcUrlAndCloseConnection(String url, Class<?> expectedType) {
        AtomicBoolean closed = new AtomicBoolean();

        JdbcLogRepositoryDialect dialect = JdbcLogRepositoryDialect.from(dataSource(url, closed));

        assertEquals(expectedType, dialect.getClass());
        assertTrue(closed.get());
    }

    @Test
    void shouldFallBackToGenericDialectAndWarnForUnknownUrl() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            JdbcLogRepositoryDialect dialect = JdbcLogRepositoryDialect.from(
                    dataSource("jdbc:unknown:test", new AtomicBoolean()));

            assertInstanceOf(GenericLogRepositoryDialect.class, dialect);
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("Unrecognized JDBC URL")));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void shouldWrapConnectionFailure() {
        SQLException failure = new SQLException("connection failed");
        DataSource dataSource = proxy(DataSource.class, (p, method, args) -> {
            if (method.getName().equals("getConnection")) {
                throw failure;
            }
            return defaultValue(method.getReturnType());
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JdbcLogRepositoryDialect.from(dataSource));

        assertEquals("Could not detect dialect from datasource", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    void shouldWrapMetadataFailureAndCloseConnection() {
        SQLException failure = new SQLException("metadata failed");
        AtomicBoolean closed = new AtomicBoolean();
        DataSource dataSource = dataSourceWithMetadataFailure(failure, closed);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JdbcLogRepositoryDialect.from(dataSource));

        assertSame(failure, exception.getCause());
        assertTrue(closed.get());
    }

    @Test
    void shouldPreferExplicitDialectAndWarnOnMismatch() {
        PostgresLogRepositoryDialect explicit = new PostgresLogRepositoryDialect();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            JdbcLogRepositoryDialect resolved = JdbcLogRepositoryDialect.resolve(
                    dataSource("jdbc:h2:mem:test", new AtomicBoolean()), explicit);

            assertSame(explicit, resolved);
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("differs from detected dialect")));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void shouldKeepExplicitDialectWhenDetectionFails() {
        PostgresLogRepositoryDialect explicit = new PostgresLogRepositoryDialect();
        DataSource failingDataSource = proxy(DataSource.class, (p, method, args) -> {
            if (method.getName().equals("getConnection")) {
                throw new SQLException("connection failed");
            }
            return defaultValue(method.getReturnType());
        });

        assertSame(explicit, JdbcLogRepositoryDialect.resolve(failingDataSource, explicit));
    }

    private static DataSource dataSource(String url, AtomicBoolean closed) {
        DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (p, method, args) -> {
            if (method.getName().equals("getURL")) {
                return url;
            }
            return defaultValue(method.getReturnType());
        });
        Connection connection = connection(metadata, closed);
        return dataSource(connection);
    }

    private static DataSource dataSourceWithMetadataFailure(SQLException failure, AtomicBoolean closed) {
        Connection connection = proxy(Connection.class, (p, method, args) -> {
            if (method.getName().equals("getMetaData")) {
                throw failure;
            }
            if (method.getName().equals("close")) {
                closed.set(true);
                return null;
            }
            if (method.getName().equals("isClosed")) {
                return closed.get();
            }
            return defaultValue(method.getReturnType());
        });
        return dataSource(connection);
    }

    private static Connection connection(DatabaseMetaData metadata, AtomicBoolean closed) {
        return proxy(Connection.class, (p, method, args) -> {
            if (method.getName().equals("getMetaData")) {
                return metadata;
            }
            if (method.getName().equals("close")) {
                closed.set(true);
                return null;
            }
            if (method.getName().equals("isClosed")) {
                return closed.get();
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static DataSource dataSource(Connection connection) {
        return proxy(DataSource.class, (p, method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        logger().detachAppender(appender);
        appender.stop();
    }

    private static ch.qos.logback.classic.Logger logger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(JdbcLogRepositoryDialect.class);
    }
}
