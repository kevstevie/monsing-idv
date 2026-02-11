-- Migration: Separate Member-Teacher-Student from JPA JOINED inheritance to independent entities
-- Before: Member (JOINED inheritance) -> Teacher, Student
-- After: Member (thin identity), Teacher (standalone), Student (standalone)

-- Step 1: Add common fields to teacher table
ALTER TABLE teacher ADD COLUMN identifier VARCHAR(255);
ALTER TABLE teacher ADD COLUMN oauth_provider_type VARCHAR(50);
ALTER TABLE teacher ADD COLUMN nickname VARCHAR(255);
ALTER TABLE teacher ADD COLUMN created_date DATETIME(6);
ALTER TABLE teacher ADD COLUMN updated_date DATETIME(6);

-- Step 2: Copy common fields from member to teacher
UPDATE teacher t JOIN member m ON t.id = m.id
SET t.identifier = m.identifier,
    t.oauth_provider_type = m.oauth_provider_type,
    t.nickname = m.nickname,
    t.created_date = m.created_date,
    t.updated_date = m.updated_date;

-- Step 3: Add NOT NULL constraints to teacher common fields
ALTER TABLE teacher MODIFY COLUMN identifier VARCHAR(255) NOT NULL;
ALTER TABLE teacher MODIFY COLUMN oauth_provider_type VARCHAR(50) NOT NULL;
ALTER TABLE teacher MODIFY COLUMN nickname VARCHAR(255) NOT NULL;

-- Step 4: Add common fields to student table
ALTER TABLE student ADD COLUMN identifier VARCHAR(255);
ALTER TABLE student ADD COLUMN oauth_provider_type VARCHAR(50);
ALTER TABLE student ADD COLUMN nickname VARCHAR(255);
ALTER TABLE student ADD COLUMN created_date DATETIME(6);
ALTER TABLE student ADD COLUMN updated_date DATETIME(6);

-- Step 5: Copy common fields from member to student
UPDATE student s JOIN member m ON s.id = m.id
SET s.identifier = m.identifier,
    s.oauth_provider_type = m.oauth_provider_type,
    s.nickname = m.nickname,
    s.created_date = m.created_date,
    s.updated_date = m.updated_date;

-- Step 6: Add NOT NULL constraints to student common fields
ALTER TABLE student MODIFY COLUMN identifier VARCHAR(255) NOT NULL;
ALTER TABLE student MODIFY COLUMN oauth_provider_type VARCHAR(50) NOT NULL;
ALTER TABLE student MODIFY COLUMN nickname VARCHAR(255) NOT NULL;

-- Step 7: Uppercase member_type values (discriminator -> enum)
UPDATE member SET member_type = UPPER(member_type);

-- Step 8: Remove FK constraints from teacher and student (if exists)
-- Note: Constraint names may vary. Check actual constraint names before running.
-- ALTER TABLE teacher DROP FOREIGN KEY <fk_constraint_name>;
-- ALTER TABLE student DROP FOREIGN KEY <fk_constraint_name>;

-- Step 9: Remove common fields from member table (optional, can be deferred)
ALTER TABLE member DROP COLUMN identifier;
ALTER TABLE member DROP COLUMN oauth_provider_type;
ALTER TABLE member DROP COLUMN nickname;
ALTER TABLE member DROP COLUMN created_date;
ALTER TABLE member DROP COLUMN updated_date;
