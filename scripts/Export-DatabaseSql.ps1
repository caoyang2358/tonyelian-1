[CmdletBinding()]
param(
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$OutputPath = if ([string]::IsNullOrWhiteSpace($OutputPath)) { Join-Path $repoRoot 'database\copper_twin_full.sql' } else { $OutputPath }
$migrationDir = Join-Path $repoRoot 'src\main\resources\db\migration'
$output = [System.Collections.Generic.List[string]]::new()
$output.Add('-- Copper Twin standalone database export')
$output.Add('-- Generated from Flyway migrations; target PostgreSQL 14+ (also H2 PostgreSQL mode).')
$output.Add('-- Execute against an empty database. Do not run through Flyway.')
$output.Add('')

Get-ChildItem -LiteralPath $migrationDir -Filter 'V*.sql' -File | Sort-Object Name | ForEach-Object {
    $output.Add("-- ===== $($_.Name) =====")
    $output.AddRange([string[]](Get-Content -LiteralPath $_.FullName -Encoding UTF8))
    $output.Add('')
}

$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputPath)) { $OutputPath } else { Join-Path $repoRoot $OutputPath }
$parent = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $parent | Out-Null
[System.IO.File]::WriteAllText($resolvedOutput, ($output -join [Environment]::NewLine) + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Write-Output "Generated $resolvedOutput"
