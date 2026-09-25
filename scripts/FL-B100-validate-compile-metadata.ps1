param(
    [string]$SmlRoot,
    [string]$EngineRoot
)

$ErrorActionPreference = "Stop"

$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RequiredJavaMajor = 25

function Get-JavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaExecutable)

    $versionText = (& $JavaExecutable -version 2>&1 | Select-Object -First 1).ToString()
    if ($versionText -match 'version\s+"(?<major>\d+)') {
        return [int]$Matches.major
    }

    return $null
}

function Find-Java25Home {
    $candidates = [System.Collections.Generic.List[string]]::new()

    if ($env:JAVA_HOME) {
        $candidates.Add($env:JAVA_HOME)
    }

    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) {
        $candidates.Add((Split-Path (Split-Path $pathJava.Source -Parent) -Parent))
    }

    $adoptiumRoot = Join-Path $env:ProgramFiles "Eclipse Adoptium"
    if (Test-Path $adoptiumRoot) {
        Get-ChildItem $adoptiumRoot -Directory -Filter "jdk-25*" |
            Sort-Object LastWriteTime -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $java = Join-Path $candidate "bin\java.exe"
        if ((Test-Path $java) -and ((Get-JavaMajor $java) -eq $RequiredJavaMajor)) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "JDK 25 was not found. Install EclipseAdoptium.Temurin.25.JDK or set JAVA_HOME to a JDK 25 installation."
}

function Get-DotEnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Key
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }

        $parts = $trimmed.Split("=", 2)
        if ($parts[0].Trim() -eq $Key) {
            return $parts[1].Trim().Trim('"').Trim("'")
        }
    }

    return $null
}

function Resolve-ConfiguredDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $candidate = $Value.Trim().Trim('"')
    if (-not [System.IO.Path]::IsPathRooted($candidate)) {
        $candidate = Join-Path $RepositoryRoot $candidate
    }

    if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
        throw "$Label is not a directory: $candidate"
    }

    return (Resolve-Path -LiteralPath $candidate).Path
}

$javaHome = Find-Java25Home
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

$dotenvPath = Join-Path $RepositoryRoot ".env"

if (-not $SmlRoot) {
    $SmlRoot = $env:SML_PROJECT_ROOT
}
if (-not $SmlRoot) {
    $SmlRoot = Get-DotEnvValue -Path $dotenvPath -Key "SML_PROJECT_ROOT"
}
if (-not $SmlRoot) {
    $SmlRoot = Read-Host "Enter the SML Starter Project root containing FactoryGame.uproject"
}
if (-not $SmlRoot) {
    throw "SML project root is required for FL-B100 validation."
}

$SmlRoot = Resolve-ConfiguredDirectory -Value $SmlRoot -Label "SML project root"
if (-not (Test-Path -LiteralPath (Join-Path $SmlRoot "FactoryGame.uproject") -PathType Leaf)) {
    throw "FactoryGame.uproject was not found under SML project root: $SmlRoot"
}
$env:SML_PROJECT_ROOT = $SmlRoot

if (-not $EngineRoot) {
    $EngineRoot = $env:FACTORYLENS_ENGINE_ROOT
}
if (-not $EngineRoot) {
    $EngineRoot = Get-DotEnvValue -Path $dotenvPath -Key "FACTORYLENS_ENGINE_ROOT"
}
if ($EngineRoot) {
    $EngineRoot = Resolve-ConfiguredDirectory -Value $EngineRoot -Label "Unreal Engine root"
    $env:FACTORYLENS_ENGINE_ROOT = $EngineRoot
}

Write-Host "FactoryLens FL-B100 validation"
Write-Host "Repository : $RepositoryRoot"
Write-Host "SML root   : $SmlRoot"
if ($EngineRoot) {
    Write-Host "Engine root: $EngineRoot (explicit)"
} else {
    Write-Host "Engine root: resolve from EngineAssociation"
}
Write-Host "JAVA_HOME  : $javaHome"
& (Join-Path $javaHome "bin\java.exe") -version

Push-Location $RepositoryRoot
try {
    & ".\gradlew.bat" --stop
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle daemon shutdown failed with exit code $LASTEXITCODE."
    }

    & ".\gradlew.bat" ":cli:run" "--args=compile-metadata"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
