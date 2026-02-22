-- =============================================================
-- Monsing 성능 테스트 더미 데이터 삽입 스크립트 (5× 확장판)
--
-- ID 범위:
--   teacher  ID:  1       ~  500,000   (TEACHER)
--   student  ID:  500,001 ~ 2,000,000  (STUDENT)
--   member       teacher + student 동일 ID
--
-- 총 삽입 행 수: ~75.8M rows
--   member          2,000,000
--   teacher           500,000
--   student         1,500,000
--   temp_member        10,000
--   career          1,500,000  (teacher_id FK in career)
--   portfolio       1,000,000  (teacher_id FK in portfolio)
--   course          1,500,000
--   lesson          7,500,000
--   record         15,000,000
--   feedback       20,000,000
--   feedback_item   1,500,000
--   feedback_ticket 7,500,000
--   review          4,000,000
--   rating         12,000,000
--   report            250,000
--   report_image      500,000
--
-- 예상 소요 시간: 약 2~4시간 (innodb_buffer_pool=4G 기준)
-- 실행 방법:
--   mysql -uroot -proot monsing < scripts/seed_data.sql
-- =============================================================

USE monsing;

SET FOREIGN_KEY_CHECKS   = 0;
SET SESSION cte_max_recursion_depth = 10000;
SET SESSION innodb_lock_wait_timeout = 300;
SET time_zone = '+09:00';

-- =============================================================
-- 헬퍼: 0-9999 시퀀스 테이블
-- =============================================================
DROP TABLE IF EXISTS _seq;
CREATE TABLE _seq (n BIGINT NOT NULL, PRIMARY KEY (n));

INSERT INTO _seq
WITH RECURSIVE r AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM r WHERE n < 9999
)
SELECT n FROM r;

SELECT CONCAT('[', NOW(), '] _seq 생성 완료') AS log;

-- =============================================================
-- 1. member (2M)
--    id 1-500000     → TEACHER
--    id 500001-2000000 → STUDENT
-- =============================================================
-- 1a. teacher members (500K: 10K × 50)
INSERT INTO member (id, member_type)
SELECT a.n * 50 + b.n + 1, 'TEACHER'
FROM _seq a
         CROSS JOIN (SELECT n FROM _seq WHERE n < 50) b;

-- 1b. student members (1.5M: stored procedure, 100K씩 15 배치)
DROP PROCEDURE IF EXISTS gen_student_members;
DELIMITER $$
CREATE PROCEDURE gen_student_members()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 15
        DO
            INSERT INTO member (id, member_type)
            SELECT a.n * 10 + b.n + batch * 100000 + 500001, 'STUDENT'
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 1500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] student member 완료') AS log;
END $$
DELIMITER ;
CALL gen_student_members();
DROP PROCEDURE IF EXISTS gen_student_members;

SELECT CONCAT('[', NOW(), '] member 완료: 2,000,000 rows') AS log;

-- =============================================================
-- 2. teacher (500K: 10K × 50)
--    nickname UNIQUE → 'teacher00000001' 패턴
-- =============================================================
INSERT INTO teacher (id, identifier, oauth_provider_type, nickname,
                     summary, strong_side_type, description, for_student,
                     verified, profile_image, gender_type, expertise_type,
                     created_date, updated_date)
SELECT a.n * 50 + b.n + 1                                                          AS id,
       CONCAT('oauth_t', LPAD(a.n * 50 + b.n + 1, 10, '0'))                        AS identifier,
       ELT(MOD(a.n * 50 + b.n, 3) + 1, 'GOOGLE', 'NAVER', 'KAKAO')                AS oauth_provider_type,
       CONCAT('teacher', LPAD(a.n * 50 + b.n + 1, 8, '0'))                         AS nickname,
       CONCAT('summary-', a.n * 50 + b.n + 1)                                      AS summary,
       ELT(MOD(a.n * 50 + b.n, 3) + 1, 'PITCH', 'FIX', 'RHYTHM')                  AS strong_side_type,
       CONCAT('desc-', a.n * 50 + b.n + 1)                                         AS description,
       CONCAT('for-student-', a.n * 50 + b.n + 1)                                  AS for_student,
       IF(MOD(a.n * 50 + b.n, 5) = 0, 1, 0)                                        AS verified,
       NULL                                                                          AS profile_image,
       ELT(MOD(a.n * 50 + b.n, 3) + 1, 'MALE', 'FEMALE', 'OTHER')                  AS gender_type,
       ELT(MOD(a.n * 50 + b.n, 3) + 1, 'VOCAL', 'DANCE', 'NONE')                   AS expertise_type,
       NOW(6),
       NOW(6)
FROM _seq a
         CROSS JOIN (SELECT n FROM _seq WHERE n < 50) b;

SELECT CONCAT('[', NOW(), '] teacher 완료: ', ROW_COUNT(), ' rows') AS log;

-- =============================================================
-- 3. student (1.5M: 100K씩 15 배치)
--    id: 500001 ~ 2000000
-- =============================================================
DROP PROCEDURE IF EXISTS gen_students;
DELIMITER $$
CREATE PROCEDURE gen_students()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 15
        DO
            INSERT INTO student (id, identifier, oauth_provider_type, nickname,
                                 profile_image, created_date, updated_date)
            SELECT a.n * 10 + b.n + batch * 100000 + 500001                                 AS id,
                   CONCAT('oauth_s', LPAD(a.n * 10 + b.n + batch * 100000 + 1, 10, '0'))    AS identifier,
                   ELT(MOD(a.n * 10 + b.n, 3) + 1, 'GOOGLE', 'NAVER', 'KAKAO')             AS oauth_provider_type,
                   CONCAT('student', LPAD(a.n * 10 + b.n + batch * 100000 + 1, 8, '0'))     AS nickname,
                   NULL                                                                       AS profile_image,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 1500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] student 완료') AS log;
END $$
DELIMITER ;
CALL gen_students();
DROP PROCEDURE IF EXISTS gen_students;

-- =============================================================
-- 4. temp_member (10K)
--    임시 OAuth 인증 사용자
-- =============================================================
INSERT INTO temp_member (identifier, oauth_provider_type, created_date, updated_date)
SELECT CONCAT('temp_oauth_', LPAD(n + 1, 8, '0'))                  AS identifier,
       ELT(MOD(n, 3) + 1, 'GOOGLE', 'NAVER', 'KAKAO')              AS oauth_provider_type,
       NOW(6),
       NOW(6)
FROM _seq
WHERE n < 10000;

SELECT CONCAT('[', NOW(), '] temp_member 완료: ', ROW_COUNT(), ' rows') AS log;

-- =============================================================
-- 5. career (1.5M: teacher당 3개)
--    teacher_id FK가 career 테이블에 직접 존재
--    teacher_id = MOD(idx, 500000) + 1  → 500K teacher 균등 분산
-- =============================================================
DROP PROCEDURE IF EXISTS gen_careers;
DELIMITER $$
CREATE PROCEDURE gen_careers()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 15
        DO
            INSERT INTO career (teacher_id, period, detail, created_date, updated_date)
            SELECT MOD(a.n * 10 + b.n + batch * 100000, 500000) + 1              AS teacher_id,
                   ELT(MOD(a.n * 10 + b.n + batch * 100000, 3) + 1,
                       '2019-2021', '2021-2023', '2023-현재')                     AS period,
                   CONCAT('career-detail-', a.n * 10 + b.n + batch * 100000 + 1) AS detail,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 1500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] career 완료') AS log;
END $$
DELIMITER ;
CALL gen_careers();
DROP PROCEDURE IF EXISTS gen_careers;

-- =============================================================
-- 6. portfolio (1M: teacher당 2개)
--    teacher_id FK가 portfolio 테이블에 직접 존재
-- =============================================================
DROP PROCEDURE IF EXISTS gen_portfolios;
DELIMITER $$
CREATE PROCEDURE gen_portfolios()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 10
        DO
            INSERT INTO portfolio (teacher_id, url, created_date, updated_date)
            SELECT MOD(a.n * 10 + b.n + batch * 100000, 500000) + 1              AS teacher_id,
                   CONCAT('https://cdn.example.com/portfolio/',
                          a.n * 10 + b.n + batch * 100000 + 1, '.jpg')           AS url,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 1000000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] portfolio 완료') AS log;
END $$
DELIMITER ;
CALL gen_portfolios();
DROP PROCEDURE IF EXISTS gen_portfolios;

-- =============================================================
-- 7. course (1.5M: teacher당 3개, 100K씩 15 배치)
--    teacher_id = (idx DIV 3) + 1 → 1~500000 균등
-- =============================================================
DROP PROCEDURE IF EXISTS gen_courses;
DELIMITER $$
CREATE PROCEDURE gen_courses()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 15
        DO
            INSERT INTO course (name, description, curriculum, teacher_id,
                                duration, price_per_lesson, minimum_lesson_count,
                                created_date, updated_date)
            SELECT CONCAT('course-', a.n * 10 + b.n + batch * 100000 + 1)            AS name,
                   CONCAT('desc-', a.n * 10 + b.n + batch * 100000 + 1)              AS description,
                   CONCAT('curriculum-', a.n * 10 + b.n + batch * 100000 + 1)        AS curriculum,
                   (a.n * 10 + b.n + batch * 100000) DIV 3 + 1                       AS teacher_id,
                   ELT(MOD(a.n * 10 + b.n + batch * 100000, 4) + 1, 30, 45, 60, 90) AS duration,
                   (MOD(a.n * 10 + b.n + batch * 100000, 10) + 1) * 10000            AS price_per_lesson,
                   MOD(a.n * 10 + b.n + batch * 100000, 10) + 1                      AS minimum_lesson_count,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 1500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] course 완료') AS log;
END $$
DELIMITER ;
CALL gen_courses();
DROP PROCEDURE IF EXISTS gen_courses;

-- =============================================================
-- 8. lesson (7.5M: course당 5개, 100K씩 75 배치)
--    같은 course 내 (day_of_week, start_time) UNIQUE 보장:
--    lesson_in_course 0→MON/09:00, 1→TUE/10:00, 2→WED/11:00,
--                     3→THU/13:00, 4→FRI/14:00
-- =============================================================
DROP PROCEDURE IF EXISTS gen_lessons;
DELIMITER $$
CREATE PROCEDURE gen_lessons()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 75
        DO
            INSERT INTO lesson (day_of_week, start_time,
                                lesson_status_type, class_room_status_type,
                                course_id, created_date, updated_date)
            SELECT ELT(MOD(a.n * 10 + b.n + batch * 100000, 5) + 1,
                       'MON', 'TUE', 'WED', 'THU', 'FRI')                                    AS day_of_week,
                   ELT(MOD(a.n * 10 + b.n + batch * 100000, 5) + 1,
                       '09:00:00', '10:00:00', '11:00:00', '13:00:00', '14:00:00')            AS start_time,
                   'AVAILABLE'                                                                  AS lesson_status_type,
                   'CLOSED'                                                                     AS class_room_status_type,
                   (a.n * 10 + b.n + batch * 100000) DIV 5 + 1                                AS course_id,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 7500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] lesson 완료') AS log;
END $$
DELIMITER ;
CALL gen_lessons();
DROP PROCEDURE IF EXISTS gen_lessons;

-- =============================================================
-- 9. record (15M: student당 10개, 100K씩 150 배치)
--    student_id = (idx DIV 10) + 500001 → 500001~2000000
--    title 패턴: ^[가-힣a-zA-Z0-9._\-()]*$ 준수 → 'record-숫자'
-- =============================================================
DROP PROCEDURE IF EXISTS gen_records;
DELIMITER $$
CREATE PROCEDURE gen_records()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 150
        DO
            INSERT INTO record (title, student_id, file_key, url, created_date, updated_date)
            SELECT CONCAT('record-', a.n * 10 + b.n + batch * 100000 + 1)                          AS title,
                   (a.n * 10 + b.n + batch * 100000) DIV 10 + 500001                               AS student_id,
                   CONCAT('keys/', a.n * 10 + b.n + batch * 100000 + 1, '.mp4')                    AS file_key,
                   CONCAT('https://cdn.example.com/', a.n * 10 + b.n + batch * 100000 + 1, '.mp4') AS url,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 15000000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] record 완료') AS log;
END $$
DELIMITER ;
CALL gen_records();
DROP PROCEDURE IF EXISTS gen_records;

-- =============================================================
-- 10. feedback (20M)
--     1차 pass (15M): record 1~15000000 각 1개
--       teacher_id = MOD(idx, 500000) + 1
--     2차 pass  (5M): record 1~5000000 추가 1개 (다른 teacher)
--       teacher_id = MOD(idx + 1, 500000) + 1  (겹치지 않도록 offset)
--     → record_id는 feedback 테이블의 FK 컬럼으로 직접 관리
-- =============================================================
DROP PROCEDURE IF EXISTS gen_feedbacks;
DELIMITER $$
CREATE PROCEDURE gen_feedbacks()
BEGIN
    DECLARE batch INT DEFAULT 0;

    -- 1차: record당 첫 번째 feedback (15M, 150배치)
    WHILE batch < 150
        DO
            INSERT INTO feedback (record_id, teacher_id, _detail, status, created_date, updated_date)
            SELECT a.n * 10 + b.n + batch * 100000 + 1                                       AS record_id,
                   MOD(a.n * 10 + b.n + batch * 100000, 500000) + 1                          AS teacher_id,
                   CONCAT('feedback-detail-', a.n * 10 + b.n + batch * 100000 + 1)           AS _detail,
                   IF(MOD(a.n * 10 + b.n + batch * 100000, 3) = 0, 'COMPLETED', 'REQUESTED') AS status,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 15000000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;

    -- 2차: record 1~5000000에 두 번째 feedback 추가 (5M, 50배치)
    -- teacher offset +1 으로 1차와 다른 teacher 보장
    SET batch = 0;
    WHILE batch < 50
        DO
            INSERT INTO feedback (record_id, teacher_id, _detail, status, created_date, updated_date)
            SELECT a.n * 10 + b.n + batch * 100000 + 1                                            AS record_id,
                   MOD(a.n * 10 + b.n + batch * 100000 + 1, 500000) + 1                           AS teacher_id,
                   CONCAT('feedback2-detail-', a.n * 10 + b.n + batch * 100000 + 1)               AS _detail,
                   IF(MOD(a.n * 10 + b.n + batch * 100000, 5) = 0, 'COMPLETED', 'REQUESTED')      AS status,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 5000000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;

    SELECT CONCAT('[', NOW(), '] feedback 완료') AS log;
END $$
DELIMITER ;
CALL gen_feedbacks();
DROP PROCEDURE IF EXISTS gen_feedbacks;

-- =============================================================
-- 11. feedback_item (1.5M: teacher당 3개, 100K씩 15 배치)
-- =============================================================
DROP PROCEDURE IF EXISTS gen_feedback_items;
DELIMITER $$
CREATE PROCEDURE gen_feedback_items()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 15
        DO
            INSERT INTO feedback_item (teacher_id, description, price, amount, created_date, updated_date)
            SELECT (a.n * 10 + b.n + batch * 100000) DIV 3 + 1                     AS teacher_id,
                   CONCAT('item-desc-', a.n * 10 + b.n + batch * 100000 + 1)       AS description,
                   (MOD(a.n * 10 + b.n + batch * 100000, 10) + 1) * 5000           AS price,
                   MOD(a.n * 10 + b.n + batch * 100000, 50) + 10                   AS amount,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 1500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] feedback_item 완료') AS log;
END $$
DELIMITER ;
CALL gen_feedback_items();
DROP PROCEDURE IF EXISTS gen_feedback_items;

-- =============================================================
-- 12. feedback_ticket (7.5M: feedback_item당 5개, 100K씩 75 배치)
--     UNIQUE(student_id, feedback_item_id) 보장:
--       feedback_item_id = (idx DIV 5) + 1
--       student_id = MOD((idx DIV 5)*5 + MOD(idx,5), 1500000) + 500001
--     → 같은 item 내 5개 ticket은 서로 다른 student
-- =============================================================
DROP PROCEDURE IF EXISTS gen_tickets;
DELIMITER $$
CREATE PROCEDURE gen_tickets()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 75
        DO
            INSERT INTO feedback_ticket (feedback_item_id, student_id, _amount, version,
                                         created_date, updated_date)
            SELECT (a.n * 10 + b.n + batch * 100000) DIV 5 + 1                                 AS feedback_item_id,
                   MOD((a.n * 10 + b.n + batch * 100000) DIV 5 * 5
                           + MOD(a.n * 10 + b.n + batch * 100000, 5), 1500000) + 500001        AS student_id,
                   MOD(a.n * 10 + b.n + batch * 100000, 10) + 1                                AS _amount,
                   0                                                                             AS version,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 7500000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] feedback_ticket 완료') AS log;
END $$
DELIMITER ;
CALL gen_tickets();
DROP PROCEDURE IF EXISTS gen_tickets;

-- =============================================================
-- 13. review (4M: teacher당 8개, 100K씩 40 배치)
--     writer_id = student 범위 (500001~2000000)
-- =============================================================
DROP PROCEDURE IF EXISTS gen_reviews;
DELIMITER $$
CREATE PROCEDURE gen_reviews()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 40
        DO
            INSERT INTO review (teacher_id, writer_id, detail, created_date, updated_date)
            SELECT (a.n * 10 + b.n + batch * 100000) DIV 8 + 1                    AS teacher_id,
                   MOD(a.n * 10 + b.n + batch * 100000, 1500000) + 500001         AS writer_id,
                   CONCAT('review-', a.n * 10 + b.n + batch * 100000 + 1)         AS detail,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 4000000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] review 완료') AS log;
END $$
DELIMITER ;
CALL gen_reviews();
DROP PROCEDURE IF EXISTS gen_reviews;

-- =============================================================
-- 14. rating (12M: review당 3개 KINDNESS/KNOWLEDGE/PASSION, 100K씩 120 배치)
--     score 1~5 순환
-- =============================================================
DROP PROCEDURE IF EXISTS gen_ratings;
DELIMITER $$
CREATE PROCEDURE gen_ratings()
BEGIN
    DECLARE batch INT DEFAULT 0;
    WHILE batch < 120
        DO
            INSERT INTO rating (review_id, category, score, created_date, updated_date)
            SELECT (a.n * 10 + b.n + batch * 100000) DIV 3 + 1                                   AS review_id,
                   ELT(MOD(a.n * 10 + b.n + batch * 100000, 3) + 1,
                       'KINDNESS', 'KNOWLEDGE', 'PASSION')                                         AS category,
                   MOD(a.n * 10 + b.n + batch * 100000, 5) + 1                                    AS score,
                   NOW(6),
                   NOW(6)
            FROM _seq a
                     CROSS JOIN (SELECT n FROM _seq WHERE n < 10) b
            WHERE a.n * 10 + b.n + batch * 100000 < 12000000;
            SET batch = batch + 1;
            COMMIT;
        END WHILE;
    SELECT CONCAT('[', NOW(), '] rating 완료') AS log;
END $$
DELIMITER ;
CALL gen_ratings();
DROP PROCEDURE IF EXISTS gen_ratings;

-- =============================================================
-- 15. report (250K)
--     reporter / reported → member.id (1~2000000)
--     자기 자신 신고 방지: offset 1000000
-- =============================================================
INSERT INTO report (reporter_id, reported_id, report_type, detail, created_date, updated_date)
SELECT MOD(a.n * 25 + b.n, 2000000) + 1                               AS reporter_id,
       MOD(a.n * 25 + b.n + 1000000, 2000000) + 1                     AS reported_id,
       ELT(MOD(a.n * 25 + b.n, 3) + 1, 'INSULT', 'SPAM', 'SEXUAL')   AS report_type,
       CONCAT('report-detail-', a.n * 25 + b.n + 1)                   AS detail,
       NOW(6),
       NOW(6)
FROM _seq a
         CROSS JOIN (SELECT n FROM _seq WHERE n < 25) b
WHERE a.n * 25 + b.n < 250000;

COMMIT;
SELECT CONCAT('[', NOW(), '] report 완료: ', ROW_COUNT(), ' rows') AS log;

-- =============================================================
-- 16. report_image (500K: report당 2개)
--     report_id = (idx DIV 2) + 1 → 1~250000
-- =============================================================
INSERT INTO report_image (report_id, url, created_date, updated_date)
SELECT a.n * 50 + b.n DIV 2 + 1                                                          AS report_id,
       CONCAT('https://cdn.example.com/report-image/', a.n * 50 + b.n + 1, '.jpg')       AS url,
       NOW(6),
       NOW(6)
FROM _seq a
         CROSS JOIN (SELECT n FROM _seq WHERE n < 50) b
WHERE a.n * 50 + b.n < 500000;

COMMIT;
SELECT CONCAT('[', NOW(), '] report_image 완료: ', ROW_COUNT(), ' rows') AS log;

-- =============================================================
-- 정리 및 최종 통계
-- =============================================================
DROP TABLE IF EXISTS _seq;
SET FOREIGN_KEY_CHECKS = 1;

SELECT 'member'          AS tbl, COUNT(*) AS cnt FROM member
UNION ALL SELECT 'teacher',        COUNT(*) FROM teacher
UNION ALL SELECT 'student',        COUNT(*) FROM student
UNION ALL SELECT 'temp_member',    COUNT(*) FROM temp_member
UNION ALL SELECT 'career',         COUNT(*) FROM career
UNION ALL SELECT 'portfolio',      COUNT(*) FROM portfolio
UNION ALL SELECT 'course',         COUNT(*) FROM course
UNION ALL SELECT 'lesson',         COUNT(*) FROM lesson
UNION ALL SELECT 'record',         COUNT(*) FROM record
UNION ALL SELECT 'feedback',       COUNT(*) FROM feedback
UNION ALL SELECT 'feedback_item',  COUNT(*) FROM feedback_item
UNION ALL SELECT 'feedback_ticket',COUNT(*) FROM feedback_ticket
UNION ALL SELECT 'review',         COUNT(*) FROM review
UNION ALL SELECT 'rating',         COUNT(*) FROM rating
UNION ALL SELECT 'report',         COUNT(*) FROM report
UNION ALL SELECT 'report_image',   COUNT(*) FROM report_image;

SELECT CONCAT('[', NOW(), '] 시드 데이터 삽입 완료. 총 ~75.8M rows') AS status;
