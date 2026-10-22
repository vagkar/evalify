-- Every assessment is timed: AssessmentRequest already requires durationMinutes and
-- CandidateService computes the submit deadline from it. A NULL here caused a 500 on submit.
--
-- Before deploying, verify there are no NULLs, otherwise this migration fails:
--   SELECT id, company_id, title FROM assessments WHERE duration_minutes IS NULL;
ALTER TABLE assessments
    ALTER COLUMN duration_minutes SET NOT NULL;
