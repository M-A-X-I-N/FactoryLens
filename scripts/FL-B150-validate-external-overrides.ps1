param(
    [string]$Clangd
)

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$B110Script = Join-Path $PSScriptRoot "FL-B110-validate-semantic-backend.ps1"

if (-not (Test-Path -LiteralPath $B110Script -PathType Leaf)) {
    throw "FL-B110 validation script was not found: $B110Script"
}

Write-Host "FactoryLens FL-B150 validation"
Write-Host "Repository : $RepositoryRoot"
Write-Host ""
Write-Host "[1/3] Resolving the supported JDK 25 + clangd 20 environment..."
Write-Host ""

$environmentArguments = @{
    EnvironmentOnly = $true
}
if ($Clangd) {
    $environmentArguments["Clangd"] = $Clangd
}

& $B110Script @environmentArguments

$javaHome = $env:JAVA_HOME
$resolvedClangd = $env:FACTORYLENS_CLANGD

if (-not $javaHome -or -not (Test-Path -LiteralPath (Join-Path $javaHome "bin\java.exe") -PathType Leaf)) {
    throw "Resolved FL-B110 JDK is missing or invalid: $javaHome"
}
if (-not $resolvedClangd -or -not (Test-Path -LiteralPath $resolvedClangd -PathType Leaf)) {
    throw "Resolved FL-B110 clangd is missing or invalid: $resolvedClangd"
}

Write-Host ""
Write-Host "[2/3] Generating and auditing the B3-proven Clang compile view..."
Write-Host ""

Push-Location $RepositoryRoot
try {
    & ".\gradlew.bat" ":cli:run" "--args=compile-metadata --compiler clang"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "[3/3] Discovering supported RSS2 external-override roots..."
Write-Host "      Per-header semantic discovery progress will stream below."
Write-Host ""

Push-Location $RepositoryRoot
try {
    & ".\gradlew.bat" ":cli:run" "--args=external-override-check"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
