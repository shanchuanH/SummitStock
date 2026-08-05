# Backup and restore verification

Create a consistent backup with `scripts/backup.ps1 -OutputFile <absolute-path>`. Restore only into an explicitly selected disposable environment/database using `scripts/restore.ps1 -InputFile <absolute-path> -Database <disposable-database> -ConfirmRestore`.

After restore, run Flyway validation, compare row counts for users, instruments, bars, snapshots, recommendations, jobs, and backtests, then run `scripts/smoke.ps1`. Record the backup checksum, restore duration, MySQL version, migration head, and verification results. Never test a restore over the production database.
