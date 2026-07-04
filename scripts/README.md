# Scripts

## Database

| Script | Purpose |
|--------|---------|
| `init-db.ps1` | Create database, apply schema and seed |
| `export-db.ps1` | Export all table data to date-named incremental folders |

### Export all tables (`export-db.ps1`)

Exports every table in `translation_app` as a separate SQL file (INSERT statements, no DDL).

**Folder layout:**

```
data-exports/incremental/
  2026-07-04/
    metadata.json
    users.sql
    crawl_tasks.sql
    documents.sql
    ...
```

- One folder per calendar day (`YYYY-MM-DD`).
- Re-running on the same day **overwrites** that day's folder by default.
- Use `-SameDayMode timestamp` to keep multiple runs per day (`2026-07-04_15-30-00/`).
- `metadata.json` records export time, table list, and row counts.

**Examples (PowerShell, from repo root):**

```powershell
# Default: local MySQL, password 1234qwer
.\scripts\export-db.ps1

# Custom credentials / client path
.\scripts\export-db.ps1 -Password 1234qwer -MySqlBin "D:\software\MySQL\MySQL Server 8.4\bin"

# Keep history when exporting twice on the same day
.\scripts\export-db.ps1 -SameDayMode timestamp

# Also dump full schema DDL as _schema.sql
.\scripts\export-db.ps1 -IncludeSchema
```

**Parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `-DbHost` | `localhost` | MySQL host |
| `-Port` | `3306` | MySQL port |
| `-User` | `root` | MySQL user |
| `-Password` | `1234qwer` | MySQL password |
| `-Database` | `translation_app` | Database name |
| `-MySqlBin` | `D:\software\MySQL\MySQL Server 8.4\bin` | Directory containing `mysql.exe` and `mysqldump.exe` |
| `-ExportRoot` | `<repo>/data-exports/incremental` | Root directory for dated export folders |
| `-SameDayMode` | `overwrite` | `overwrite` or `timestamp` |
| `-IncludeSchema` | off | Also write `_schema.sql` (DDL only) |

## Infrastructure & startup

| Script | Purpose |
|--------|---------|
| `start-infra.ps1` | Start Redis, MinIO (optional MySQL/coturn) via Docker |
| `start-all.ps1` | Start crawler, backend, and frontend |
