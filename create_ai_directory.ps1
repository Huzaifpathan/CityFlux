#!/usr/bin/env powershell
# Create the AI module directory structure for CityFlux
$directoryPath = "c:\Users\sh\AndroidStudioProjects\HACK-THE-GAP-CODE-DOMINATORS\app\src\main\java\com\example\cityflux\ui\ai"

try {
    if (-not (Test-Path $directoryPath)) {
        New-Item -ItemType Directory -Path $directoryPath -Force | Out-Null
        Write-Host "✓ Directory created successfully: $directoryPath" -ForegroundColor Green
    } else {
        Write-Host "✓ Directory already exists: $directoryPath" -ForegroundColor Green
    }
    
    # Verify the directory was created
    if (Test-Path $directoryPath) {
        Write-Host "✓ Verification: Directory exists and is accessible" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Error: $_" -ForegroundColor Red
    exit 1
}
