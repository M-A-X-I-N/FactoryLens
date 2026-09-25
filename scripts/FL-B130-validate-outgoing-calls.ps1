param(
    [string]$Clangd
)

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$B110Script = Join-Path $PSScriptRoot "FL-B110-validate-semantic-backend.ps1"

if (-not (Test-Path -LiteralPath $B110Script -PathType Leaf)) {
    throw "FL-B110 validation script was not found: $B110Script"
}

$handoffPath = Join-Path $RepositoryRoot "work\factorylens\validation\b130\b110-environment.json"
if (Test-Path -LiteralPath $handoffPath) {
    Remove-Item -LiteralPath $handoffPath -Force
}

Write-Host "FactoryLens FL-B130 validation"
Write-Host "Repository : $RepositoryRoot"
Write-Host ""
Write-Host "[1/2] Establishing the supported FL-B110 Clang environment..."
Write-Host "      FL-B110 output will stream live below."
Write-Host ""

$arguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $B110Script,
    "-EnvironmentOutput", $handoffPath
)
if ($Clangd) {
    $arguments += @("-Clangd", $Clangd)
}

& powershell.exe @arguments
$b110Exit = $LASTEXITCODE

if ($b110Exit -ne 0) {
    exit $b110Exit
}
if (-not (Test-Path -LiteralPath $handoffPath -PathType Leaf)) {
    throw "FL-B110 succeeded but did not write the expected environment handoff: $handoffPath"
}

$resolvedEnvironment = Get-Content -LiteralPath $handoffPath -Raw | ConvertFrom-Json
$javaHome = [string]$resolvedEnvironment.JAVA_HOME
$resolvedClangd = [string]$resolvedEnvironment.FACTORYLENS_CLANGD

if (-not $javaHome -or -not (Test-Path -LiteralPath (Join-Path $javaHome "bin\java.exe") -PathType Leaf)) {
    throw "Resolved FL-B110 JDK is missing or invalid: $javaHome"
}
if (-not $resolvedClangd -or -not (Test-Path -LiteralPath $resolvedClangd -PathType Leaf)) {
    throw "Resolved FL-B110 clangd is missing or invalid: $resolvedClangd"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$env:FACTORYLENS_CLANGD = $resolvedClangd

Write-Host ""
Write-Host "[2/2] Expanding the supported RSS2 FL-B130 semantic specimen..."
Write-Host "      clangd semantic output will stream live below."
Write-Host ""

Push-Location $RepositoryRoot
try {
    & ".\gradlew.bat" ":cli:run" "--args=call-expand-check"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
    if (Test-Path -LiteralPath $handoffPath) {
        Remove-Item -LiteralPath $handoffPath -Force -ErrorAction SilentlyContinue
    }
}
