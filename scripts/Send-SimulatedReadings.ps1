param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Rounds = 12,
    [int]$IntervalSeconds = 5
)

$points = @(
    @{ sensor="TEMP-001"; parameter="temperature"; value=1176; unit="C" },
    @{ sensor="TEMP-002"; parameter="temperature"; value=1182; unit="C" },
    @{ sensor="TEMP-003"; parameter="temperature"; value=1169; unit="C" },
    @{ sensor="PRESS-001"; parameter="pressure"; value=-18; unit="Pa" },
    @{ sensor="LEVEL-001"; parameter="level"; value=72.6; unit="PCT" },
    @{ sensor="SPEED-001"; parameter="speed"; value=32.4; unit="M_PER_MIN" },
    @{ sensor="DIAM-ROD-001"; parameter="diameter"; value=8.0; unit="MM" },
    @{ sensor="SPEED-003"; parameter="speed"; value=23.4; unit="M_PER_SEC" },
    @{ sensor="TENSION-003"; parameter="tension"; value=20.2; unit="N" },
    @{ sensor="DIAM-WIRE-002"; parameter="diameter"; value=1.821; unit="MM" }
)

for ($round = 1; $round -le $Rounds; $round++) {
    $now = [DateTimeOffset]::Now.ToString("o")
    $readings = foreach ($point in $points) {
        $jitter = (Get-Random -Minimum -10 -Maximum 11) / 1000.0
        @{
            sourceRecordId = "sim-$round-$($point.sensor)-$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
            sensorCode = $point.sensor
            parameterCode = $point.parameter
            collectTime = $now
            valueNum = [Math]::Round($point.value * (1 + $jitter), 3)
            sourceUnit = $point.unit
            qualityCode = "GOOD"
        }
    }
    $body = @{ sourceSystem="SIMULATOR"; readings=$readings } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/ingest/sensor-readings:batch" -ContentType "application/json; charset=utf-8" -Body $body | Out-Null
    Write-Host "Round $round sent at $now"
    if ($round -lt $Rounds) { Start-Sleep -Seconds $IntervalSeconds }
}
