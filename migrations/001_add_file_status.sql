-- =============================================================================
-- Migration: 001_add_file_status.sql
-- Description: Backward-compatible addition of status column to files table
-- Target DB: PostgreSQL (HomeCloud)
-- =============================================================================

-- Step 1: Add status column as nullable VARCHAR(50) if it does not exist
ALTER TABLE files ADD COLUMN IF NOT EXISTS status VARCHAR(50);

-- Step 2: Backfill all existing file records with 'ACTIVE'
UPDATE files SET status = 'ACTIVE' WHERE status IS NULL;

-- Step 3: Set column default value for all future inserts
ALTER TABLE files ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- Step 4: Apply NOT NULL constraint now that all rows are guaranteed non-null
ALTER TABLE files ALTER COLUMN status SET NOT NULL;

-- Step 5: Add CHECK constraint to enforce allowed status values ('ACTIVE', 'PENDING_DELETION')
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_files_status'
    ) THEN
        ALTER TABLE files ADD CONSTRAINT chk_files_status CHECK (status IN ('ACTIVE', 'PENDING_DELETION'));
    END IF;
END $$;

-- Step 6: Create index on (user_id, status) for optimized query performance
CREATE INDEX IF NOT EXISTS idx_files_user_status ON files (user_id, status);
