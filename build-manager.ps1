param(
    [string]$Csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
    [string]$Source = "$PSScriptRoot\src\manager\AccountManager.cs",
    [string]$Output = "$PSScriptRoot\AccountManager.exe"
)
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
$java = Get-ChildItem 'C:\Program Files\Java\*\bin\java.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $java) { $java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source }
if (-not $java) { throw 'Can Java de build launcher.' }
New-Item -ItemType Directory -Force build\launcher | Out-Null
& $java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -d build\launcher tools\ManagerLauncher.java tools\PackageLauncher.java
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed' }
& $java -cp build\launcher PackageLauncher build\launcher\ManagerLauncher.class tools\manager-launcher.jar
if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed' }

if (-not (Test-Path -LiteralPath $Csc)) {
    $found = Get-Command csc.exe -ErrorAction SilentlyContinue
    if ($found) { $Csc = $found.Source }
    else { throw "Khong tim thay csc.exe. Vui long cai .NET Framework 4.5+ hoac chi dinh duong dan." }
}

# The default game artifact is required by every manager entry point.
if (-not (Test-Path -LiteralPath "$PSScriptRoot\dist\KnightOnline_PhucHso_v2.jar")) {
    & "$PSScriptRoot/build.ps1"
}
Write-Output "Dang bien dich $Output ..."
& $Csc /nologo /codepage:65001 /target:winexe /win32icon:"$PSScriptRoot\src\manager/assets/app.ico" /optimize+ /r:System.Security.dll /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Web.Extensions.dll /out:"$Output" "$Source"

if ($LASTEXITCODE -ne 0) { throw "Bien dich AccountManager that bai!" }
Write-Output "Bien dich thanh cong: $Output"
