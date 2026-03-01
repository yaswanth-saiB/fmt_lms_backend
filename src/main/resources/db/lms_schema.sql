-- ============================================
-- COURSES TABLE
-- ============================================
CREATE TABLE courses (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         title VARCHAR(200) NOT NULL,
                         description TEXT,
                         mentor_id UUID NOT NULL REFERENCES users(id),
                         batch_id UUID,
                         price DECIMAL(10,2),
                         duration_hours INTEGER,
                         status VARCHAR(50) DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, ARCHIVED
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         created_by UUID REFERENCES users(id)
);

-- ============================================
-- BATCHES TABLE
-- ============================================
CREATE TABLE batches (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         name VARCHAR(200) NOT NULL,
                         course_id UUID NOT NULL REFERENCES courses(id),
                         mentor_id UUID NOT NULL REFERENCES users(id),
                         start_date DATE,
                         end_date DATE,
                         max_students INTEGER DEFAULT 30,
                         current_students INTEGER DEFAULT 0,
                         status VARCHAR(50) DEFAULT 'UPCOMING', -- UPCOMING, ONGOING, COMPLETED
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- ENROLLMENTS TABLE (Many-to-Many: Students <-> Batches)
-- ============================================
CREATE TABLE enrollments (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             student_id UUID NOT NULL REFERENCES users(id),
                             batch_id UUID NOT NULL REFERENCES batches(id),
                             enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, DROPPED, COMPLETED
                             progress_percentage INTEGER DEFAULT 0,
                             UNIQUE(student_id, batch_id)
);

-- ============================================
-- ZOOM MEETINGS TABLE
-- ============================================
CREATE TABLE meetings (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          zoom_meeting_id VARCHAR(100) UNIQUE,
                          topic VARCHAR(500) NOT NULL,
                          description TEXT,
                          batch_id UUID REFERENCES batches(id),
                          mentor_id UUID NOT NULL REFERENCES users(id),
                          start_time TIMESTAMP NOT NULL,
                          duration_minutes INTEGER NOT NULL,
                          join_url TEXT,
                          start_url TEXT,  -- Mentor's start link (sensitive)
                          password VARCHAR(100),
                          status VARCHAR(50) DEFAULT 'SCHEDULED', -- SCHEDULED, LIVE, ENDED
                          recording_url TEXT,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================
CREATE INDEX idx_courses_mentor ON courses(mentor_id);
CREATE INDEX idx_batches_mentor ON batches(mentor_id);
CREATE INDEX idx_batches_course ON batches(course_id);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_batch ON enrollments(batch_id);
CREATE INDEX idx_meetings_batch ON meetings(batch_id);
CREATE INDEX idx_meetings_mentor ON meetings(mentor_id);
CREATE INDEX idx_meetings_start_time ON meetings(start_time);