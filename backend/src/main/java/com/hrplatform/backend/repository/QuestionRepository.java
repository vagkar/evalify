package com.hrplatform.backend.repository;

import com.hrplatform.backend.model.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findAllByAssessmentIdOrderByOrderIndexAsc(Long assessmentId);
    long countByAssessmentId(Long assessmentId);
    Optional<Question> findByIdAndAssessmentId(Long id, Long assessmentId);
    Optional<Question> findByIdAndAssessmentCompanyId(Long id, Long companyId);
}
