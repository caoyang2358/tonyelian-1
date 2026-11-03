param(
    [string]$BaseUrl = "http://localhost:8080",
    [double]$IntervalSeconds = 10,
    [int]$Rounds = 0,
    [ValidateSet("normal", "showcase")][string]$Scenario = "showcase",
    [int]$Seed = 20260903
)
$ErrorActionPreference = "Stop"
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command py -ErrorAction SilentlyContinue }
if (-not $python) { throw "Python 3.10+ is required. Use generate_demo_data.py on Linux/macOS." }
& $python.Source (Join-Path $PSScriptRoot "generate_demo_data.py") --base-url $BaseUrl --interval $IntervalSeconds --rounds $Rounds --scenario $Scenario --seed $Seed
exit $LASTEXITCODE
