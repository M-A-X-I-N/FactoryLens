param(
    [string]$Clangd,
    [string]$EnvironmentOutput,
    [switch]$EnvironmentOnly
)

$ErrorActionPreference = "Stop"

$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RequiredJavaMajor = 25
$PortableClangdVersion = "20.1.8"
$PortableClangdSha256 = "717a0700fc660574647468b3d0b67e46a077d27e4da794d9d0c212add6ba6765"
$PortableClangdUrl = "https://github.com/clangd/clangd/releases/download/20.1.8/clangd-windows-20.1.8.zip"

function Get-NativeVersionText {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string]$Argument
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.Arguments = $Argument
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    if (-not $process.Start()) {
        throw "Failed to start version probe: $Executable $Argument"
    }

    try {
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        if ($process.ExitCode -ne 0) {
            throw "Version probe exited with code $($process.ExitCode): $Executable $Argument"
        }

        return (($stdout + [Environment]::NewLine + $stderr).Trim())
    }
    finally {
        $process.Dispose()
    }
}

function Get-JavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaExecutable)

    $versionText = Get-NativeVersionText -Executable $JavaExecutable -Argument "-version"
    $firstLine = ($versionText -split "\r?\n" | Where-Object { $_ } | Select-Object -First 1)
    if ($firstLine -match 'version\s+"(?<major>\d+)') {
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
        $versionText = Get-NativeVersionText -Executable $ClangdExecutable -Argument "--version"
        $firstLine = ($versionText -split "\r?\n" | Where-Object { $_ } | Select-Object -First 1)
        if ($firstLine -match 'clangd version\s+(?<major>\d+)') {
            return [int]$Matches.major
        }
    }
    catch {
        return $null
    }

    return $null
}

function Install-PortableClangd20 {
    $toolchainRoot = Join-Path $RepositoryRoot "work\factorylens\toolchains\clangd-$PortableClangdVersion"
    $downloadRoot = Join-Path $RepositoryRoot "work\factorylens\downloads"
    $archivePath = Join-Path $downloadRoot "clangd-windows-$PortableClangdVersion.zip"

    New-Item -ItemType Directory -Force -Path $toolchainRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null

    $existing = Get-ChildItem -LiteralPath $toolchainRoot -Recurse -Filter "clangd.exe" -File -ErrorAction SilentlyContinue |
        Where-Object { (Get-ClangdMajor -ClangdExecutable $_.FullName) -eq 20 } |
        Select-Object -First 1
    if ($existing) {
        return $existing.FullName
    }

    $needDownload = $true
    if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
        $hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -eq $PortableClangdSha256) {
            $needDownload = $false
        } else {
            Remove-Item -LiteralPath $archivePath -Force
        }
    }

    if ($needDownload) {
        Write-Host "clangd 20 not found locally; downloading portable clangd $PortableClangdVersion..."
        Invoke-WebRequest -Uri $PortableClangdUrl -OutFile $archivePath
    }

    $hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $PortableClangdSha256) {
        throw "Portable clangd archive hash mismatch. Expected $PortableClangdSha256 but got $hash."
    }

    Remove-Item -LiteralPath $toolchainRoot -Recurse -Force
    New-Item -ItemType Directory -Force -Path $toolchainRoot | Out-Null
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolchainRoot -Force

    $clangd = Get-ChildItem -LiteralPath $toolchainRoot -Recurse -Filter "clangd.exe" -File |
        Where-Object { (Get-ClangdMajor -ClangdExecutable $_.FullName) -eq 20 } |
        Select-Object -First 1
    if (-not $clangd) {
        throw "Portable clangd $PortableClangdVersion was extracted but no clangd major 20 executable was found."
    }

    return $clangd.FullName
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
    Write-Host "No installed clangd major 20 was found. Checked:"
    Write-Host "  - $checkedText"
    return Install-PortableClangd20
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

if ($EnvironmentOutput) {
    $environmentOutputPath = [Environment]::ExpandEnvironmentVariables($EnvironmentOutput)
    if (-not [System.IO.Path]::IsPathRooted($environmentOutputPath)) {
        $environmentOutputPath = Join-Path $RepositoryRoot $environmentOutputPath
    }

    $environmentOutputPath = [System.IO.Path]::GetFullPath($environmentOutputPath)
    $environmentOutputParent = Split-Path $environmentOutputPath -Parent
    if ($environmentOutputParent) {
        New-Item -ItemType Directory -Force -Path $environmentOutputParent | Out-Null
    }

    @{
        JAVA_HOME = $javaHome
        FACTORYLENS_CLANGD = $resolvedClangd
    } |
        ConvertTo-Json |
        Set-Content -LiteralPath $environmentOutputPath -Encoding UTF8
}

Write-Host "FactoryLens FL-B110 validation"
Write-Host "Repository : $RepositoryRoot"
Write-Host "JAVA_HOME  : $javaHome"
Write-Host "clangd     : $resolvedClangd"
$clangdVersionText = Get-NativeVersionText -Executable $resolvedClangd -Argument "--version"
Write-Host (($clangdVersionText -split "\r?\n" | Where-Object { $_ } | Select-Object -First 1))
$javaVersionText = Get-NativeVersionText -Executable (Join-Path $javaHome "bin\java.exe") -Argument "-version"
$javaVersionText -split "\r?\n" | Where-Object { $_ } | ForEach-Object { Write-Host $_ }

if ($EnvironmentOnly) {
    return
}

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
