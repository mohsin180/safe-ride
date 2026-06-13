# load-env.ps1 — load a .env file into the CURRENT PowerShell session so the
# Spring services (which don't read .env themselves) pick the values up as
# environment variables.
#
# IMPORTANT: dot-source it so the variables persist in your shell:
#     . .\load-env.ps1            # loads ./.env
#     . .\load-env.ps1 .env.prod  # loads a specific file
# Then start a service from the SAME shell:
#     cd user-services ; .\mvnw.cmd spring-boot:run

param([string]$Path = ".env")

if (-not (Test-Path $Path)) {
    Write-Host "No env file at '$Path'. Copy .env.example to .env first." -ForegroundColor Yellow
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
