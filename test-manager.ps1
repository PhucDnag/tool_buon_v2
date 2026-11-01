param([string]$Manager = "$PSScriptRoot\AccountManager.exe")
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Force build | Out-Null
& 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe' /nologo /out:build\RuntimePathsTest.exe /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Web.Extensions.dll /r:"$Manager" tests\RuntimePathsTest.cs
if ($LASTEXITCODE -ne 0) { throw 'Manager test compilation failed' }
$testAssembly = Join-Path "$PSScriptRoot\build" (Split-Path -Leaf $Manager)
if ([IO.Path]::GetFullPath($Manager) -ne [IO.Path]::GetFullPath($testAssembly)) { Copy-Item -LiteralPath $Manager -Destination $testAssembly }
& .\build\RuntimePathsTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Runtime detection tests failed' }
$java = Get-ChildItem 'C:\Program Files\Java\*\bin\java.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $java) { $java = (Get-Command java.exe).Source }
& $java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp 'tools\microemulator.jar;tools\manager-launcher.jar' -d build\launcher tests\ManagerProfileTest.java
if ($LASTEXITCODE -ne 0) { throw 'Profile test compilation failed' }
$profileRoot = Join-Path "$PSScriptRoot\build" ('profile-test-' + [Guid]::NewGuid().ToString('N'))
foreach ($mode in @('write', 'read')) {
    foreach ($profile in @('account_a', 'account_b')) {
        & $java -cp 'build\launcher;tools\manager-launcher.jar;tools\microemulator.jar' ManagerProfileTest $profileRoot $profile $mode
        if ($LASTEXITCODE -ne 0) { throw 'Emulator profile isolation failed' }
    }
}
