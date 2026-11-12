param([string]$Java = 'C:\Program Files\Java\jre1.8.0_503\bin\java.exe', [string]$Jar = 'dist\KnightOnline_PhucHso_v2.jar')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp "$Jar;tools\microemulator.jar" -d build\tests tests\ProxyConnectionTest.java
if ($LASTEXITCODE -ne 0) { throw 'Proxy test compilation failed' }
& $Java -Xverify:all -cp "build\tests;$Jar;tools\microemulator.jar" ProxyConnectionTest $Jar
if ($LASTEXITCODE -ne 0) { throw 'Proxy transport regression failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp $Jar -d build\tests tests\RuntimeMaintenanceTest.java
if ($LASTEXITCODE -ne 0) { throw 'Maintenance test compilation failed' }
& $Java -Xverify:all -cp "build\tests;$Jar" RuntimeMaintenanceTest
if ($LASTEXITCODE -ne 0) { throw 'Maintenance regression failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp $Jar -d build\tests tests\StartupLoginTest.java
if ($LASTEXITCODE -ne 0) { throw 'Startup login test compilation failed' }
& $Java -Xverify:all -cp "build\tests;$Jar" StartupLoginTest
if ($LASTEXITCODE -ne 0) { throw 'Startup login regression failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp build\classes -d build\tests tests\TradeTests.java tests\VerifyPatched.java tests\ReconnectPolicyTest.java tests\ManagerControlTest.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& $Java -Xverify:all -cp 'build\tests;build\classes' ManagerControlTest
if ($LASTEXITCODE -ne 0) { throw 'Manager control regression failed' }
& $Java -Xverify:all -cp 'build\tests;build\classes' TradeTests
if ($LASTEXITCODE -ne 0) { throw 'Behavior tests failed' }
& $Java -Xverify:all -cp 'build\tests;build\classes' ReconnectPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Reconnect regression failed' }
New-Item -ItemType Directory -Force build\simulation | Out-Null
$fakeSources = @(Get-ChildItem tests\fakes -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$logicSources = @('LowDetail','LowDetailRenderer','OwnedAnimal','TradeEarnings','TradeConfig','TradeStock','TradeSignals','TradeEngine','MapGraph','KnownRoutes','WorldTopology','GridPath','Navigator','TradeRecovery','TradeTrace','LiveTrade') | ForEach-Object { "src\autotrade\$_.java" }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -d build\simulation @fakeSources @logicSources tests\AutoTradeLifecycleTest.java
if ($LASTEXITCODE -ne 0) { throw 'Lifecycle simulation compilation failed' }
& $Java -Xverify:all -cp build\simulation AutoTradeLifecycleTest
if ($LASTEXITCODE -ne 0) { throw 'Automatic trade lifecycle regression failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -d build\simulation @fakeSources @logicSources tests\ClientFlowTests.java tests\MovementVerificationTest.java tests\MapLoopRegressionTest.java tests\TradeConfirmationTest.java tests\DylanPurchaseRegressionTest.java tests\OwnedAnimalTest.java tests\LowDetailTest.java
if ($LASTEXITCODE -ne 0) { throw 'Simulation compilation failed' }
& $Java -Xverify:all -cp build\simulation OwnedAnimalTest
if ($LASTEXITCODE -ne 0) { throw 'Animal selection regression failed' }
& $Java -Xverify:all -cp build\simulation LowDetailTest
if ($LASTEXITCODE -ne 0) { throw 'Low-detail regression failed' }
& $Java -Xverify:all -cp build\simulation ClientFlowTests
if ($LASTEXITCODE -ne 0) { throw 'Live adapter simulation failed' }
& $Java -Xverify:all -cp build\simulation MovementVerificationTest
if ($LASTEXITCODE -ne 0) { throw 'Movement verification failed' }
& $Java -Xverify:all -cp build\simulation MapLoopRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Map-loop regression failed' }
& $Java -Xverify:all -cp build\simulation TradeConfirmationTest
if ($LASTEXITCODE -ne 0) { throw 'Trade confirmation/purchase regression failed' }
& $Java -Xverify:all -cp build\simulation DylanPurchaseRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Dylan purchase regression failed' }
& $Java -Xverify:all -cp "build\tests;$Jar;tools\microemulator.jar" VerifyPatched
if ($LASTEXITCODE -ne 0) { throw 'Bytecode verification failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp 'build\classes;tools\microemulator.jar' -d build\tests tools\ManagerLauncher.java tests\LaunchConfigTest.java
if ($LASTEXITCODE -ne 0) { throw 'Launch config test compilation failed' }
& $Java -cp "build\tests;$Jar;tools\microemulator.jar" LaunchConfigTest
if ($LASTEXITCODE -ne 0) { throw 'Launch config regression failed' }
& $Java -jar tools\ecj.jar -encoding UTF-8 -source 1.7 -target 1.7 -nowarn -cp build\classes -d build\tests tests\ToolInputTest.java
if ($LASTEXITCODE -ne 0) { throw 'Tool input test compilation failed' }
& $Java -cp 'build\tests;build\classes' ToolInputTest
if ($LASTEXITCODE -ne 0) { throw 'Tool input isolation regression failed' }
