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
        var income = new AccountItem();
        Check(income.ReadTradeSnapshot("12000:1"), "New receipt added");
        Check(!income.ReadTradeSnapshot("12000:1") && income.TradeGold == 12000, "Repeated snapshot never doubles income");
        Check(!income.ReadTradeSnapshot("100:0") && !income.ReadTradeSnapshot("bad"), "Stale and partial telemetry ignored");
        Check(income.ReadTradeSnapshot("15000:2") && income.TradeGold == 15000 && income.UnmeasuredSales == 2, "Incremental totals");
        income.SessionGold = income.SessionUnmeasured = 0;
        Check(income.ReadTradeSnapshot("50:0") && income.TradeGold == 15050, "New game session adds to saved totals");
        var area = new System.Drawing.Rectangle(-1920, 40, 1920, 1040);
        for (int count = 1; count <= 20; count++) {
            var boxes = WindowLayout.Arrange(area, count);
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
                    windowA.Show(); windowB.Show();
                    rows[1].Process = System.Diagnostics.Process.GetCurrentProcess();
                    rows[0].WindowHandle = windowA.Handle; rows[1].WindowHandle = windowB.Handle;
                    var visibility = typeof(MainForm).GetMethod("SetAllVisible", flags);
                    visibility.Invoke(form, new object[] { false });
                    System.Windows.Forms.Application.DoEvents();
                    Check(!windowA.Visible && !windowB.Visible && rows[0].Background && rows[1].Background, "Hide all keeps both processes alive in background mode");
                    visibility.Invoke(form, new object[] { true });
                    System.Windows.Forms.Application.DoEvents();
                    Check(windowA.Visible && windowB.Visible && !rows[0].Background && !rows[1].Background, "Restore finds previously hidden windows");
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
            }
            Console.WriteLine("PASS bound row selection and PID display: 4 assertions");
        } finally {
            if (backup == null) File.Delete(file); else File.WriteAllBytes(file, backup);
        }
    }
}
