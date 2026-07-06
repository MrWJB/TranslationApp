# Initialize TranslationApp MySQL database (Windows PowerShell)
param(
    [string]$DbHost = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = "1234qwer",
    [string]$Database = "translation_app",
    [string]$MySqlBin = "D:\software\mysql8.3\bin\mysql.exe"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Schema = Join-Path $Root "backend\src\main\resources\db\schema.sql"
$Seed = Join-Path $Root "backend\src\main\resources\db\seed.sql"

if (-not (Test-Path $MySqlBin)) {
    throw "mysql.exe not found at: $MySqlBin"
}

$mysqlArgs = @("-h", $DbHost, "-P", $Port, "-u", $User, "-p$Password", "--default-character-set=utf8mb4", $Database)

Write-Host "Creating database if not exists: $Database"
& $MySqlBin -h $DbHost -P $Port -u $User -p$Password -e "CREATE DATABASE IF NOT EXISTS $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

Write-Host "Applying schema from $Schema"
Get-Content $Schema -Raw -Encoding UTF8 | & $MySqlBin @mysqlArgs

Write-Host "Applying seed data from $Seed"
Get-Content $Seed -Raw -Encoding UTF8 | & $MySqlBin @mysqlArgs

Write-Host "Database '$Database' initialized."
