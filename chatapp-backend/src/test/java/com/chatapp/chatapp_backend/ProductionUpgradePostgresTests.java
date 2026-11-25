package com.chatapp.chatapp_backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_PG_MIGRATION_TESTS", matches = "true")
class ProductionUpgradePostgresTests {
    @Test void upgradeFromDeployedSchemaPreservesAccountsRelationshipsMessagesAndImages() throws Exception {
        String url = System.getenv().getOrDefault("PG_MIGRATION_TEST_URL", "jdbc:postgresql://127.0.0.1:15437/samus_audit");
        // Fixed disposable credentials: never consumes Render connection variables.
        String schema = "update_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var db = DriverManager.getConnection(url, "audit", "audit_test_only"); var sql = db.createStatement()) {
            try {
                var old = Flyway.configure().dataSource(url, "audit", "audit_test_only").schemas(schema).defaultSchema(schema).target("9").load();
                old.migrate(); sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO users(id,username,email,password,auth_version) VALUES (41,'owner','owner@example.test','preserved-password-hash',7),(42,'member','member@example.test','second-hash',2)");
                sql.execute("INSERT INTO servers(id,name,owner_email) VALUES (61,'Preserved server','owner@example.test')");
                sql.execute("INSERT INTO channels(id,name,type,server_id) VALUES (81,'general','TEXT',61)");
                sql.execute("INSERT INTO server_members(user_email,role,server_id) VALUES ('owner@example.test','ADMIN',61),('member@example.test','USER',61)");
                sql.execute("INSERT INTO messages(id,content,sender_email,sender_username,channel_id,attachment_url,attachment_name,attachment_size) VALUES (91,'Preserved message','owner@example.test','owner',81,'https://existing.example/uploads/00000000-0000-0000-0000-000000000001.txt','00000000-0000-0000-0000-000000000001.txt',12)");
                sql.execute("INSERT INTO direct_messages(sender_email,recipient_email,sender_username,content,created_at) VALUES ('owner@example.test','member@example.test','owner','Preserved direct',CURRENT_TIMESTAMP)");
                sql.execute("INSERT INTO friendships(low_user_id,high_user_id,requested_by,accepted) VALUES (41,42,41,TRUE)");
                sql.execute("INSERT INTO appearance_blobs(filename,content_type,content) VALUES ('00000000-0000-0000-0000-000000000002.png','image/png',decode('89504e47','hex'))");
                var latest = Flyway.configure().dataSource(url, "audit", "audit_test_only").schemas(schema).defaultSchema(schema).load();
                latest.migrate(); latest.validate();
                assertThat(latest.info().current().getVersion().toString()).isEqualTo("11");
                assertThat(latest.info().pending()).isEmpty();
                try (var rows = sql.executeQuery("SELECT username,password,auth_version FROM users WHERE id=41")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("owner");
                    assertThat(rows.getString(2)).isEqualTo("preserved-password-hash"); assertThat(rows.getInt(3)).isEqualTo(7);
                }
                try (var rows = sql.executeQuery("SELECT content,attachment_url,attachment_name FROM messages WHERE id=91 AND channel_id=81")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Preserved message");
                    assertThat(rows.getString(2)).startsWith("https://existing.example/uploads/");
                    assertThat(rows.getString(3)).endsWith(".txt");
                }
                for (String check : new String[]{
                        "SELECT count(*)=2 FROM users", "SELECT count(*)=2 FROM server_members",
                        "SELECT count(*)=1 FROM friendships WHERE accepted", "SELECT count(*)=1 FROM direct_messages WHERE content='Preserved direct'",
                        "SELECT count(*)=1 FROM appearance_blobs WHERE content=decode('89504e47','hex')",
                        "SELECT count(*)=1 FROM servers WHERE id=61 AND invite_code IS NULL",
                        "SELECT count(*)=0 FROM attachment_blobs"}) {
                    try (var rows = sql.executeQuery(check)) { assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isTrue(); }
                }
            } finally {
                assertThat(schema).matches("update_test_[0-9a-f]{32}");
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
