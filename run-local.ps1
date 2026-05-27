# run-local.ps1
# Helper script to load .env environment variables and launch the Spring Boot backend.

$envFile = Join-Path $PSScriptRoot ".env"

if (Test-Path $envFile) {
    Write-Host "--------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "Loading environment variables from .env..." -ForegroundColor Cyan
    Write-Host "--------------------------------------------------------" -ForegroundColor Cyan
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        # Skip empty lines and comment lines
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $name, $value = $line -split '=', 2
            $name = $name.Trim()
            $value = $value.Trim()
            # If value is surrounded by quotes, remove them
            if ($value.StartsWith('"') -and $value.EndsWith('"')) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
            Write-Host "  Set: $name" -ForegroundColor Gray
        }
    }
    Write-Host "Environment variables successfully loaded!" -ForegroundColor Green
} else {
    Write-Host "WARNING: No .env file found at the project root." -ForegroundColor Yellow
    Write-Host "Please copy .env.example to .env and fill in your Supabase and Jira details." -ForegroundColor Yellow
}

Write-Host "--------------------------------------------------------" -ForegroundColor Cyan
Write-Host "Navigating to backend directory and running the application..." -ForegroundColor Cyan
Write-Host "--------------------------------------------------------" -ForegroundColor Cyan

Set-Location -Path (Join-Path $PSScriptRoot "backend")
.\mvnw.cmd spring-boot:run
