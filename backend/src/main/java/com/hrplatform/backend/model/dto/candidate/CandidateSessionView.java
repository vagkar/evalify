package com.hrplatform.backend.model.dto.candidate;

import com.hrplatform.backend.model.entity.SessionStatus;

import java.time.Instant;
import java.util.List;

/**
 * questions is populated only while the session is IN_PROGRESS; before start and after
 * submit it is empty, so the content cannot be read without the timer running.
 * questionCount is always set.
 */
public record CandidateSessionView(
        Long sessionId,
        String assessmentTitle,
        Integer durationMinutes,
        SessionStatus status,
        Instant startedAt,
        long questionCount,
        List<CandidateQuestionView> questions
) {}
