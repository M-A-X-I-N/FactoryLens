param(
    [string]$Clangd
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

function Get-ClangdMajor {
    param([Parameter(Mandatory = $true)][string]$ClangdExecutable)

    try {
        $versionText = (& $ClangdExecutable --version 2>&1 | Select-Object -First 1).ToString()
        if ($versionText -match 'clangd version\s+(?<major>\d+)') {
            return [int]$Matches.major
        }
    }
    catch {
        return $null
    }

    return $null
}

function Find-Clangd20 {
    param([string]$ExplicitPath)

    $candidates = [System.Collections.Generic.List[string]]::new()

    if ($ExplicitPath) {
        $candidates.Add($ExplicitPath)
    }

    if ($env:FACTORYLENS_CLANGD) {
        $candidates.Add($env:FACTORYLENS_CLANGD)
    }

    $dotenvValue = Get-DotEnvValue -Path (Join-Path $RepositoryRoot ".env") -Key "FACTORYLENS_CLANGD"
    if ($dotenvValue) {
        $candidates.Add($dotenvValue)
    }

    # This was the recommended side-by-side install location used by the B3 research.
    $candidates.Add((Join-Path $env:ProgramFiles "LLVM-20.1.8\bin\clangd.exe"))

    $pathClangd = Get-Command clangd.exe -ErrorAction SilentlyContinue
    if ($pathClangd) {
        $candidates.Add($pathClangd.Source)
    }

    if ($env:LOCALAPPDATA) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Links\clangd.exe"))
    }

    foreach ($programFilesRoot in @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }) {
        Get-ChildItem -LiteralPath $programFilesRoot -Directory -Filter "LLVM*" -ErrorAction SilentlyContinue |
            ForEach-Object {
                $candidates.Add((Join-Path $_.FullName "bin\clangd.exe"))
            }

        $visualStudioRoot = Join-Path $programFilesRoot "Microsoft Visual Studio\2022"
        if (Test-Path -LiteralPath $visualStudioRoot) {
            Get-ChildItem -LiteralPath $visualStudioRoot -Directory -ErrorAction SilentlyContinue |
                ForEach-Object {
                    $candidates.Add((Join-Path $_.FullName "VC\Tools\Llvm\x64\bin\clangd.exe"))
                    $candidates.Add((Join-Path $_.FullName "VC\Tools\Llvm\bin\clangd.exe"))
                }
        }
    }

    $checked = [System.Collections.Generic.List[string]]::new()
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not $candidate) {
            continue
        }

        $expanded = [Environment]::ExpandEnvironmentVariables($candidate)
        if (-not [System.IO.Path]::IsPathRooted($expanded)) {
            $expanded = Join-Path $RepositoryRoot $expanded
        }

        $checked.Add($expanded)
        if (-not (Test-Path -LiteralPath $expanded -PathType Leaf)) {
            continue
        }

        $resolved = (Resolve-Path -LiteralPath $expanded).Path
        if ((Get-ClangdMajor -ClangdExecutable $resolved) -eq 20) {
            return $resolved
        }
    }

    $checkedText = ($checked | Select-Object -Unique) -join "`n  - "
    throw "clangd major 20 was not found. Checked:`n  - $checkedText"
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

$resolvedClangd = Find-Clangd20 -ExplicitPath $Clangd
$env:FACTORYLENS_CLANGD = $resolvedClangd

Write-Host "FactoryLens FL-B110 validation"
Write-Host "Repository : $RepositoryRoot"
Write-Host "JAVA_HOME  : $javaHome"
Write-Host "clangd     : $resolvedClangd"
& $resolvedClangd --version | Select-Object -First 1
& (Join-Path $javaHome "bin\java.exe") -version

Push-Location $RepositoryRoot
try {
    & ".\gradlew.bat" --stop
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle daemon shutdown failed with exit code $LASTEXITCODE."
    }

    Write-Host ""
    Write-Host "Generating the B3-proven Clang compile view..."
    & ".\gradlew.bat" ":cli:run" "--args=compile-metadata --compiler clang"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Write-Host ""
    Write-Host "Starting the persistent clangd backend session..."
    & ".\gradlew.bat" ":cli:run" "--args=backend-check"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
