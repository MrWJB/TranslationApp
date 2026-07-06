# Import TranslationApp incremental SQL export into MySQL
param(
    [string]$ImportDir = "",
    [string]$DbHost = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = "1234qwer",
    [string]$Database = "translation_app",
    [switch]$SkipInit
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if (-not $ImportDir) {
    $ImportDir = Join-Path $Root "data-exports\incremental\2026-07-04"
}

$pyArgs = @(
    (Join-Path $Root "scripts\import-db.py"),
    "--dir", $ImportDir,
    "--host", $DbHost,
    "--port", $Port,
    "--user", $User,
    "--password", $Password,
    "--database", $Database
)
if ($SkipInit) { $pyArgs += "--skip-init" }

python @pyArgs
