package com.chatapp.chatapp_backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.flywaydb.core.Flyway;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_PG_MIGRATION_TESTS", matches = "true")
class AttachmentMigrationPostgresTests {
    @Test
    void v7AndLatestPreserveExistingMessagesAndBackfillAttachmentIdentityOnPostgres() throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:15435/samus_audit";
        String schema = "audit_mig_" + UUID.randomUUID().toString().replace("-", "");
        String filename = UUID.randomUUID() + ".txt";
        try (var connection = DriverManager.getConnection(url, "audit", "audit_test_only");
             var sql = connection.createStatement()) {
            try {
                var v6 = Flyway.configure().dataSource(url, "audit", "audit_test_only")
                        .schemas(schema).defaultSchema(schema).target("6").load();
                v6.migrate();
                sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO servers(name,owner_email) VALUES ('legacy','owner@example.test')");
                sql.execute("INSERT INTO channels(name,type,server_id) VALUES ('legacy','TEXT',1)");
                sql.execute("INSERT INTO messages(content,sender_email,sender_username,attachment_url,channel_id) "
                        + "VALUES ('preserved','owner@example.test','owner','https://old.example.test/uploads/"
                        + filename + "',1)");
                sql.execute("INSERT INTO direct_messages(sender_email,recipient_email,sender_username,content,created_at) "
                        + "VALUES ('owner@example.test','friend@example.test','owner','preserved direct',"
                        + "TIMESTAMP '2026-01-01 12:00:00')");

                var v7 = Flyway.configure().dataSource(url, "audit", "audit_test_only")
                        .schemas(schema).defaultSchema(schema).target("7").load();
                v7.migrate();
                assertThat(v7.info().current().getVersion().toString()).isEqualTo("7");
                assertLegacyAttachment(connection, filename);

                var latest = Flyway.configure().dataSource(url, "audit", "audit_test_only")
                        .schemas(schema).defaultSchema(schema).load();
                latest.migrate();
                latest.validate();
                assertThat(latest.info().pending()).isEmpty();
                assertLegacyAttachment(connection, filename);
                try (var row = sql.executeQuery("SELECT content, read_at FROM direct_messages")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("content")).isEqualTo("preserved direct");
                    assertThat(row.getTimestamp("read_at")).isNull();
                    assertThat(row.next()).isFalse();
                }
            } finally {
                assertThat(schema).matches("audit_mig_[0-9a-f]{32}");
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void assertLegacyAttachment(Connection connection, String filename) throws Exception {
        try (var sql = connection.createStatement();
             var row = sql.executeQuery("SELECT content, attachment_name, attachment_url FROM messages")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("content")).isEqualTo("preserved");
            assertThat(row.getString("attachment_name")).isEqualTo(filename);
            assertThat(row.getString("attachment_url")).isEqualTo("https://old.example.test/uploads/" + filename);
            assertThat(row.next()).isFalse();
        }
    }
}
