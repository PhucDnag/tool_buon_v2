using System;
using System.IO;
using PhucHsoManager;
public class RuntimePathsTest : MarshalByRefObject
{
    public void Run() { Main(); }
    static void Check(bool ok, string message) { if (!ok) throw new Exception(message); }
    [STAThread]
    public static void Main(string[] args = null)
    {
        var proxyAccount = new AccountItem { ProxyEnabled = true, ProxyHost = "127.0.0.1", ProxyPort = 1080, ProxyUsername = "fixture-user" };
        proxyAccount.SetProxyPassword("proxy-fixture-secret");
        var proxySerializer = new System.Web.Script.Serialization.JavaScriptSerializer();
        string proxyJson = proxySerializer.Serialize(proxyAccount);
        Check(!proxyJson.Contains("proxy-fixture-secret"), "Proxy password is protected at rest");
        proxyAccount = proxySerializer.Deserialize<AccountItem>(proxyJson);
        Check(proxyAccount.ReadProxyPassword() == "proxy-fixture-secret", "Proxy settings survive reload");
        using (var proxyDialog = new AccountEditDialog(proxyAccount, false)) {
            var flags = System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance;
            var passwordBox = (System.Windows.Forms.TextBox)typeof(AccountEditDialog).GetField("txtProxyPassword", flags).GetValue(proxyDialog);
            Check(passwordBox.Text == "" && passwordBox.UseSystemPasswordChar, "Saved proxy password never enters edit control");
            typeof(AccountEditDialog).GetMethod("BtnOk_Click", flags).Invoke(proxyDialog, new object[] { null, EventArgs.Empty });
            Check(proxyAccount.ReadProxyPassword() == "proxy-fixture-secret", "Blank proxy edit preserves secret");
        }
        proxyAccount.SetProxyPassword("replacement-proxy-fixture");
        Check(proxyAccount.ReadProxyPassword() == "replacement-proxy-fixture", "Explicit proxy replacement works");
        Check(!new AccountItem().ProxyEnabled, "New accounts default to direct mode");
        Console.WriteLine("PASS per-account proxy settings and credential privacy");
        var account = new AccountItem();
        Check(account.Id != new AccountItem().Id, "New rows have independent profiles");
        var profiles = new[] { new AccountItem { Id = "acc2" }, new AccountItem { Id = "ACC2" },
            new AccountItem { Id = "../shared" }, new AccountItem { Id = "" }, new AccountItem { Id = "acc3" } };
        AccountItem.EnsureUniqueProfiles(profiles);
        Check(profiles[0].Id == "acc2" && profiles[4].Id == "acc3", "Preserve valid existing profile data");
        Check(new System.Collections.Generic.HashSet<string>(Array.ConvertAll(profiles, a => a.Id), StringComparer.OrdinalIgnoreCase).Count == profiles.Length,
            "Repair duplicate and invalid profile IDs");
        string stableId = profiles[1].Id;
        AccountItem.EnsureUniqueProfiles(profiles);
        Check(profiles[1].Id == stableId, "Repaired profile persists across reloads");
        Console.WriteLine("PASS account profile identities: 4 assertions");
        TestRowSelection();
        TestManagerLifecycle();
        using (var dialog = new SettingsDialog(new AppSettings { LaunchDelaySeconds = int.MaxValue })) {
            Check(dialog != null, "Out-of-range saved launch delay cannot crash settings dialog");
        }
        var income = new AccountItem();
        string recoveryControl = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, Guid.NewGuid().ToString("N") + ".cmd");
        var recovery = new AccountItem { ControlFile = recoveryControl, AutoRestart = true };
        try {
            File.WriteAllText(recoveryControl + ".restart", "animal-not-found\n");
            Check(!recovery.ShouldRestartStalled(200000), "Animal-not-found message never restarts a game");
            File.WriteAllText(recoveryControl + ".health", "partial");
            Check(!recovery.ShouldRestartStalled(200000), "Incomplete heartbeat ignored");
            File.WriteAllText(recoveryControl + ".health", "200000:0:1");
            Check(!recovery.ShouldRestartStalled(200000), "Idle character with live tick is healthy");
            Check(!recovery.ShouldRestartStalled(259999) && recovery.ShouldRestartStalled(260000), "Restart only after 60 seconds without game ticks");
            recovery.AutoRestart = false;
            Check(!recovery.ShouldRestartStalled(260000), "Disabled automatic restart also disables stalled-game restart");
            recovery.AutoRestart = true;
            File.WriteAllText(recoveryControl + ".health", "200000:110000:1");
            Check(recovery.ShouldRestartStalled(200000), "Unanswered network traffic for 90 seconds triggers recovery");
            File.WriteAllText(recoveryControl + ".health", "200000:110001:1");
            Check(!recovery.ShouldRestartStalled(200000), "Brief network lag does not restart");
            File.WriteAllText(recoveryControl + ".health", "200000:0:1");
            Check(!recovery.ShouldRestartStalled(200000), "Server response clears pending recovery");
            File.WriteAllText(recoveryControl + ".health", "100000:0:0");
            Check(!recovery.ShouldRestartStalled(200000), "Login and reconnect are excluded");
            File.WriteAllText(recoveryControl + ".health", "100000:0:1");
            recovery.UserStopped = true;
            Check(!recovery.ShouldRestartStalled(200000), "User stop prevents automatic recovery");
            Check(!new AccountItem { ControlFile = recoveryControl + "other" }.ShouldRestartStalled(200000), "Recovery isolated to current session");
        } finally { File.Delete(recoveryControl + ".health"); File.Delete(recoveryControl + ".restart"); }
        Console.WriteLine("PASS stalled-game heartbeat and network timeout recovery");
        Check(income.ReadTradeSnapshot("12000:1"), "New receipt added");
        Check(!income.ReadTradeSnapshot("12000:1") && income.TradeGold == 12000, "Repeated snapshot never doubles income");
        Check(!income.ReadTradeSnapshot("100:0") && !income.ReadTradeSnapshot("bad"), "Stale and partial telemetry ignored");
        Check(income.ReadTradeSnapshot("15000:2") && income.TradeGold == 15000 && income.UnmeasuredSales == 2, "Incremental totals");
        income.SessionGold = income.SessionUnmeasured = 0;
        Check(income.ReadTradeSnapshot("50:0") && income.TradeGold == 15050, "New game session adds to saved totals");
        Check(income.ReadTradeSnapshot("50:0:3:2") && income.SuccessfulTrips == 3 && income.FailedTrips == 2, "Trip counters without gold changes");
        Check(!income.ReadTradeSnapshot("50:0:3:2"), "Repeated trip snapshot is idempotent");
        Check(!income.ReadTradeSnapshot("60:0:3:1") && income.TradeGold == 15050, "Stale trip counter rejects whole snapshot");
        Check(!income.ReadTradeSnapshot("50:0:3:") && !income.ReadTradeSnapshot("50:0:3:-1"), "Partial and negative counters rejected");
        var tripJson = new System.Web.Script.Serialization.JavaScriptSerializer();
        var savedTrips = tripJson.Deserialize<AccountItem>(tripJson.Serialize(income));
        Check(savedTrips.SuccessfulTrips == 3 && savedTrips.FailedTrips == 2 && savedTrips.SessionSuccessfulTrips == 0, "Trip totals persist while session counters reset");
        Check(savedTrips.ReadTradeSnapshot("0:0:1:1") && savedTrips.SuccessfulTrips == 4 && savedTrips.FailedTrips == 3, "New session accumulates saved trip totals");
        var area = new System.Drawing.Rectangle(-1920, 40, 1920, 1664);
        for (int count = 1; count <= 20; count++) {
            var sizes = new System.Drawing.Size[count];
            for (int i = 0; i < count; i++) sizes[i] = new System.Drawing.Size(310, 408);
            var boxes = WindowLayout.Arrange(area, sizes);
            Check(boxes.Length == count, "Every game has a slot");
            for (int i = 0; i < count; i++) {
                Check(area.Contains(boxes[i]), "Layout stays within monitor working area");
                for (int j = 0; j < i; j++) Check(!boxes[i].IntersectsWith(boxes[j]), "Game windows do not overlap");
            }
        }
        Console.WriteLine("PASS gold snapshot deduplication and multi-monitor layouts");
        account.Password = "fixture-secret-\"-\\-đ";
        Check(account.Password == "", "Password getter never reveals saved value");
        Check(account.ReadPassword() == "fixture-secret-\"-\\-đ", "Protected password decrypts for launch");
        var serializer = new System.Web.Script.Serialization.JavaScriptSerializer();
        string json = serializer.Serialize(account);
        Check(!json.Contains("fixture-secret"), "Saved JSON contains no plaintext password");
        account = serializer.Deserialize<AccountItem>(json);
        Check(account.ReadPassword() == "fixture-secret-\"-\\-đ", "Protected password survives reload");
        var legacy = serializer.Deserialize<AccountItem>("{\"Password\":\"legacy-fixture\"}");
        Check(legacy.ReadPassword() == "legacy-fixture" && !serializer.Serialize(legacy).Contains("legacy-fixture"), "Legacy passwords migrate without plaintext output");
        using (var dialog = new AccountEditDialog(account, false)) {
            var field = typeof(AccountEditDialog).GetField("txtPassword", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            var box = (System.Windows.Forms.TextBox)field.GetValue(dialog);
            Check(box.Text == "" && box.UseSystemPasswordChar, "Edit dialog never loads saved password");
            var save = typeof(AccountEditDialog).GetMethod("BtnOk_Click", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            save.Invoke(dialog, new object[] { null, EventArgs.Empty });
            Check(account.ReadPassword() == "fixture-secret-\"-\\-đ", "Blank edit preserves existing password");
        }
        account.Password = "replacement-fixture";
        Check(account.ReadPassword() == "replacement-fixture", "Explicit replacement updates password");
        Console.WriteLine("PASS protected credentials and edit dialog: 8 assertions");
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

    static void TestManagerLifecycle()
    {
        string file = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "accounts.json");
        byte[] backup = File.Exists(file) ? File.ReadAllBytes(file) : null;
        var flags = System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic;
        try {
            File.WriteAllText(file, "[]");
            using (var form = new MainForm()) {
                var rows = (System.Collections.Generic.List<AccountItem>)typeof(MainForm).GetField("accounts", flags).GetValue(form);
                Check(rows.Count == 0, "Deleted final account stays deleted after reload");
                var pending = (System.Collections.Generic.Queue<AccountItem>)typeof(MainForm).GetField("pendingLaunches", flags).GetValue(form);
                var stopped = new AccountItem { UserStopped = true };
                rows.Add(stopped); pending.Enqueue(stopped);
                typeof(MainForm).GetMethod("LaunchNextPending", flags).Invoke(form, null);
                Check(pending.Count == 0 && stopped.Process == null, "Stopped queued account is skipped without launch");
                var waiting = new AccountItem { AutoRestart = true, NextRestartAt = DateTime.Now };
                rows.Add(waiting); pending.Enqueue(waiting);
                typeof(MainForm).GetMethod("StopAll", flags).Invoke(form, null);
                Check(pending.Count == 0 && waiting.UserStopped && waiting.NextRestartAt == DateTime.MinValue,
                    "Stop all cancels launch queue and crash restart countdown even for stopped games");
            }
            string damaged = "[{broken account data";
            File.WriteAllText(file, damaged);
            using (var form = new MainForm()) {
                typeof(MainForm).GetMethod("SaveAccounts", flags).Invoke(form, null);
                typeof(MainForm).GetMethod("AddAccount", flags).Invoke(form, null);
                Check(File.ReadAllText(file) == damaged, "Malformed account file is never replaced with defaults or new accounts");
            }
            AtomicFile.Write(file, "original");
            using (var locked = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read)) {
                bool failed = false;
                try { AtomicFile.Write(file, "replacement"); } catch (IOException) { failed = true; }
                Check(failed && File.ReadAllText(file) == "original", "Failed atomic replacement preserves previous file");
            }
            AtomicFile.Write(file, "replacement");
            Check(File.ReadAllText(file) == "replacement", "Atomic replacement succeeds once unlocked");
            Check(Directory.GetFiles(Path.GetDirectoryName(file), "accounts.json.*.tmp").Length == 0, "Atomic writes clean up temporary files");
            Console.WriteLine("PASS empty/corrupt storage, atomic saves, stopped queues and watchdog cancellation: 7 assertions");
        } finally {
            if (backup == null) File.Delete(file); else File.WriteAllBytes(file, backup);
        }
    }

    static void TestRowSelection()
    {
        string file = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "accounts.json");
        byte[] backup = File.Exists(file) ? File.ReadAllBytes(file) : null;
        try {
            File.WriteAllText(file, "[{\"Id\":\"fixture_a\",\"Username\":\"account-a\"},{\"Id\":\"fixture_b\",\"Username\":\"account-b\"}]");
            using (var form = new MainForm()) {
                var flags = System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic;
                var grid = (System.Windows.Forms.DataGridView)typeof(MainForm).GetField("dgvAccounts", flags).GetValue(form);
                var label = (System.Windows.Forms.ToolStripStatusLabel)typeof(MainForm).GetField("lblStatus", flags).GetValue(form);
                var rows = (System.Collections.Generic.List<AccountItem>)typeof(MainForm).GetField("accounts", flags).GetValue(form);
                rows[0].Process = System.Diagnostics.Process.GetCurrentProcess();
                // Reverse the bound order to catch any accidental row-index lookup.
                grid.DataSource = new System.ComponentModel.BindingList<AccountItem>(new System.Collections.Generic.List<AccountItem> { rows[1], rows[0] });
                form.CreateControl(); grid.CreateControl();
                var selected = typeof(MainForm).GetMethod("GetSelectedAccount", flags);
                grid.CurrentCell = grid.Rows[1].Cells[3];
                Check(object.ReferenceEquals(selected.Invoke(form, null), rows[0]), "Actions target bound account, independent of list order");
                Check(label.Text.Contains("account-a") && label.Text.Contains("PID: " + rows[0].Process.Id), "Selection shows running account and its PID");
                grid.CurrentCell = grid.Rows[0].Cells[3];
                Check(object.ReferenceEquals(selected.Invoke(form, null), rows[1]), "Selection switches to second account");
                Check(label.Text.Contains("account-b") && !label.Text.Contains("PID:"), "Stopped account never displays previous account PID");
                grid.CurrentCell = grid.Rows[0].Cells[8];
                grid.BeginEdit(false);
                ((System.Windows.Forms.IDataGridViewEditingCell)grid.CurrentCell).EditingCellFormattedValue = false;
                grid.NotifyCurrentCellDirty(true);
                Check(!rows[1].AutoTradeOn, "Auto checkbox commits without leaving row");
                string mailbox = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "auto-checkbox-test.cmd");
                rows[1].Process = System.Diagnostics.Process.GetCurrentProcess(); rows[1].ControlFile = mailbox;
                try {
                    grid.BeginEdit(false);
                    ((System.Windows.Forms.IDataGridViewEditingCell)grid.CurrentCell).EditingCellFormattedValue = true;
                    grid.NotifyCurrentCellDirty(true);
                    Check(rows[1].AutoTradeOn && File.ReadAllText(mailbox).StartsWith("1:"), "Checking auto on running row sends immediate start command");
                } finally { rows[1].Process = null; rows[1].ControlFile = null; if (File.Exists(mailbox)) File.Delete(mailbox); }
                using (var windowA = new System.Windows.Forms.Form())
                using (var windowB = new System.Windows.Forms.Form()) {
                    windowA.Text = "Fixture game A"; windowB.Text = "Fixture game B";
                    windowA.Show(); windowB.Show();
                    rows[1].Process = System.Diagnostics.Process.GetCurrentProcess();
                    rows[0].WindowHandle = windowA.Handle; rows[1].WindowHandle = windowB.Handle;
                    var visibility = typeof(MainForm).GetMethod("SetAllVisible", flags);
                    visibility.Invoke(form, new object[] { false });
                    System.Windows.Forms.Application.DoEvents();
                    Check(!windowA.Visible && !windowB.Visible && rows[0].Background && rows[1].Background, "Hide all keeps both processes alive in background mode");
                    var native = typeof(MainForm).Assembly.GetType("PhucHsoManager.Win32");
                    using (var toolkit = new System.Windows.Forms.Form { Text = "theAwtToolkitWindow" }) {
                        var score = native.GetMethod("MainWindowScore");
                        Check((int)score.Invoke(null, new object[] { toolkit.Handle, rows[0].Process.Id }) == -1,
                            "AWT toolkit window is rejected even if its PID matches the game");
                    }
                    var discover = native.GetMethod("FindMainWindow", System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static);
                    IntPtr hidden = (IntPtr)discover.Invoke(null, new object[] { System.Diagnostics.Process.GetCurrentProcess().Id });
                    Check(hidden == windowA.Handle || hidden == windowB.Handle, "Hidden game frames remain discoverable without cached handles");
                    visibility.Invoke(form, new object[] { true });
                    System.Windows.Forms.Application.DoEvents();
                    Check(windowA.Visible && windowB.Visible && !rows[0].Background && !rows[1].Background, "Restore finds previously hidden windows");
                    var originalSizes = new[] { windowB.Size, windowA.Size };
                    typeof(MainForm).GetMethod("TileWindows", flags).Invoke(form, null);
                    System.Windows.Forms.Application.DoEvents();
                    DateTime tileStart = DateTime.UtcNow;
                    DateTime tileDeadline = tileStart.AddSeconds(6);
                    bool repacked = false;
                    while (!label.Text.StartsWith("Đã xếp") && DateTime.UtcNow < tileDeadline) {
                        System.Windows.Forms.Application.DoEvents(); System.Threading.Thread.Sleep(25);
                        if (!repacked && (DateTime.UtcNow - tileStart).TotalMilliseconds >= 550) {
                            windowB.Location = new System.Drawing.Point(windowB.Left + 80, windowB.Top + 120);
                            repacked = true;
                        }
                    }
                    Check(repacked, "Simulate emulator resetting position after initial layout");
                    Check(label.Text.Contains("2/2"), "Tile verifies both window rectangles");
                    var expected = WindowLayout.Arrange(System.Windows.Forms.Screen.FromControl(form).WorkingArea, originalSizes);
                    Check(windowB.Bounds == expected[0] && windowA.Bounds == expected[1],
                        "Actual windows align left to right in displayed row order, not backing list order");
                    rows[0].WindowHandle = rows[1].WindowHandle = IntPtr.Zero;
                    rows[1].Process = null;
                }
                form.Width = 1100; form.Height = 700;
                form.Show(); form.PerformLayout(); System.Windows.Forms.Application.DoEvents();
                using (var bitmap = new System.Drawing.Bitmap(form.Width, form.Height)) {
                    form.DrawToBitmap(bitmap, new System.Drawing.Rectangle(0, 0, form.Width, form.Height));
                    bitmap.Save(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "manager-preview.png"));
                }
                ((System.Windows.Forms.Timer)typeof(MainForm).GetField("updateTimer", flags).GetValue(form)).Stop();
                rows[0].Process = null;
                var fixtureInfo = new System.Diagnostics.ProcessStartInfo {
                    FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), @"WindowsPowerShell\v1.0\powershell.exe"),
                    Arguments = "-NoProfile -NonInteractive -Command \"Start-Sleep -Seconds 30\"",
                    UseShellExecute = false, CreateNoWindow = true, WindowStyle = System.Diagnostics.ProcessWindowStyle.Hidden
                };
                using (var fixture = System.Diagnostics.Process.Start(fixtureInfo))
                using (var observer = System.Diagnostics.Process.GetProcessById(fixture.Id)) {
                    try {
                        rows[1].Process = fixture;
                        var stop = typeof(MainForm).GetMethod("StopSelected", flags);
                        grid.CurrentCell = grid.Rows[1].Cells[3]; // stopped account-a
                        stop.Invoke(form, null);
                        Check(!observer.HasExited && label.Text.Contains("đã dừng"), "Stopping a stopped row explains selection and leaves other game alive");
                        grid.CurrentCell = grid.Rows[0].Cells[3]; // running account-b
                        stop.Invoke(form, null);
                        Check(observer.WaitForExit(3000) && rows[1].Process == null && rows[1].UserStopped,
                            "Stop selected actually terminates its own fixture process and disables restart");
                    } finally {
                        if (!observer.HasExited) { observer.Kill(); observer.WaitForExit(3000); }
                        rows[1].Process = null;
                    }
                }
            }
            Console.WriteLine("PASS bound row selection and PID display: 4 assertions");
        } finally {
            if (backup == null) File.Delete(file); else File.WriteAllBytes(file, backup);
        }
    }
}
