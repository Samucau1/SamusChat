package com.chatapp.chatapp_backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class ServerInviteMigrationTests {
    @Test void additiveMigrationPreservesOldServersAndEnforcesUniqueCodes() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:invite-migration;MODE=PostgreSQL", "sa", "")) {
            var sql = db.createStatement();
            sql.execute("CREATE TABLE servers(id BIGINT PRIMARY KEY, name VARCHAR(255))");
            sql.execute("INSERT INTO servers(id, name) VALUES (42, 'Existing server')");
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V10__server_invite_codes.sql"));
            try (var result = sql.executeQuery("SELECT id, name, invite_code FROM servers")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(42);
                assertThat(result.getString(2)).isEqualTo("Existing server");
                assertThat(result.getString(3)).isNull();
            }
            sql.execute("UPDATE servers SET invite_code = 'A#%' WHERE id = 42");
            assertThatThrownBy(() -> sql.execute("INSERT INTO servers(id, invite_code) VALUES (43, 'A#%')"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (var result = sql.executeQuery("SELECT id FROM server_invite_code_lock")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }
}
