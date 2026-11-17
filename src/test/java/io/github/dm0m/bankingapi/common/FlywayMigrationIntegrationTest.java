package io.github.dm0m.bankingapi.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

// Every migration in db/migration applies cleanly to an empty Postgres of the production version.
// The entity mapping itself is checked at startup by spring.jpa.hibernate.ddl-auto=validate.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrationsAreApplied_noneFailedOrPending() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(m -> assertThat(m.getState().isFailed()).as(m.getScript()).isFalse());
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void appliedMigrationsMatchTheFilesOnTheClasspath() {
        // fails if a migration was edited after it was applied (checksum mismatch)
        ValidateResult result = flyway.validateWithResult();

        assertThat(result.validationSuccessful).as(String.valueOf(result.invalidMigrations)).isTrue();
    }
}
