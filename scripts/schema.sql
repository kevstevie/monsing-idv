-- =============================================================
-- Monsing DDL Schema
-- JPA/Hibernate 6 (Spring Boot 3.3.4) + MySQL 8.0 기준
-- 엔티티 어노테이션에서 직접 역산한 정확한 스키마
-- =============================================================

SET FOREIGN_KEY_CHECKS = 0;
SET time_zone = '+09:00';

-- Drop order
DROP TABLE IF EXISTS report_image;
DROP TABLE IF EXISTS rating;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS report;
DROP TABLE IF EXISTS feedback_ticket;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS feedback_item;
DROP TABLE IF EXISTS record;
DROP TABLE IF EXISTS lesson;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS career;
DROP TABLE IF EXISTS portfolio;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS temp_member;

-- =============================================================
-- member
-- Teacher/Student와 동일한 id를 공유 (id 수동 할당, no AUTO_INCREMENT)
-- =============================================================
CREATE TABLE member (
    id          BIGINT      NOT NULL,
    member_type VARCHAR(31) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- student (id = member.id 공유, 수동 할당)
-- @Embedded Nickname → @Column(name="nickname", unique=true)
-- =============================================================
CREATE TABLE student (
    id                  BIGINT       NOT NULL,
    identifier          VARCHAR(255) NOT NULL,
    oauth_provider_type VARCHAR(31)  NOT NULL,
    nickname            VARCHAR(255) NOT NULL,
    profile_image       VARCHAR(255),
    created_date        DATETIME(6),
    updated_date        DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_nickname (nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- teacher (id = member.id 공유, 수동 할당)
-- @Embedded Nickname → nickname 컬럼
-- portfolios / careers: @OneToMany @JoinColumn → FK in child tables
-- =============================================================
CREATE TABLE teacher (
    id                  BIGINT       NOT NULL,
    identifier          VARCHAR(255) NOT NULL,
    oauth_provider_type VARCHAR(31)  NOT NULL,
    nickname            VARCHAR(255) NOT NULL,
    summary             VARCHAR(255),
    strong_side_type    VARCHAR(31),
    description         VARCHAR(255),
    for_student         VARCHAR(255),
    verified            BIT(1)       NOT NULL DEFAULT 0,
    profile_image       VARCHAR(255),
    gender_type         VARCHAR(31)  NOT NULL,
    expertise_type      VARCHAR(31)  NOT NULL,
    created_date        DATETIME(6),
    updated_date        DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_nickname (nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- temp_member
-- 임시 회원 (OAuth 인증 중 임시 저장, BaseEntity → AUTO_INCREMENT)
-- =============================================================
CREATE TABLE temp_member (
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    identifier          VARCHAR(255) NOT NULL,
    oauth_provider_type VARCHAR(31)  NOT NULL,
    created_date        DATETIME(6),
    updated_date        DATETIME(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- career
-- @OneToMany @JoinColumn on Teacher.careers
-- → FK 컬럼 teacher_id가 career 테이블에 생성됨
-- (join table teacher_careers 불필요)
-- =============================================================
CREATE TABLE career (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    teacher_id   BIGINT       NOT NULL,
    period       VARCHAR(255) NOT NULL,
    detail       VARCHAR(255) NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_career_teacher (teacher_id),
    CONSTRAINT fk_career_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- portfolio
-- @OneToMany @JoinColumn on Teacher.portfolios
-- → FK 컬럼 teacher_id가 portfolio 테이블에 생성됨
-- =============================================================
CREATE TABLE portfolio (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    teacher_id   BIGINT       NOT NULL,
    url          VARCHAR(255) NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_portfolio_teacher (teacher_id),
    CONSTRAINT fk_portfolio_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- course
-- @Column(nullable=false) var teacherId: Long  (순수 Long, FK 제약 있음)
-- @Embedded CourseOverview → name, description, curriculum
-- @Embedded CourseDuration → @Column(name="duration")
-- @Embedded CoursePricePerLesson → @Column(name="price_per_lesson")
-- @Embedded CourseMinimumLessonCount → @Column(name="minimum_lesson_count")
-- @Index(name="teacher_id_idx", columnList="teacher_id")
-- =============================================================
CREATE TABLE course (
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    description          VARCHAR(255) NOT NULL,
    curriculum           VARCHAR(255) NOT NULL,
    teacher_id           BIGINT       NOT NULL,
    duration             INT          NOT NULL,
    price_per_lesson     INT          NOT NULL,
    minimum_lesson_count INT          NOT NULL,
    created_date         DATETIME(6),
    updated_date         DATETIME(6),
    PRIMARY KEY (id),
    KEY teacher_id_idx (teacher_id),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- lesson
-- @JoinColumn(name="course_id", nullable=false) @OneToMany on Course
-- → FK course_id in lesson table
-- @Embedded LessonSchedule → day_of_week (enum), start_time
-- @Index(course_id_schedule_idx: course_id, day_of_week, start_time)
-- lesson_status_type: AVAILABLE | NOT_AVAILABLE | RESERVED
-- class_room_status_type: CLOSED | OPEN
-- =============================================================
CREATE TABLE lesson (
    id                     BIGINT AUTO_INCREMENT NOT NULL,
    day_of_week            VARCHAR(31),
    start_time             TIME(6)      NOT NULL,
    student_id             BIGINT,
    lesson_remaining       INT,
    lesson_status_type     VARCHAR(31)  NOT NULL,
    class_room_status_type VARCHAR(31)  NOT NULL,
    course_id              BIGINT       NOT NULL,
    created_date           DATETIME(6),
    updated_date           DATETIME(6),
    PRIMARY KEY (id),
    KEY course_id_schedule_idx (course_id, day_of_week, start_time),
    CONSTRAINT fk_lesson_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- record
-- studentId: Long (@Column nullable=false)
-- feedbacks: @OneToMany(mappedBy="record") → FK record_id in feedback table
-- @Embedded RecordTitle → @Column(name="title")
-- =============================================================
CREATE TABLE record (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    title        VARCHAR(255) NOT NULL,
    student_id   BIGINT       NOT NULL,
    file_key     VARCHAR(255) NOT NULL,
    url          VARCHAR(255) NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_record_student (student_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- feedback
-- @ManyToOne record: Record → @JoinColumn(name="record_id", nullable=false)
-- @ManyToOne teacher: Teacher → @JoinColumn(nullable=false) → teacher_id
-- _detail: @Lob → LONGTEXT
-- status: REQUESTED | COMPLETED
-- =============================================================
CREATE TABLE feedback (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    record_id    BIGINT       NOT NULL,
    teacher_id   BIGINT       NOT NULL,
    _detail      LONGTEXT,
    status       VARCHAR(31),
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_feedback_record  (record_id),
    KEY idx_feedback_teacher (teacher_id),
    CONSTRAINT fk_feedback_record  FOREIGN KEY (record_id)  REFERENCES record  (id),
    CONSTRAINT fk_feedback_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- feedback_item
-- @ManyToOne teacher: Teacher → teacher_id FK
-- amount: 판매 가능 잔여 수량
-- =============================================================
CREATE TABLE feedback_item (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    teacher_id   BIGINT       NOT NULL,
    description  VARCHAR(255) NOT NULL,
    price        INT          NOT NULL,
    amount       INT          NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_fi_teacher (teacher_id),
    CONSTRAINT fk_fi_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- feedback_ticket
-- @ManyToOne feedbackItem: FeedbackItem → feedback_item_id FK
-- @ManyToOne student: Student? → student_id FK (nullable)
-- _amount: backing field → _amount 컬럼
-- @Version → optimistic lock
-- @UniqueConstraint(student_id, feedback_item_id)
-- =============================================================
CREATE TABLE feedback_ticket (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    feedback_item_id BIGINT NOT NULL,
    student_id       BIGINT,
    _amount          INT,
    version          INT,
    created_date     DATETIME(6),
    updated_date     DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ft_student_item (student_id, feedback_item_id),
    CONSTRAINT fk_ft_item    FOREIGN KEY (feedback_item_id) REFERENCES feedback_item (id),
    CONSTRAINT fk_ft_student FOREIGN KEY (student_id)       REFERENCES student (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- review
-- @ManyToOne teacher: Teacher → @JoinColumn(name="teacher_id")
-- @ManyToOne writer: Student → @JoinColumn(name="writer_id")
-- =============================================================
CREATE TABLE review (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    teacher_id   BIGINT,
    writer_id    BIGINT,
    detail       VARCHAR(255) NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_review_teacher (teacher_id),
    CONSTRAINT fk_review_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id),
    CONSTRAINT fk_review_writer  FOREIGN KEY (writer_id)  REFERENCES student (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- rating
-- @ManyToOne review: Review → @JoinColumn(name="review_id")
-- @Embedded RatingScore → @Column(name="score")
-- category: KINDNESS | KNOWLEDGE | PASSION
-- =============================================================
CREATE TABLE rating (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    review_id    BIGINT NOT NULL,
    category     VARCHAR(31),
    score        INT,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_rating_review (review_id),
    CONSTRAINT fk_rating_review FOREIGN KEY (review_id) REFERENCES review (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- report
-- @ManyToOne reporter: Member → @JoinColumn(name="reporter_id")
-- @ManyToOne reported: Member → @JoinColumn(name="reported_id")
-- report_type: INSULT | SPAM | SEXUAL
-- =============================================================
CREATE TABLE report (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    reporter_id  BIGINT,
    reported_id  BIGINT,
    report_type  VARCHAR(31),
    detail       VARCHAR(255) NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id),
    CONSTRAINT fk_report_reported FOREIGN KEY (reported_id) REFERENCES member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =============================================================
-- report_image
-- @ManyToOne report: Report → @JoinColumn(name="report_id")
-- url: java.net.URL → VARCHAR(2048)
-- =============================================================
CREATE TABLE report_image (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    report_id    BIGINT NOT NULL,
    url          VARCHAR(2048) NOT NULL,
    created_date DATETIME(6),
    updated_date DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_ri_report (report_id),
    CONSTRAINT fk_ri_report FOREIGN KEY (report_id) REFERENCES report (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'Schema created successfully.' AS status;
