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

$javaHome = Find-Java25Home
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

Write-Host "FactoryLens FL-B100 validation"
Write-Host "Repository : $RepositoryRoot"
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
