package com.example.llmplayground.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long> {

    Optional<Run> findTopByOrderByIdDesc();

    List<Run> findTop200ByOrderByIdDesc();

    /** Runs that were queued or running when the app stopped can never finish. */
    @Modifying
    @Transactional
    @Query("update Run r set r.status = :failed, r.errorMessage = :message, r.finishedAt = :now "
            + "where r.status in :active")
    int failActive(@Param("active") Collection<RunStatus> active, @Param("failed") RunStatus failed,
                   @Param("message") String message, @Param("now") Instant now);
}
