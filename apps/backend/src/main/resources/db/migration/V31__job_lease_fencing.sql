ALTER TABLE job_run
    ADD COLUMN lease_token BINARY(16) NULL AFTER lease_owner;

UPDATE job_run
SET status='PENDING', lease_owner=NULL, lease_token=NULL, lease_expires_at=NULL,
    scheduled_at=UTC_TIMESTAMP(6), updated_at=UTC_TIMESTAMP(6), version=version+1
WHERE status='RUNNING';
