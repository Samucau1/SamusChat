package com.chatapp.chatapp_backend;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class DesktopTestDatabaseLifetimeTests {
    @Test void launcherDatabaseRetainsSchemaAndDataAfterAllConnectionsClose() throws Exception {
        String launcher = Files.readString(Path.of("..", "scripts", "start-desktop-test-api.ps1"));
        var settings = Pattern.compile(";MODE=PostgreSQL;DB_CLOSE_DELAY=-?\\d+;DB_CLOSE_ON_EXIT=FALSE").matcher(launcher);
        assertThat(settings.find()).as("Read actual launcher database options").isTrue();
        String url = "jdbc:h2:mem:desktop_lifetime_" + UUID.randomUUID() + settings.group();
        try {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE users (id INT PRIMARY KEY, username VARCHAR(50))");
                sql.execute("INSERT INTO users VALUES (1, 'synthetic-user')");
            }
            // A replacement connection sees the same data even when the pool had no connection.
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement();
                 var row = sql.executeQuery("SELECT username FROM users WHERE id = 1")) {
                assertThat(row.next()).isTrue(); assertThat(row.getString(1)).isEqualTo("synthetic-user");
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) { sql.execute("SHUTDOWN"); }
        }
    }
}
