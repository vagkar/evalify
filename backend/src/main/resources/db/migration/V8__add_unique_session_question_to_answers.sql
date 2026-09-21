-- A session stores exactly one answer per question. The application enforces this
-- (validation + row lock on candidate_sessions during submit); this constraint is the
-- database-level guarantee.
--
-- Before deploying, verify there are no existing duplicates, otherwise this migration fails:
--   SELECT session_id, question_id, COUNT(*) FROM answers
--   GROUP BY session_id, question_id HAVING COUNT(*) > 1;
ALTER TABLE answers
    ADD CONSTRAINT uq_answers_session_question UNIQUE (session_id, question_id);
