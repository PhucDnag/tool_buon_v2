param([string]$Manager = "$PSScriptRoot\AccountManager_portable.exe")
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Force build | Out-Null
& 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe' /nologo /out:build\RuntimePathsTest.exe /r:"$Manager" tests\RuntimePathsTest.cs
if ($LASTEXITCODE -ne 0) { throw 'Manager test compilation failed' }
Copy-Item -LiteralPath $Manager -Destination (Join-Path "$PSScriptRoot\build" (Split-Path -Leaf $Manager))
& .\build\RuntimePathsTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Runtime detection tests failed' }
