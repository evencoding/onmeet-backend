ALTER TABLE file_metadata 
ADD COLUMN owner_type VARCHAR(50) NOT NULL DEFAULT 'USER',
ADD COLUMN owner_id VARCHAR(255) NOT NULL,
MODIFY COLUMN uploader_id BIGINT NULL;

-- For existing records, set owner_id to uploader_id
UPDATE file_metadata SET owner_id = CAST(uploader_id AS CHAR) WHERE owner_id IS NULL OR owner_id = '';
