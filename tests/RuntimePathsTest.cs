using System;
using System.IO;
using PhucHsoManager;
public static class RuntimePathsTest
{
    static void Check(bool ok, string message) { if (!ok) throw new Exception(message); }
    public static void Main()
    {
        string root = Path.Combine(Path.GetFullPath("build"), "runtime-test-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(Path.Combine(root, "runtime", "bin"));
        Directory.CreateDirectory(Path.Combine(root, "dist"));
        Directory.CreateDirectory(Path.Combine(root, "tools"));
        try {
            File.WriteAllText(Path.Combine(root, "runtime", "bin", "java.exe"), "test fixture");
            File.WriteAllText(Path.Combine(root, "tools", "microemulator.jar"), "test fixture");
            string old = Path.Combine(root, "dist", "KnightOnline_PhucHso_v2.jar");
            string current = Path.Combine(root, "dist", "KnightOnline_PhucHso_v2_escort.jar");
            File.WriteAllText(old, "old"); File.SetLastWriteTimeUtc(old, DateTime.UtcNow.AddDays(-1));
            File.WriteAllText(current, "new");
            Check(RuntimePaths.FindJava(root, @"Z:\missing-machine\bin\java.exe") == @"runtime\bin\java.exe", "Repair stale absolute Java path using portable runtime");
            Check(RuntimePaths.FindJar(root, "", true) == @"dist\KnightOnline_PhucHso_v2_escort.jar", "Choose newest modded game");
            Check(RuntimePaths.FindJar(root, old, true) == @"dist\KnightOnline_PhucHso_v2.jar", "Preserve explicit valid choice and make it portable");
            Check(RuntimePaths.FindJar(root, @"Z:\old-machine\dist\KnightOnline_PhucHso_v2_escort.jar", true) == @"dist\KnightOnline_PhucHso_v2_escort.jar", "Rebase moved game");
            Check(RuntimePaths.FindJar(root, @"Z:\old-machine\microemulator.jar", false) == @"tools\microemulator.jar", "Rebase emulator");
            Check(RuntimePaths.Existing(root, "\0") == "", "Invalid paths do not crash detection");
            File.Delete(old); File.Delete(current);
            File.WriteAllText(Path.Combine(root, "KnightOnline_v403.jar"), "original");
            Check(RuntimePaths.FindJar(root, "", true) == "", "Never silently use unpatched client");
            Console.WriteLine("PASS runtime detection: 7 assertions");
        } finally { Directory.Delete(root, true); }
    }
}
