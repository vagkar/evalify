package com.hrplatform.backend.repository;

import com.hrplatform.backend.model.dto.assessment.AssessmentStatsRow;
import com.hrplatform.backend.model.entity.CandidateSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CandidateSessionRepository extends JpaRepository<CandidateSession, Long> {
    Optional<CandidateSession> findByInviteToken(String inviteToken);

    /**
     * SELECT ... FOR UPDATE on the session row. Used by state transitions (start, submit) so
     * concurrent requests for the same token are serialised and see each other's status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CandidateSession cs WHERE cs.inviteToken = :inviteToken")
    Optional<CandidateSession> findByInviteTokenForUpdate(@Param("inviteToken") String inviteToken);
    List<CandidateSession> findAllByAssessmentId(Long assessmentId);

    @Query("""
        SELECT cs FROM CandidateSession cs
        JOIN cs.assessment a
        WHERE a.id = :assessmentId AND a.company.id = :companyId
    """)
    List<CandidateSession> findAllByAssessmentIdAndCompanyId(
            @Param("assessmentId") Long assessmentId,
            @Param("companyId") Long companyId
    );

    @Query("""
        SELECT cs FROM CandidateSession cs
        JOIN cs.assessment a
        WHERE cs.id = :sessionId AND a.company.id = :companyId
    """)
    Optional<CandidateSession> findByIdAndCompanyId(
            @Param("sessionId") Long sessionId,
            @Param("companyId") Long companyId
    );

    @Query("SELECT COUNT(cs) FROM CandidateSession cs JOIN cs.assessment a WHERE a.company.id = :companyId")
    long countByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(cs) FROM CandidateSession cs JOIN cs.assessment a WHERE a.company.id = :companyId AND cs.status = 'COMPLETED'")
    long countCompletedByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT AVG(cs.score) FROM CandidateSession cs JOIN cs.assessment a WHERE a.company.id = :companyId AND cs.status = 'COMPLETED' AND cs.score IS NOT NULL")
    BigDecimal avgScoreByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(cs) FROM CandidateSession cs WHERE cs.assessment.id = :assessmentId")
    long countByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Query("SELECT COUNT(cs) FROM CandidateSession cs WHERE cs.assessment.id = :assessmentId AND cs.status = 'COMPLETED'")
    long countCompletedByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Query("SELECT AVG(cs.score) FROM CandidateSession cs WHERE cs.assessment.id = :assessmentId AND cs.status = 'COMPLETED' AND cs.score IS NOT NULL")
    BigDecimal avgScoreByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Query("""
        SELECT cs.score FROM CandidateSession cs
        WHERE cs.assessment.id = :assessmentId AND cs.status = 'COMPLETED' AND cs.score IS NOT NULL
        ORDER BY cs.completedAt DESC
    """)
    List<BigDecimal> findRecentScoresByAssessmentId(@Param("assessmentId") Long assessmentId,
                                                     org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT cs.score FROM CandidateSession cs
        JOIN cs.assessment a
        WHERE a.id = :assessmentId AND a.company.id = :companyId
        AND cs.status = 'COMPLETED' AND cs.score IS NOT NULL
        ORDER BY cs.score
    """)
    List<BigDecimal> findAllCompletedScoresByAssessmentIdAndCompanyId(
            @Param("assessmentId") Long assessmentId,
            @Param("companyId") Long companyId
    );

    @Query(value = """
        SELECT AVG(EXTRACT(EPOCH FROM (cs.completed_at - cs.started_at)) / 60.0)
        FROM candidate_sessions cs
        JOIN assessments a ON a.id = cs.assessment_id
        WHERE a.id = :assessmentId AND a.company_id = :companyId
          AND cs.status = 'COMPLETED'
          AND cs.started_at IS NOT NULL AND cs.completed_at IS NOT NULL
    """, nativeQuery = true)
    Double avgCompletionMinutesByAssessmentIdAndCompanyId(
            @Param("assessmentId") Long assessmentId,
            @Param("companyId") Long companyId
    );

    @Query("""
        SELECT new com.hrplatform.backend.model.dto.assessment.AssessmentStatsRow(
            a.id,
            COUNT(cs.id),
            COUNT(CASE WHEN cs.status = 'COMPLETED' THEN 1 END),
            AVG(CASE WHEN cs.status = 'COMPLETED' AND cs.score IS NOT NULL THEN cs.score END)
        )
        FROM Assessment a
        LEFT JOIN CandidateSession cs ON cs.assessment = a
        WHERE a.company.id = :companyId
        GROUP BY a.id
    """)
    List<AssessmentStatsRow> findStatsByCompanyId(@Param("companyId") Long companyId);

    @Query(value = """
        SELECT assessment_id, score
        FROM (
            SELECT cs.assessment_id, cs.score,
                   ROW_NUMBER() OVER (PARTITION BY cs.assessment_id ORDER BY cs.completed_at DESC) AS rn
            FROM candidate_sessions cs
            JOIN assessments a ON a.id = cs.assessment_id
            WHERE a.company_id = :companyId
              AND cs.status = 'COMPLETED'
              AND cs.score IS NOT NULL
        ) sub
        WHERE rn <= 6
        ORDER BY assessment_id, rn DESC
    """, nativeQuery = true)
    List<Object[]> findRecentScoresByCompanyId(@Param("companyId") Long companyId);
}
