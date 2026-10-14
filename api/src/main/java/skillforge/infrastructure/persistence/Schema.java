package skillforge.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
class Schema {

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
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("catalog.sql"));
                ready = true;
            }
            catch (SQLException ex) {
                throw new Unavailable(ex);
            }
        }
    }

}
