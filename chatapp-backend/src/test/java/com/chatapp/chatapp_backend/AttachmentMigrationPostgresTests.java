package com.chatapp.chatapp_backend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RUN_PG_MIGRATION_TESTS",matches="true")
class AttachmentMigrationPostgresTests {
    @Test void v7PreservesExistingMessagesAndBackfillsAttachmentIdentityOnPostgres() throws Exception {
        String url="jdbc:postgresql://127.0.0.1:15435/samus_audit", schema="audit_mig_"+UUID.randomUUID().toString().replace("-", "");
        var v6=Flyway.configure().dataSource(url,"audit","audit_test_only").schemas(schema).defaultSchema(schema).target("6").load();
        v6.migrate();
        try(var connection=DriverManager.getConnection(url,"audit","audit_test_only");var sql=connection.createStatement()){
            sql.execute("SET search_path TO " + schema);
            sql.execute("INSERT INTO servers(name,owner_email) VALUES ('legacy','owner@example.test')");
            sql.execute("INSERT INTO channels(name,type,server_id) VALUES ('legacy','TEXT',1)");
            String filename=UUID.randomUUID()+".txt";
            sql.execute("INSERT INTO messages(content,sender_email,sender_username,attachment_url,channel_id) VALUES ('preserved','owner@example.test','owner','https://old.example.test/uploads/"+filename+"',1)");
            var current=Flyway.configure().dataSource(url,"audit","audit_test_only").schemas(schema).defaultSchema(schema).load();current.migrate();
            assertThat(current.info().current().getVersion().toString()).isEqualTo("7");
            try(var row=sql.executeQuery("SELECT content,attachment_name,attachment_url FROM messages")){
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("preserved");assertThat(row.getString(2)).isEqualTo(filename);assertThat(row.getString(3)).startsWith("https://old.example.test/");
            }
            assertThat(schema).matches("audit_mig_[0-9a-f]{32}");sql.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
