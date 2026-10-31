param([string]$Java = 'C:\Program Files\Java\jre1.8.0_503\bin\java.exe', [string]$OutputJar = 'dist\KnightOnline_PhucHso_v2.jar')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Force build\classes,build\tools,build\tests,dist | Out-Null
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.3 -target 1.1 -nowarn -classpath 'KnightOnline_v403.jar;tools\microemulator.jar' -d build\classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Client compilation failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -classpath tools\asm.jar -d build\tools tools\PatchClient.java
if ($LASTEXITCODE -ne 0) { throw 'Patcher compilation failed' }
& $Java -cp 'build\tools;tools\asm.jar' PatchClient KnightOnline_v403.jar build\classes $OutputJar
if ($LASTEXITCODE -ne 0) { throw 'Patching failed' }
Write-Output "Build complete: $OutputJar"
