package io.github.dm0m.bankingapi.auth;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO login_attempts AS a (email, failed_count, window_started_at, locked_until)
        VALUES (:email, 1, :now, CASE WHEN :max <= 1 THEN CAST(:lockUntil AS timestamptz) END)
        ON CONFLICT (email) DO UPDATE SET
            failed_count = CASE WHEN a.window_started_at <= :cutoff THEN 1 ELSE a.failed_count + 1 END,
            window_started_at = CASE WHEN a.window_started_at <= :cutoff THEN :now ELSE a.window_started_at END,
            locked_until = CASE
            WHEN (CASE WHEN a.window_started_at <= :cutoff THEN 1 ELSE a.failed_count + 1 END) >= :max
            THEN CAST(:lockUntil AS timestamptz) END
        """)
    void recordFailure(
        @Param("email") String email,
        @Param("now") Instant now,
        @Param("cutoff") Instant cutoff,
        @Param("max") int max,
        @Param("lockUntil") Instant lockUntil
    );

    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.email = :email")
    void deleteByEmail(@Param("email") String email);

    // a row can still hold a running lock after its window is over: keep it until both are done
    @Modifying
    @Query("""
            DELETE FROM LoginAttempt a
            WHERE a.windowStartedAt <= :cutoff AND (a.lockedUntil IS NULL OR a.lockedUntil <= :now)
            """)
    int deleteExpired(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
