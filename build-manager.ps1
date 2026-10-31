param(
    [string]$Csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
    [string]$Source = "$PSScriptRoot\src\manager\AccountManager.cs",
    [string]$Output = "$PSScriptRoot\AccountManager.exe"
)
$ErrorActionPreference = "Stop"

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
& $Csc /nologo /codepage:65001 /target:winexe /win32icon:"$PSScriptRoot\src\manager/assets/app.ico" /optimize+ /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Web.Extensions.dll /out:"$Output" "$Source"

if ($LASTEXITCODE -ne 0) { throw "Bien dich AccountManager that bai!" }
Write-Output "Bien dich thanh cong: $Output"
