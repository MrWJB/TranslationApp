# Export all TranslationApp MySQL tables into date-named incremental folders.
param(
    [string]$DbHost = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = "1234qwer",
    [string]$Database = "translation_app",
    [string]$MySqlBin = "D:\software\MySQL\MySQL Server 8.4\bin",
    [string]$ExportRoot = "",
    [ValidateSet("overwrite", "timestamp")]
    [string]$SameDayMode = "overwrite",
    [switch]$IncludeSchema
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if (-not $ExportRoot) {
    $ExportRoot = Join-Path $Root "data-exports\incremental"
}

$mysqlExe = Join-Path $MySqlBin "mysql.exe"
$mysqldumpExe = Join-Path $MySqlBin "mysqldump.exe"

foreach ($exe in @($mysqlExe, $mysqldumpExe)) {
    if (-not (Test-Path $exe)) {
        throw "MySQL client not found at: $exe (set -MySqlBin to your MySQL bin directory)"
    }
}

$dateStamp = Get-Date -Format "yyyy-MM-dd"
$exportDir = Join-Path $ExportRoot $dateStamp

if (Test-Path $exportDir) {
    if ($SameDayMode -eq "timestamp") {
        $timeStamp = Get-Date -Format "HH-mm-ss"
        $exportDir = Join-Path $ExportRoot "${dateStamp}_${timeStamp}"
    } else {
        Write-Host "Same-day folder exists; overwriting contents of $exportDir"
        Get-ChildItem -Path $exportDir -Force | Remove-Item -Recurse -Force
    }
}

New-Item -ItemType Directory -Path $exportDir -Force | Out-Null

$mysqlBaseArgs = @(
    "-h", $DbHost,
    "-P", $Port,
    "-u", $User,
    "-p$Password",
    "--default-character-set=utf8mb4",
    "-N",
    "-B"
)

Write-Host "Listing tables in database '$Database'..."
$tableQuery = @"
SELECT table_name
FROM information_schema.tables
WHERE table_schema = '$Database'
  AND table_type = 'BASE TABLE'
ORDER BY table_name
"@

$tables = & $mysqlExe @mysqlBaseArgs -e $tableQuery
if (-not $tables) {
    throw "No tables found in database '$Database'"
}

$exportStarted = Get-Date
$tableMeta = @()

Write-Host "Exporting $($tables.Count) tables to $exportDir"

foreach ($table in $tables) {
    $table = $table.Trim()
    if (-not $table) { continue }

    $outFile = Join-Path $exportDir "$table.sql"
    Write-Host "  -> $table"

    $dumpArgs = @(
        "-h", $DbHost,
        "-P", $Port,
        "-u", $User,
        "-p$Password",
        "--default-character-set=utf8mb4",
        "--single-transaction",
        "--skip-triggers",
        "--no-create-info",
        "--complete-insert",
        $Database,
        $table
    )

    & $mysqldumpExe @dumpArgs | Set-Content -Path $outFile -Encoding UTF8

    $rowCount = & $mysqlExe @mysqlBaseArgs -e "SELECT COUNT(*) FROM ``$Database``.``$table``"
    $fileInfo = Get-Item $outFile

    $tableMeta += [ordered]@{
        name     = $table
        file     = "$table.sql"
        rowCount = [int]$rowCount
        bytes    = $fileInfo.Length
    }
}

if ($IncludeSchema) {
    $schemaFile = Join-Path $exportDir "_schema.sql"
    Write-Host "  -> _schema.sql (full DDL)"
    $schemaArgs = @(
        "-h", $DbHost,
        "-P", $Port,
        "-u", $User,
        "-p$Password",
        "--default-character-set=utf8mb4",
        "--no-data",
        "--routines",
        "--triggers",
        $Database
    )
    & $mysqldumpExe @schemaArgs | Set-Content -Path $schemaFile -Encoding UTF8
}

$exportFinished = Get-Date
$metadata = [ordered]@{
    exportTime     = $exportFinished.ToString("o")
    exportStarted  = $exportStarted.ToString("o")
    durationMs     = [int]($exportFinished - $exportStarted).TotalMilliseconds
    database       = $Database
    host           = $DbHost
    port           = $Port
    sameDayMode    = $SameDayMode
    tableCount     = $tableMeta.Count
    tables         = $tableMeta
    includeSchema  = [bool]$IncludeSchema
}

$metadataPath = Join-Path $exportDir "metadata.json"
$metadata | ConvertTo-Json -Depth 6 | Set-Content -Path $metadataPath -Encoding UTF8

Write-Host ""
Write-Host "Export complete."
Write-Host "  Folder:   $exportDir"
Write-Host "  Tables:   $($tableMeta.Count)"
Write-Host "  Metadata: $metadataPath"
