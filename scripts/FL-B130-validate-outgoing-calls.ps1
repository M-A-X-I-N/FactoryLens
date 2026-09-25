param(
    [string]$Clangd
)

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$B110Script = Join-Path $PSScriptRoot "FL-B110-validate-semantic-backend.ps1"

if (-not (Test-Path -LiteralPath $B110Script -PathType Leaf)) {
    throw "FL-B110 validation script was not found: $B110Script"
}

Write-Host "FactoryLens FL-B130 validation"
Write-Host "Repository : $RepositoryRoot"
Write-Host ""
Write-Host "Establishing the supported FL-B110 Clang environment first..."

$arguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $B110Script
)
if ($Clangd) {
    $arguments += @("-Clangd", $Clangd)
}

$previousErrorActionPreference = $ErrorActionPreference
try {
    # Windows PowerShell 5.1 wraps native stderr from the child process as
    # non-terminating ErrorRecord objects. Preserve that output, but judge
    # success from the child process exit code instead of treating stderr text
    # (for example Java/Gradle diagnostics) as a terminating script failure.
    $ErrorActionPreference = "Continue"
    $b110Output = & powershell.exe @arguments 2>&1
    $b110Exit = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$b110Output | ForEach-Object { Write-Host $_ }

if ($b110Exit -ne 0) {
    exit $b110Exit
}

$javaHomeLine = $b110Output |
    Where-Object { $_.ToString().StartsWith("JAVA_HOME  : ") } |
    Select-Object -Last 1
$clangdLine = $b110Output |
    Where-Object { $_.ToString().StartsWith("clangd     : ") } |
    Select-Object -Last 1

if (-not $javaHomeLine -or -not $clangdLine) {
    throw "Could not recover JAVA_HOME/clangd from successful FL-B110 validation output."
}

$javaHome = $javaHomeLine.ToString().Substring("JAVA_HOME  : ".Length).Trim()
$resolvedClangd = $clangdLine.ToString().Substring("clangd     : ".Length).Trim()

if (-not (Test-Path -LiteralPath (Join-Path $javaHome "bin\java.exe") -PathType Leaf)) {
    throw "Resolved FL-B110 JDK no longer exists: $javaHome"
}
if (-not (Test-Path -LiteralPath $resolvedClangd -PathType Leaf)) {
    throw "Resolved FL-B110 clangd no longer exists: $resolvedClangd"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$env:FACTORYLENS_CLANGD = $resolvedClangd

Write-Host ""
Write-Host "Expanding the supported RSS2 FL-B130 semantic specimen..."

Push-Location $RepositoryRoot
try {
    & ".\gradlew.bat" ":cli:run" "--args=call-expand-check"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
