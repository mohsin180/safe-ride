# load-env.ps1 — load a .env file into the CURRENT PowerShell session so the
# Spring services (which don't read .env themselves) pick the values up as
# environment variables.
#
# IMPORTANT: dot-source it so the variables persist in your shell:
#     . .\load-env.ps1            # loads ./.env
#     . .\load-env.ps1 .env.prod  # loads a specific file
# Then start a service from the SAME shell:
#     cd user-services ; .\mvnw.cmd spring-boot:run

param([string]$Path)

# Default to ".env" sitting next to this script (backend\.env), so it works
# no matter which service folder you dot-source it from.
if (-not $Path) { $Path = Join-Path $PSScriptRoot ".env" }
elseif (-not [System.IO.Path]::IsPathRooted($Path)) { $Path = Join-Path $PSScriptRoot $Path }

if (-not (Test-Path $Path)) {
    Write-Host "No env file at '$Path'." -ForegroundColor Yellow
    Write-Host "Create it first:  Copy-Item '$PSScriptRoot\.env.example' '$PSScriptRoot\.env'" -ForegroundColor Yellow
    return
}

$count = 0
Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }

    $name  = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()

    # strip optional surrounding quotes
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    Set-Item -Path "Env:$name" -Value $value
    $count++
}

Write-Host "Loaded $count environment variables from '$Path' into this session." -ForegroundColor Green
