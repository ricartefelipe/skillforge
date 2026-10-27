package skillforge.infrastructure.persistence;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
class Schema {

    // A fixed key so every instance takes the same Postgres advisory lock before touching the schema.
    private static final long LOCK_KEY = 8_151_146_018_264_244_993L;

    private final DataSource dataSource;

    // The script is idempotent. Skip it after the first success so a later outage is a failed call.
    private volatile boolean ready;

    Schema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void ensure() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            // Tables are created on use. A closed database must not stop liveness.
            try (Connection connection = dataSource.getConnection()) {
                // Serialize first-request DDL so two instances cannot evolve the schema at the same time.
                advisoryLock(connection);
                try {
                    migrate(connection);
                    ready = true;
                }
                finally {
                    advisoryUnlock(connection);
                }
            }
            catch (SQLException ex) {
                throw new Unavailable(ex);
            }
        }
    }

    // The script carries its own history ledger, so an unchanged version is applied once and then skipped.
    private void migrate(Connection connection) throws SQLException {
        ClassPathResource script = new ClassPathResource("catalog.sql");
        String checksum = checksum(script);
        try (PreparedStatement ledger = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS schema_history ("
                        + "checksum text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())")) {
            ledger.executeUpdate();
        }
        if (applied(connection, checksum)) {
            return;
        }
        ScriptUtils.executeSqlScript(connection, script);
        try (PreparedStatement record = connection.prepareStatement(
                "INSERT INTO schema_history (checksum) VALUES (?) ON CONFLICT DO NOTHING")) {
            record.setString(1, checksum);
            record.executeUpdate();
        }
    }

    private static boolean applied(Connection connection, String checksum) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM schema_history WHERE checksum = ?")) {
            statement.setString(1, checksum);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static String checksum(ClassPathResource script) throws SQLException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(script.getContentAsByteArray()));
        }
        catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("schema checksum", ex);
        }
    }

    private static void advisoryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        }
    }

    private static void advisoryUnlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        }
    }

}
