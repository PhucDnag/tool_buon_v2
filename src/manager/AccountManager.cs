using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace PhucHsoManager
{
    public static class ProxyProbe
    {
        private static int ReadByte(Stream stream) {
            int value = stream.ReadByte();
            if (value < 0) throw new IOException("Proxy đóng kết nối.");
            return value;
        }
        public static void Check(string host, int port, string username, string password) {
            byte[] user = Encoding.UTF8.GetBytes(username ?? ""), pass = Encoding.UTF8.GetBytes(password ?? "");
            if (user.Length > 255 || pass.Length > 255) throw new IOException("Thông tin xác thực quá dài.");
            using (var client = new System.Net.Sockets.TcpClient()) {
                IAsyncResult pending = client.BeginConnect(host, port, null, null);
                using (WaitHandle wait = pending.AsyncWaitHandle) {
                    if (!wait.WaitOne(8000)) throw new IOException("Hết thời gian chờ proxy.");
                    client.EndConnect(pending);
                }
                client.ReceiveTimeout = client.SendTimeout = 8000;
                using (Stream stream = client.GetStream()) {
                    int method = user.Length == 0 ? 0 : 2;
                    byte[] greeting = new byte[] { 5, 1, (byte)method };
                    stream.Write(greeting, 0, greeting.Length);
                    if (ReadByte(stream) != 5 || ReadByte(stream) != method) throw new IOException("Proxy từ chối phương thức SOCKS5.");
                    if (method == 2) {
                        stream.WriteByte(1); stream.WriteByte((byte)user.Length); stream.Write(user, 0, user.Length);
                        stream.WriteByte((byte)pass.Length); stream.Write(pass, 0, pass.Length);
                        if (ReadByte(stream) != 1 || ReadByte(stream) != 0) throw new IOException("Sai username/password proxy.");
                    }
                }
            }
        }
    }
    public static class AtomicFile
    {
        public static void Write(string path, string text)
        {
            string temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
            try {
                File.WriteAllText(temp, text, Encoding.UTF8);
                if (File.Exists(path)) File.Replace(temp, path, null);
                else File.Move(temp, path);
            } finally { if (File.Exists(temp)) File.Delete(temp); }
        }
    }
    public static class RuntimePaths
    {
        public static string Existing(string root, string path)
        {
            try {
                if (string.IsNullOrWhiteSpace(path)) return "";
                path = Environment.ExpandEnvironmentVariables(path.Trim().Trim('"'));
                string full = Path.GetFullPath(Path.IsPathRooted(path) ? path : Path.Combine(root, path));
                return File.Exists(full) ? full : "";
            } catch { return ""; }
        }
        private static string Portable(string root, string full)
        {
            string prefix = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            return full.StartsWith(prefix, StringComparison.OrdinalIgnoreCase) ? full.Substring(prefix.Length) : full;
        }
        public static string FindJar(string root, string configured, bool game)
        {
            string found = Existing(root, configured);
            if (found.Length > 0) return Portable(root, found);
            // Rebase an absolute path copied from another machine before falling back.
            string name = "";
            try { name = Path.GetFileName(configured ?? ""); } catch { }
            string folder = game ? "dist" : "tools";
            if (name.Length > 0) {
                found = Existing(root, Path.Combine(folder, name));
                if (found.Length > 0) return Portable(root, found);
            }
            if (!game) {
                found = Existing(root, @"tools\microemulator.jar");
                if (found.Length == 0) found = Existing(root, "microemulator.jar");
            } else {
                // Only modded client artifacts, never the unpatched original game.
                DateTime latest = DateTime.MinValue;
                foreach (string dir in new string[] { Path.Combine(root, "dist"), root }) {
                    try {
                        foreach (string file in Directory.GetFiles(dir, "KnightOnline_PhucHso*.jar")) {
                            DateTime time = File.GetLastWriteTimeUtc(file);
                            if (time > latest) { found = file; latest = time; }
                        }
                    } catch (IOException) { } catch (UnauthorizedAccessException) { }
                }
            }
            return found.Length > 0 ? Portable(root, found) : "";
        }
        public static string FindJava(string root, string configured)
        {
            string found = Existing(root, configured);
            if (found.Length > 0) return Portable(root, found);
            var candidates = new List<string>();
            candidates.Add(@"runtime\bin\java.exe");
            candidates.Add(@"jre\bin\java.exe");
            candidates.Add(@"java\bin\java.exe");
            foreach (string variable in new string[] { "JAVA_HOME", "JRE_HOME" }) {
                string home = Environment.GetEnvironmentVariable(variable);
                if (!string.IsNullOrWhiteSpace(home)) candidates.Add(Path.Combine(home.Trim('"'), @"bin\java.exe"));
            }
            foreach (string folder in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator)) {
                if (string.IsNullOrWhiteSpace(folder)) continue;
                try { candidates.Add(Path.Combine(folder.Trim().Trim('"'), "java.exe")); } catch { }
            }
            foreach (string baseDir in new string[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                    Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), Environment.GetEnvironmentVariable("ProgramW6432") }) {
                if (string.IsNullOrEmpty(baseDir)) continue;
                foreach (string vendor in new string[] { "Java", "Eclipse Adoptium", "AdoptOpenJDK", "Microsoft", "Amazon Corretto", "Zulu" }) {
                    try {
                        string[] homes = Directory.GetDirectories(Path.Combine(baseDir, vendor));
                        Array.Sort(homes, StringComparer.OrdinalIgnoreCase);
                        Array.Reverse(homes);
                        foreach (string home in homes) candidates.Add(Path.Combine(home, @"bin\java.exe"));
                    } catch (IOException) { } catch (UnauthorizedAccessException) { }
                }
            }
            foreach (string candidate in candidates) {
                found = Existing(root, candidate);
                if (found.Length > 0) return Portable(root, found);
            }
            return "";
        }
    }
    public class AppSettings
    {
        public string JavaPath { get; set; }
        public string EmulatorJar { get; set; }
        public string GameJar { get; set; }
        public int LaunchDelaySeconds { get; set; }
        public bool RenameWindowTitle { get; set; }
        public int MemoryCleanupMinutes { get; set; }

        public AppSettings()
        {
            JavaPath = "";
            EmulatorJar = @"tools\microemulator.jar";
            GameJar = "";
            LaunchDelaySeconds = 2;
            RenameWindowTitle = true;
            MemoryCleanupMinutes = 10;
        }

        public static string SettingsFile
        {
            get { return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "manager_config.json"); }
        }

        public static AppSettings Load()
        {
            try
            {
                if (File.Exists(SettingsFile))
                {
                    string json = File.ReadAllText(SettingsFile, Encoding.UTF8);
                    JavaScriptSerializer jss = new JavaScriptSerializer();
                    AppSettings s = jss.Deserialize<AppSettings>(json);
                    if (s != null) { s.DetectPaths(); return s; }
                }
            }
            catch { }
            AppSettings defaults = new AppSettings();
            defaults.DetectPaths();
            return defaults;
        }

        public void DetectPaths()
        {
            string root = AppDomain.CurrentDomain.BaseDirectory;
            JavaPath = RuntimePaths.FindJava(root, JavaPath);
            EmulatorJar = RuntimePaths.FindJar(root, EmulatorJar, false);
            string canonical = RuntimePaths.Existing(root, @"dist\KnightOnline_PhucHso_v2.jar");
            GameJar = canonical.Length > 0 ? @"dist\KnightOnline_PhucHso_v2.jar" : RuntimePaths.FindJar(root, GameJar, true);
        }

        public void Save()
        {
            JavaScriptSerializer jss = new JavaScriptSerializer();
            string json = jss.Serialize(this);
            AtomicFile.Write(SettingsFile, json);
        }
    }

    public class AccountItem
    {
        public static string NewProfileId() { return "acc_" + Guid.NewGuid().ToString("N"); }

        public static void EnsureUniqueProfiles(IEnumerable<AccountItem> items)
        {
            var used = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (AccountItem item in items)
            {
                if (string.IsNullOrEmpty(item.Id) ||
                    !System.Text.RegularExpressions.Regex.IsMatch(item.Id, @"\A[A-Za-z0-9_-]+\z") ||
                    !used.Add(item.Id))
                {
                    do { item.Id = NewProfileId(); } while (!used.Add(item.Id));
                }
            }
        }

        public bool Selected { get; set; }
        public string Id { get; set; }
        public string DisplayName { get; set; }
        public string Username { get; set; }
        // Write-only compatibility with legacy accounts.json; never serialize plaintext.
        public string Password {
            get { return ""; }
            set { if (!string.IsNullOrEmpty(value)) PasswordProtected = Convert.ToBase64String(
                System.Security.Cryptography.ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null,
                System.Security.Cryptography.DataProtectionScope.CurrentUser)); }
        }
        public string PasswordProtected { get; set; }
        public string ReadPassword() {
            if (string.IsNullOrEmpty(PasswordProtected)) return "";
            try { return Encoding.UTF8.GetString(System.Security.Cryptography.ProtectedData.Unprotect(
                Convert.FromBase64String(PasswordProtected), null, System.Security.Cryptography.DataProtectionScope.CurrentUser)); }
            catch { throw new InvalidOperationException("Không giải mã được mật khẩu trên máy/tài khoản Windows này. Hãy nhập mật khẩu mới trong Sửa tài khoản."); }
        }
        public bool ProxyEnabled { get; set; }
        public string ProxyHost { get; set; }
        public int ProxyPort { get; set; }
        public string ProxyUsername { get; set; }
        public string ProxyPasswordProtected { get; set; }
        [ScriptIgnore] public string ProxyStatus { get; set; }
        public void SetProxyPassword(string value) {
            if (!string.IsNullOrEmpty(value)) ProxyPasswordProtected = Convert.ToBase64String(
                System.Security.Cryptography.ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null,
                System.Security.Cryptography.DataProtectionScope.CurrentUser));
        }
        public string ReadProxyPassword() {
            if (string.IsNullOrEmpty(ProxyPasswordProtected)) return "";
            try { return Encoding.UTF8.GetString(System.Security.Cryptography.ProtectedData.Unprotect(
                Convert.FromBase64String(ProxyPasswordProtected), null, System.Security.Cryptography.DataProtectionScope.CurrentUser)); }
            catch { throw new InvalidOperationException("Không giải mã được mật khẩu proxy. Hãy nhập lại."); }
        }
        public string Server { get; set; }
        public int CharacterSlot { get; set; }
        public int Market { get; set; }
        public int Gem { get; set; }
        public int Animal { get; set; }
        public int HpIndex { get; set; }
        public bool AutoTradeOn { get; set; }
        public bool AutoReconnect { get; set; }
        public bool AutoMount { get; set; }
        public bool AutoRestart { get; set; }
        public bool RotateMarkets { get; set; }
        public bool Teleport { get; set; }
        public bool ShowHud { get; set; }
        public bool ShowGuide { get; set; }
        public bool FakeName { get; set; }
        public bool SilentBuy { get; set; }
        public bool LowDetail { get; set; }
        public bool AutoAttack { get; set; }
        public bool AutoHeal { get; set; }
        public bool AutoLoot { get; set; }
        public int HealHpPercent { get; set; }
        public int HealMpPercent { get; set; }
        public string MountName { get; set; }
        public string Notes { get; set; }
        public long TradeGold { get; set; }
        public long UnmeasuredSales { get; set; }
        [ScriptIgnore] public long SessionGold { get; set; }
        [ScriptIgnore] public long SessionUnmeasured { get; set; }
        [ScriptIgnore] public bool Background { get; set; }
        [ScriptIgnore] public List<IntPtr> HiddenWindows = new List<IntPtr>();
        public bool ReadTradeSnapshot(string text)
        {
            string[] parts = text.Trim().Split(':');
            long gold, unknown;
            if (parts.Length != 2 || !long.TryParse(parts[0], out gold) || !long.TryParse(parts[1], out unknown)
                || gold < SessionGold || unknown < SessionUnmeasured || gold < 0 || unknown < 0) return false;
            long delta = gold - SessionGold, missing = unknown - SessionUnmeasured;
            if (delta > long.MaxValue - TradeGold || missing > long.MaxValue - UnmeasuredSales) return false;
            TradeGold += delta; UnmeasuredSales += missing;
            SessionGold = gold; SessionUnmeasured = unknown;
            return delta != 0 || missing != 0;
        }

        [ScriptIgnore]
        public Process Process { get; set; }

        [ScriptIgnore]
        public string ControlFile { get; set; }

        [ScriptIgnore]
        public DateTime StartTime { get; set; }

        [ScriptIgnore]
        public IntPtr WindowHandle { get; set; }

        [ScriptIgnore]
        public bool UserStopped { get; set; }

        [ScriptIgnore]
        public DateTime NextRestartAt { get; set; }

        public AccountItem()
        {
            Selected = true;
            Id = NewProfileId();
            DisplayName = "Tài khoản 1";
            Username = "";
            Password = "";
            Server = "Chiến Thần";
            CharacterSlot = 0;
            Market = 0;
            Gem = 0;
            Animal = 2;
            HpIndex = 1;
            AutoTradeOn = true;
            AutoReconnect = true;
            AutoMount = true;
            AutoRestart = false;
            RotateMarkets = false;
            Teleport = false;
            ShowHud = true;
            ShowGuide = true;
            FakeName = true;
            SilentBuy = true;
            LowDetail = true;
            AutoAttack = false;
            AutoHeal = true;
            AutoLoot = true;
            HealHpPercent = 50;
            HealMpPercent = 50;
            MountName = "";
            Notes = "Cổng Địa Ngục - Bò vàng 24v";
        }

        [ScriptIgnore]
        public bool IsRunning
        {
            get
            {
                try
                {
                    return Process != null && !Process.HasExited;
                }
                catch
                {
                    return false;
                }
            }
        }
    }

    internal static class Win32
    {
        public const int SW_RESTORE = 9;
        public const int SW_SHOW = 5;

        [DllImport("user32.dll")]
        public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
        public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool IsWindowVisible(IntPtr hWnd);
        [DllImport("user32.dll")]
        public static extern bool IsWindow(IntPtr hWnd);

        [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

        [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        public static extern bool SetWindowText(IntPtr hWnd, string lpString);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int nWidth, int nHeight, bool bRepaint);

        [DllImport("user32.dll")]
        public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
        [DllImport("user32.dll")]
        public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint message, IntPtr wParam, string text, uint flags, uint timeout, out IntPtr result);
        public static void RenameWindow(IntPtr handle, string title) {
            IntPtr result;
            SendMessageTimeout(handle, 0x000C, IntPtr.Zero, title, 0x0002, 100, out result);
        }
        public static List<IntPtr> VisibleWindows(int pid) {
            var result = new List<IntPtr>();
            EnumWindows(delegate(IntPtr h, IntPtr unused) {
                uint owner; GetWindowThreadProcessId(h, out owner);
                if (owner == pid && IsWindowVisible(h)) result.Add(h);
                return true;
            }, IntPtr.Zero);
            return result;
        }

        [DllImport("user32.dll")]
        public static extern bool SetForegroundWindow(IntPtr hWnd);

        public static IntPtr FindMainWindow(int pid)
        {
            IntPtr found = IntPtr.Zero;
            EnumWindows(delegate (IntPtr hWnd, IntPtr lParam)
            {
                uint p;
                GetWindowThreadProcessId(hWnd, out p);
                if (p == pid && IsWindowVisible(hWnd))
                {
                    StringBuilder sb = new StringBuilder(256);
                    GetWindowText(hWnd, sb, 256);
                    string t = sb.ToString();
                    if (t.Length > 0 && !t.Contains("Default IME") && !t.Contains("MSCTFIME"))
                    {
                        found = hWnd;
                        return false;
                    }
                }
                return true;
            }, IntPtr.Zero);
            return found;
        }
    }

    public class AccountEditDialog : Form
    {
        private TextBox txtUsername, txtPassword;
        private TextBox txtProxyHost, txtProxyUser, txtProxyPassword;
        private NumericUpDown numProxyPort;
        private CheckBox chkProxy;
        private Label lblProxyTest;
        private ComboBox cmbServer, cmbSlot, cmbMarket, cmbGem, cmbAnimal, cmbHp;
        private CheckBox chkAutoTrade, chkAutoRecon, chkAutoMount, chkAutoRestart;
        private Button btnOk, btnCancel;

        private readonly Dictionary<string, CheckBox> extraSettings = new Dictionary<string, CheckBox>();
        private TextBox txtMountName;
        private NumericUpDown numHealHp, numHealMp;
        public void ShowToolSettings() { foreach (Control c in Controls) { TabControl tabs=c as TabControl; if (tabs!=null) tabs.SelectedIndex=1; } }
        public AccountItem Account { get; private set; }

        public static readonly string[] ServerList = new string[] {
            "Chiến Thần", "Rồng Lửa", "Phượng Hoàng", "Nhân Mã", "Kì Lân", "Thiên Hà", "Bạch Hổ", "Thách Đấu", "Global Server"
        };
        public static readonly string[] MarketList = new string[] { "Cổng Địa Ngục", "Cửa Biển", "Vách Đá Cheo Leo" };
        public static readonly string[] GemList = new string[] { "Kim Cương", "Rubi", "Saphia" };
        public static readonly string[] AnimalList = new string[] { "Lừa vàng (6 viên)", "Lạc đà vàng (12 viên)", "Bò vàng (24 viên)" };
        public static readonly string[] HpList = new string[] { "30% HP", "50% HP", "70% HP", "90% HP" };

        public AccountEditDialog(AccountItem item, bool isNew)
        {
            Account = item ?? new AccountItem();
            InitializeComponent(isNew);
        }

        private void InitializeComponent(bool isNew)
        {
            Text = isNew ? "Thêm tài khoản mới" : "Chỉnh sửa tài khoản — " + Account.DisplayName;
            Width = 520;
            Height = 480;
            MinimumSize = new Size(520, 480);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterParent;
            Font = new Font("Segoe UI", 9F);
            BackColor = Color.FromArgb(245, 246, 248);

            // Bottom Buttons Panel
            Panel pnlBottom = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 48,
                BackColor = Color.FromArgb(238, 240, 244),
                Padding = new Padding(12, 8, 12, 8)
            };

            btnOk = new Button
            {
                Text = "Lưu tài khoản",
                Size = new Size(120, 30),
                Location = new Point(270, 9),
                BackColor = Color.FromArgb(46, 125, 50),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnOk.Click += BtnOk_Click;

            btnCancel = new Button
            {
                Text = "Hủy",
                Size = new Size(85, 30),
                Location = new Point(400, 9),
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnCancel.Click += delegate { DialogResult = DialogResult.Cancel; Close(); };

            pnlBottom.Controls.Add(btnOk);
            pnlBottom.Controls.Add(btnCancel);

            // TabControl
            TabControl tabs = new TabControl
            {
                Dock = DockStyle.Fill,
                Font = new Font("Segoe UI", 9.5F)
            };

            TabPage tab1 = new TabPage { Text = "Tài khoản", BackColor = Color.White, AutoScroll = true };
            TabPage tab2 = new TabPage { Text = "Auto buôn", BackColor = Color.White, AutoScroll = true };
            TabPage tab3 = new TabPage { Text = "Di chuyển & hiển thị", BackColor = Color.White, AutoScroll = true };
            TabPage tab4 = new TabPage { Text = "Hỗ trợ nhân vật", BackColor = Color.White, AutoScroll = true };
            tabs.TabPages.AddRange(new TabPage[] { tab1, tab2, tab3, tab4 });

            int labelX = 20, inputX = 145, inputW = 310, y = 18, gap = 36;

            // --- TAB 1 ---
            Label lblUser = new Label { Text = "Tài khoản game:", Left = labelX, Top = y + 3, Width = 120 };
            txtUsername = new TextBox { Left = inputX, Top = y, Width = inputW, Text = Account.Username };
            tab1.Controls.AddRange(new Control[] { lblUser, txtUsername });

            y += gap;
            Label lblPass = new Label { Text = "Mật khẩu:", Left = labelX, Top = y + 3, Width = 120 };
            txtPassword = new TextBox { Left = inputX, Top = y, Width = inputW, Text = "", UseSystemPasswordChar = true };
            tab1.Controls.AddRange(new Control[] { lblPass, txtPassword });
            tab1.Controls.Add(new Label { Text = "Để trống: giữ mật khẩu đã lưu. Nhập để thay mới.", Left = inputX, Top = y + 24, Width = inputW, Height = 18 });
            y += 14;

            y += gap;
            Label lblServer = new Label { Text = "Máy chủ:", Left = labelX, Top = y + 3, Width = 120 };
            cmbServer = new ComboBox { Left = inputX, Top = y, Width = inputW, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbServer.Items.AddRange(ServerList);
            if (!string.IsNullOrEmpty(Account.Server) && cmbServer.Items.Contains(Account.Server))
                cmbServer.SelectedItem = Account.Server;
            else
                cmbServer.SelectedIndex = 0;
            tab1.Controls.AddRange(new Control[] { lblServer, cmbServer });

            y += gap;
            Label lblSlot = new Label { Text = "Chọn nhân vật:", Left = labelX, Top = y + 3, Width = 120 };
            cmbSlot = new ComboBox { Left = inputX, Top = y, Width = inputW, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbSlot.Items.AddRange(new object[] { "Nhân vật 1", "Nhân vật 2", "Nhân vật 3", "Tự chọn (chọn bằng tay khi vào game)" });
            if (Account.CharacterSlot >= 0 && Account.CharacterSlot < 3)
                cmbSlot.SelectedIndex = Account.CharacterSlot;
            else
                cmbSlot.SelectedIndex = 3;
            tab1.Controls.AddRange(new Control[] { lblSlot, cmbSlot });

            y += 28;
            Label lblSlotHint = new Label
            {
                Text = "• Tự động chọn nhân vật khi đăng nhập. Nếu tài khoản không có nhân vật đã chọn, game sẽ hiện thông báo nhắc nhở.",
                ForeColor = Color.FromArgb(100, 116, 139),
                Font = new Font("Segoe UI", 8.25F),
                Left = inputX,
                Top = y,
                Width = inputW,
                Height = 32
            };
            tab1.Controls.Add(lblSlotHint);

            y += 36;
            Label lblIdHint = new Label
            {
                Text = "Thư mục dữ liệu cô lập: .microemulator\\" + Account.Id,
                ForeColor = Color.FromArgb(71, 85, 105),
                Font = new Font("Segoe UI", 8.25F),
                Left = inputX,
                Top = y,
                Width = inputW
            };
            tab1.Controls.Add(lblIdHint);

            // --- TAB 2 ---
            int y2 = 16, gap2 = 34;

            Label lblMkt = new Label { Text = "Bãi mua buôn:", Left = labelX, Top = y2 + 3, Width = 120 };
            cmbMarket = new ComboBox { Left = inputX, Top = y2, Width = inputW, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbMarket.Items.AddRange(MarketList);
            cmbMarket.SelectedIndex = (Account.Market >= 0 && Account.Market < 3) ? Account.Market : 0;
            tab2.Controls.AddRange(new Control[] { lblMkt, cmbMarket });

            y2 += gap2;
            Label lblGem = new Label { Text = "Loại đá mua:", Left = labelX, Top = y2 + 3, Width = 120 };
            cmbGem = new ComboBox { Left = inputX, Top = y2, Width = inputW, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbGem.Items.AddRange(GemList);
            cmbGem.SelectedIndex = (Account.Gem >= 0 && Account.Gem < 3) ? Account.Gem : 0;
            tab2.Controls.AddRange(new Control[] { lblGem, cmbGem });

            y2 += gap2;
            Label lblAni = new Label { Text = "Thú buôn:", Left = labelX, Top = y2 + 3, Width = 120 };
            cmbAnimal = new ComboBox { Left = inputX, Top = y2, Width = inputW, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbAnimal.Items.AddRange(AnimalList);
            cmbAnimal.SelectedIndex = (Account.Animal >= 0 && Account.Animal < 3) ? Account.Animal : 2;
            tab2.Controls.AddRange(new Control[] { lblAni, cmbAnimal });

            y2 += gap2;
            Label lblHp = new Label { Text = "Đổi thú buôn dưới:", Left = labelX, Top = y2 + 3, Width = 120 };
            cmbHp = new ComboBox { Left = inputX, Top = y2, Width = inputW, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbHp.Items.AddRange(HpList);
            cmbHp.SelectedIndex = (Account.HpIndex >= 0 && Account.HpIndex < 4) ? Account.HpIndex : 1;
            tab2.Controls.AddRange(new Control[] { lblHp, cmbHp });

            y2 += gap2;
            chkAutoTrade = new CheckBox
            {
                Text = "Tự động bật buôn khi vào game",
                Left = inputX,
                Top = y2,
                Width = inputW,
                Checked = Account.AutoTradeOn
            };
            tab2.Controls.Add(chkAutoTrade);

            y2 += 26;
            chkAutoRecon = new CheckBox
            {
                Text = "Tự kết nối lại khi mất mạng / đứt kết nối (Auto Reconnect)",
                Left = inputX,
                Top = y2,
                Width = inputW,
                Checked = Account.AutoReconnect
            };
            tab2.Controls.Add(chkAutoRecon);

            y2 += 26;
            chkAutoMount = new CheckBox
            {
                Text = "Tự cưỡi thú khi di chuyển bộ",
                Left = inputX,
                Top = y2,
                Width = inputW,
                Checked = Account.AutoMount
            };
            tab2.Controls.Add(chkAutoMount);

            y2 += 26;
            chkAutoRestart = new CheckBox
            {
                Text = "Tự khởi động lại nếu bị văng/crash (Watchdog)",
                Left = inputX,
                Top = y2,
                Width = inputW,
                Checked = Account.AutoRestart
            };
            tab2.Controls.Add(chkAutoRestart);

            int extraY = 18;
            AddExtraSetting(tab3, "RotateMarkets", "Tự đổi bãi khi hết hàng", Account.RotateMarkets, ref extraY);
            AddExtraSetting(tab3, "Teleport", "Ưu tiên đá dịch chuyển", Account.Teleport, ref extraY);
            AddExtraSetting(tab3, "ShowHud", "Hiện bảng thông tin trong game", Account.ShowHud, ref extraY);
            AddExtraSetting(tab3, "ShowGuide", "Hiện đường chỉ thú buôn", Account.ShowGuide, ref extraY);
            AddExtraSetting(tab3, "FakeName", "Hiện tên giả PhucHso", Account.FakeName, ref extraY);
            AddExtraSetting(tab3, "SilentBuy", "Ẩn hộp thoại mua khi auto buôn", Account.SilentBuy, ref extraY);
            AddExtraSetting(tab3, "LowDetail", "Giảm lag: ẩn người/hiệu ứng, map ô vuông", Account.LowDetail, ref extraY);
            tab3.Controls.Add(new Label { Text = "Tên thú cưỡi (để trống: tự chọn trong túi):", Left = 20, Top = extraY, Width = 440 });
            txtMountName = new TextBox { Text = Account.MountName ?? "", Left = 20, Top = extraY + 26, Width = 430 };
            tab3.Controls.Add(txtMountName);
            tab3.Controls.Add(new Label { Text = "Lưu rồi mở lại game để áp dụng." + "\nTắt tự đổi bãi: chờ hàng tại bãi đã chọn, không đi sang bãi khác.", Left = 20, Top = extraY + 62, Width = 440, Height = 55, ForeColor = Color.DimGray });
            int supportY = 18;
            AddExtraSetting(tab4, "AutoAttack", "Tự đánh quái", Account.AutoAttack, ref supportY);
            AddExtraSetting(tab4, "AutoHeal", "Tự dùng bình HP / MP", Account.AutoHeal, ref supportY);
            AddExtraSetting(tab4, "AutoLoot", "Tự nhặt đồ ngoài lúc auto buôn", Account.AutoLoot, ref supportY);
            tab4.Controls.Add(new Label { Text = "Dùng bình khi HP dưới (%)", Left = 20, Top = supportY + 4, Width = 270 });
            numHealHp = new NumericUpDown { Left = 310, Top = supportY, Width = 100, Minimum = 1, Maximum = 100, Value = Math.Max(1, Math.Min(100, Account.HealHpPercent)) };
            tab4.Controls.Add(numHealHp); supportY += 38;
            tab4.Controls.Add(new Label { Text = "Dùng bình khi MP dưới (%)", Left = 20, Top = supportY + 4, Width = 270 });
            numHealMp = new NumericUpDown { Left = 310, Top = supportY, Width = 100, Minimum = 1, Maximum = 100, Value = Math.Max(1, Math.Min(100, Account.HealMpPercent)) };
            tab4.Controls.Add(numHealMp); supportY += 42;
            tab4.Controls.Add(new Label { Text = "Bình HP/MP dùng theo ô phím tắt đang có của nhân vật. Auto buôn vẫn bật nhặt hàng để không bỏ sót đá.\n\nCấu hình chỉ áp dụng cho tài khoản đang sửa. Lưu và mở lại tab game để áp dụng.", Left = 20, Top = supportY, Width = 440, Height = 110, ForeColor = Color.DimGray });
            TabPage proxyTab = new TabPage { Text = "Proxy SOCKS5", BackColor = Color.White, AutoScroll = true };
            tabs.TabPages.Add(proxyTab);
            chkProxy = new CheckBox { Text = "Dùng proxy riêng cho tài khoản này", Checked = Account.ProxyEnabled, Left = 20, Top = 18, Width = 440 };
            txtProxyHost = new TextBox { Left = 145, Top = 56, Width = 300, Text = Account.ProxyHost ?? "" };
            numProxyPort = new NumericUpDown { Left = 145, Top = 94, Width = 120, Minimum = 1, Maximum = 65535, Value = Math.Max(1, Math.Min(65535, Account.ProxyPort)) };
            txtProxyUser = new TextBox { Left = 145, Top = 132, Width = 300, Text = Account.ProxyUsername ?? "" };
            txtProxyPassword = new TextBox { Left = 145, Top = 170, Width = 300, UseSystemPasswordChar = true, Text = "" };
            proxyTab.Controls.AddRange(new Control[] { chkProxy, txtProxyHost, numProxyPort, txtProxyUser, txtProxyPassword,
                new Label { Text = "IP / Host:", Left = 20, Top = 59, Width = 120 },
                new Label { Text = "Port:", Left = 20, Top = 97, Width = 120 },
                new Label { Text = "Username:", Left = 20, Top = 135, Width = 120 },
                new Label { Text = "Password:", Left = 20, Top = 173, Width = 120 },
                new Label { Text = "Để trống mật khẩu: giữ giá trị đã lưu.\nĐể trống username nếu proxy không cần xác thực.", Left = 20, Top = 207, Width = 440, Height = 42 } });
            Button testProxy = new Button { Text = "Kiểm tra proxy", Left = 20, Top = 255, Width = 140, Height = 30 };
            lblProxyTest = new Label { Left = 20, Top = 292, Width = 440, Height = 65, Text = "Lưu rồi mở lại game để áp dụng. Proxy lỗi sẽ không chuyển sang IP mạng nhà." };
            testProxy.Click += delegate {
                string host = txtProxyHost.Text.Trim(), userName = txtProxyUser.Text.Trim(), secret;
                int port = (int)numProxyPort.Value;
                try { secret = txtProxyPassword.Text.Length > 0 ? txtProxyPassword.Text : Account.ReadProxyPassword(); }
                catch (Exception ex) { lblProxyTest.Text = ex.Message; return; }
                if (host.Length == 0) { lblProxyTest.Text = "Hãy nhập IP / Host proxy."; return; }
                testProxy.Enabled = false; lblProxyTest.Text = "Đang kiểm tra kết nối và xác thực SOCKS5...";
                ThreadPool.QueueUserWorkItem(delegate {
                    string result;
                    try { ProxyProbe.Check(host, port, userName, secret); result = "Xác thực SOCKS5 thành công. Kết nối tới server game sẽ được kiểm tra khi mở game."; }
                    catch (Exception ex) { result = "Proxy thất bại: " + ex.Message; }
                    try { if (!IsDisposed && IsHandleCreated) BeginInvoke((MethodInvoker)delegate { lblProxyTest.Text = result; testProxy.Enabled = true; }); }
                    catch (InvalidOperationException) { }
                });
            };
            proxyTab.Controls.Add(testProxy); proxyTab.Controls.Add(lblProxyTest);
            Controls.Add(tabs);
            Controls.Add(pnlBottom);
            AcceptButton = btnOk;
            CancelButton = btnCancel;
        }

        private void AddExtraSetting(TabPage tab, string key, string text, bool value, ref int y)
        {
            CheckBox box = new CheckBox { Text = text, Checked = value, Left = 20, Top = y, Width = 440, Height = 26 };
            extraSettings.Add(key, box); tab.Controls.Add(box); y += 32;
        }

        private void BtnOk_Click(object sender, EventArgs e)
        {
            if (chkProxy.Checked && (txtProxyHost.Text.Trim().Length == 0 || txtProxyHost.Text.IndexOf("://", StringComparison.Ordinal) >= 0)) {
                MessageBox.Show("Nhập IP/host proxy, không kèm giao thức hoặc port."); return;
            }
            if (Encoding.UTF8.GetByteCount(txtProxyUser.Text.Trim()) > 255 || Encoding.UTF8.GetByteCount(txtProxyPassword.Text) > 255) {
                MessageBox.Show("Username/password proxy tối đa 255 byte."); return;
            }
            Account.ProxyEnabled = chkProxy.Checked;
            Account.ProxyHost = txtProxyHost.Text.Trim(); Account.ProxyPort = (int)numProxyPort.Value;
            Account.ProxyUsername = txtProxyUser.Text.Trim(); Account.SetProxyPassword(txtProxyPassword.Text);
            string user = txtUsername.Text.Trim();

            int chosenSlot = (cmbSlot.SelectedIndex >= 0 && cmbSlot.SelectedIndex < 3) ? cmbSlot.SelectedIndex : -1;

            Account.DisplayName = !string.IsNullOrEmpty(user) ? user : Account.Id;
            Account.Username = user;
            if (txtPassword.Text.Length > 0) Account.Password = txtPassword.Text;
            Account.Server = cmbServer.Text.Trim();
            Account.CharacterSlot = chosenSlot;

            Account.Market = cmbMarket.SelectedIndex;
            Account.Gem = cmbGem.SelectedIndex;
            Account.Animal = cmbAnimal.SelectedIndex;
            Account.HpIndex = cmbHp.SelectedIndex;
            Account.AutoTradeOn = chkAutoTrade.Checked;
            Account.AutoReconnect = chkAutoRecon.Checked;
            Account.AutoMount = chkAutoMount.Checked;
            Account.AutoRestart = chkAutoRestart.Checked;
            Account.RotateMarkets = extraSettings["RotateMarkets"].Checked;
            Account.Teleport = extraSettings["Teleport"].Checked;
            Account.ShowHud = extraSettings["ShowHud"].Checked;
            Account.ShowGuide = extraSettings["ShowGuide"].Checked;
            Account.FakeName = extraSettings["FakeName"].Checked;
            Account.SilentBuy = extraSettings["SilentBuy"].Checked;
            Account.LowDetail = extraSettings["LowDetail"].Checked;
            Account.AutoAttack = extraSettings["AutoAttack"].Checked;
            Account.AutoHeal = extraSettings["AutoHeal"].Checked;
            Account.AutoLoot = extraSettings["AutoLoot"].Checked;
            Account.HealHpPercent = (int)numHealHp.Value;
            Account.HealMpPercent = (int)numHealMp.Value;
            Account.MountName = txtMountName.Text.Trim();

            DialogResult = DialogResult.OK;
            Close();
        }
    }

    public class SettingsDialog : Form
    {
        private TextBox txtJava, txtEmulator, txtGame;
        private NumericUpDown numDelay, numCleanup;
        private CheckBox chkRename;
        private Button btnOk, btnCancel;
        public AppSettings Settings { get; private set; }

        public SettingsDialog(AppSettings s)
        {
            Settings = s;
            InitializeComponent();
        }

        private void InitializeComponent()
        {
            Text = "Cài đặt đường dẫn & Khởi chạy";
            Width = 560;
            Height = 390;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterParent;
            Font = new Font("Segoe UI", 9F);
            BackColor = Color.FromArgb(248, 249, 250);

            int lx = 20, ix = 160, iw = 270, bx = 440, y = 20, gap = 40;

            Label l1 = new Label { Text = "Đường dẫn java.exe:", Left = lx, Top = y + 3, Width = 135 };
            txtJava = new TextBox { Left = ix, Top = y, Width = iw, Text = Settings.JavaPath };
            Button b1 = new Button { Text = "Duyệt...", Left = bx, Top = y - 1, Width = 75, Height = 25 };
            b1.Click += delegate { BrowseFile(txtJava, "Java Executable|java.exe;javaw.exe|All Files|*.*"); };
            Controls.AddRange(new Control[] { l1, txtJava, b1 });

            y += gap;
            Label l2 = new Label { Text = "MicroEmulator JAR:", Left = lx, Top = y + 3, Width = 135 };
            txtEmulator = new TextBox { Left = ix, Top = y, Width = iw, Text = Settings.EmulatorJar };
            Button b2 = new Button { Text = "Duyệt...", Left = bx, Top = y - 1, Width = 75, Height = 25 };
            b2.Click += delegate { BrowseFile(txtEmulator, "JAR Files|*.jar|All Files|*.*"); };
            Controls.AddRange(new Control[] { l2, txtEmulator, b2 });

            y += gap;
            Label l3 = new Label { Text = "Knight Online JAR:", Left = lx, Top = y + 3, Width = 135 };
            txtGame = new TextBox { Left = ix, Top = y, Width = iw, Text = Settings.GameJar };
            Button b3 = new Button { Text = "Duyệt...", Left = bx, Top = y - 1, Width = 75, Height = 25 };
            b3.Click += delegate { BrowseFile(txtGame, "JAR Files|*.jar|All Files|*.*"); };
            Controls.AddRange(new Control[] { l3, txtGame, b3 });

            y += gap;
            Label l4 = new Label { Text = "Trễ mở hàng loạt (s):", Left = lx, Top = y + 3, Width = 135 };
            numDelay = new NumericUpDown { Left = ix, Top = y, Width = 80, Minimum = 1, Maximum = 10, Value = Math.Max(1, Math.Min(10, Settings.LaunchDelaySeconds)) };
            Controls.AddRange(new Control[] { l4, numDelay });

            y += gap;
            chkRename = new CheckBox
            {
                Text = "Tự động đổi tiêu đề cửa sổ theo tên tài khoản & nhân vật",
                Left = ix,
                Top = y,
                Width = 350,
                Checked = Settings.RenameWindowTitle
            };
            Controls.Add(chkRename);

            y += gap;
            numCleanup = new NumericUpDown { Left = ix, Top = y, Width = 80, Minimum = 0, Maximum = 120,
                Value = Math.Max(0, Math.Min(120, Settings.MemoryCleanupMinutes)) };
            Controls.Add(new Label { Text = "Thu gom RAM (phút):", Left = lx, Top = y + 3, Width = 135 });
            Controls.Add(numCleanup);
            Controls.Add(new Label { Text = "0: tắt; chờ lúc auto rảnh để thu gom.", Left = ix + 90, Top = y + 3, Width = 260 });

            y += gap + 5;
            btnOk = new Button
            {
                Text = "Lưu cài đặt",
                Left = ix,
                Top = y,
                Width = 110,
                Height = 32,
                BackColor = Color.FromArgb(21, 101, 192),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            btnOk.Click += delegate
            {
                Settings.JavaPath = txtJava.Text.Trim();
                Settings.EmulatorJar = txtEmulator.Text.Trim();
                Settings.GameJar = txtGame.Text.Trim();
                Settings.LaunchDelaySeconds = (int)numDelay.Value;
                Settings.RenameWindowTitle = chkRename.Checked;
                Settings.MemoryCleanupMinutes = (int)numCleanup.Value;
                Settings.DetectPaths();
                try { Settings.Save(); }
                catch (Exception ex) {
                    MessageBox.Show("Không lưu được cài đặt: " + ex.Message, "Lỗi lưu", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return;
                }
                DialogResult = DialogResult.OK;
                Close();
            };

            btnCancel = new Button { Text = "Đóng", Left = ix + 120, Top = y, Width = 90, Height = 32, FlatStyle = FlatStyle.Flat };
            btnCancel.Click += delegate { Close(); };

            Button detect = new Button { Text = "Tự nhận diện", Left = ix + 220, Top = y, Width = 110, Height = 32 };
            detect.Click += delegate {
                AppSettings detected = new AppSettings();
                detected.DetectPaths();
                txtJava.Text = detected.JavaPath; txtEmulator.Text = detected.EmulatorJar; txtGame.Text = detected.GameJar;
            };
            Controls.AddRange(new Control[] { btnOk, btnCancel, detect });
        }

        private void BrowseFile(TextBox target, string filter)
        {
            using (OpenFileDialog ofd = new OpenFileDialog())
            {
                ofd.Filter = filter;
                if (ofd.ShowDialog() == DialogResult.OK) target.Text = ofd.FileName;
            }
        }
    }

    public static class WindowLayout
    {
        public static Rectangle[] Arrange(Rectangle area, int count)
        {
            if (count <= 0) return new Rectangle[0];
            int cols = Math.Min(count, Math.Max(1, area.Width / 310));
            int rows = (count + cols - 1) / cols;
            int width = Math.Min(310, area.Width / cols), height = Math.Min(410, area.Height / rows);
            var result = new Rectangle[count];
            int left = area.Left + (area.Width - cols * width) / 2;
            for (int i = 0; i < count; i++) result[i] = new Rectangle(left + (i % cols) * width, area.Top + (i / cols) * height, width, height);
            return result;
        }
    }

    public class MainForm : Form
    {
        private List<AccountItem> accounts = new List<AccountItem>();
        private AppSettings settings;
        private DataGridView dgvAccounts;
        private ToolStripStatusLabel lblStatus, lblSummary;
        private System.Windows.Forms.Timer updateTimer;
        private bool isBatchLaunching = false;
        private System.Windows.Forms.Timer launchTimer;
        private readonly Queue<AccountItem> pendingLaunches = new Queue<AccountItem>();
        private bool accountsLoadFailed;
        private bool allHidden;
        private ToolStripStatusLabel lblGold;

        private static string AccountsFile
        {
            get { return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "accounts.json"); }
        }

        public MainForm(string javaOverride = null)
        {
            Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            settings = AppSettings.Load();
            if (!string.IsNullOrEmpty(javaOverride)) settings.JavaPath = javaOverride;
            InitializeComponent();
            LoadAccounts();
            StartTimer();
        }

        private static string FormatSlot(int slot)
        {
            switch (slot)
            {
                case 0: return "NV 1";
                case 1: return "NV 2";
                case 2: return "NV 3";
                default: return "Tự chọn";
            }
        }

        private static string FormatTradeSummary(AccountItem acc)
        {
            string[] mkt = new string[] { "Cổng ĐN", "Cửa Biển", "Vách Đá" };
            string[] gem = new string[] { "Kim Cương", "Rubi", "Saphia" };
            string[] ani = new string[] { "Lừa 6v", "Lạc đà 12v", "Bò 24v" };

            string m = (acc.Market >= 0 && acc.Market < 3) ? mkt[acc.Market] : "Cổng ĐN";
            string g = (acc.Gem >= 0 && acc.Gem < 3) ? gem[acc.Gem] : "Kim Cương";
            string a = (acc.Animal >= 0 && acc.Animal < 3) ? ani[acc.Animal] : "Bò 24v";
            return string.Format("{0} | {1} | {2}", m, g, a);
        }

        private void InitializeComponent()
        {
            Text = "PhucHso — Quản lý tài khoản";
            Width = 1020;
            Height = 580;
            MinimumSize = new Size(850, 480);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 9F);
            BackColor = Color.FromArgb(240, 242, 245);

            // Toolbar Panel: 2 hàng nút khoa học, gọn gàng, KHÔNG cần cuộn ngang
            Panel pnlToolbar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 138,
                BackColor = Color.FromArgb(245, 246, 248)
            };

            FlowLayoutPanel toolbarRow1 = new FlowLayoutPanel
            {
                Dock = DockStyle.Top,
                Height = 47,
                BackColor = Color.FromArgb(245, 246, 248),
                Padding = new Padding(8, 4, 8, 2),
                AutoScroll = true,
                WrapContents = false
            };

            FlowLayoutPanel toolbarRow2 = new FlowLayoutPanel
            {
                Dock = DockStyle.Top,
                Height = 47,
                BackColor = Color.FromArgb(245, 246, 248),
                Padding = new Padding(8, 2, 8, 4),
                AutoScroll = true,
                WrapContents = false
            };

            FlowLayoutPanel windowTools = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 44, Padding = new Padding(8, 2, 8, 2), AutoScroll = true, WrapContents = false };
            Button hideAll = CreateButton("Ẩn tất cả", Color.White, Color.Black);
            hideAll.Click += delegate { SetAllVisible(false); };
            Button showAll = CreateButton("Hiện tất cả", Color.White, Color.Black);
            showAll.Click += delegate { SetAllVisible(true); };
            Button stopAll = CreateButton("Tắt tất cả game", Color.White, Color.Black);
            stopAll.Click += delegate { StopAll(); };
            windowTools.Controls.AddRange(new Control[] { hideAll, showAll, stopAll });
            pnlToolbar.Controls.Add(windowTools);
            pnlToolbar.Controls.Add(toolbarRow2);
            pnlToolbar.Controls.Add(toolbarRow1);

            // Hàng 1: Vận hành game & cửa sổ
            Button btnLaunchAll = CreateButton("Mở các tài khoản đã tích", Color.FromArgb(46, 125, 50), Color.White);
            btnLaunchAll.Click += delegate { LaunchChecked(); };

            Button btnLaunchSelected = CreateButton("Mở game (F5)", Color.FromArgb(21, 101, 192), Color.White);
            btnLaunchSelected.Click += delegate { LaunchSelected(); };

            Button btnStopSelected = CreateButton("Dừng game", Color.FromArgb(230, 81, 0), Color.White);
            btnStopSelected.Click += delegate { StopSelected(); };

            Button btnAutoOn = CreateButton("Bật auto", Color.FromArgb(46, 125, 50), Color.White);
            btnAutoOn.Click += delegate { SetSelectedAuto(true); };
            Button btnAutoOff = CreateButton("Dừng auto", Color.FromArgb(230, 81, 0), Color.White);
            btnAutoOff.Click += delegate { SetSelectedAuto(false); };

            Button btnTile = CreateButton("Xếp cửa sổ (F9)", Color.FromArgb(106, 27, 154), Color.White);
            btnTile.Click += delegate { TileWindows(); };

            toolbarRow1.Controls.AddRange(new Control[] {
                btnLaunchSelected, btnLaunchAll, btnAutoOn, btnAutoOff, btnStopSelected, btnTile
            });

            // Hàng 2: Quản lý danh sách & Hồ sơ
            Button btnAdd = CreateButton("Thêm tài khoản", Color.FromArgb(55, 71, 79), Color.White);
            btnAdd.Click += delegate { AddAccount(); };

            Button btnEdit = CreateButton("Sửa", Color.FromArgb(55, 71, 79), Color.White);
            btnEdit.Click += delegate { EditSelected(); };

            Button btnTool = CreateButton("Cấu hình tool", Color.White, Color.Black);
            btnTool.Click += delegate { EditSelected(true); };

            Button btnDelete = CreateButton("Xóa", Color.FromArgb(198, 40, 40), Color.White);
            btnDelete.Click += delegate { DeleteSelected(); };

            Button btnSelectAll = CreateButton("Chọn hết", Color.FromArgb(84, 110, 122), Color.White);
            btnSelectAll.Click += delegate { SetAllChecked(true); };

            Button btnUnselectAll = CreateButton("Bỏ chọn", Color.FromArgb(84, 110, 122), Color.White);
            btnUnselectAll.Click += delegate { SetAllChecked(false); };

            Button btnSettings = CreateButton("Cài đặt", Color.FromArgb(55, 71, 79), Color.White);
            btnSettings.Click += delegate { OpenSettings(); };
            toolbarRow1.Controls.Add(btnSettings);

            toolbarRow2.Controls.AddRange(new Control[] {
                btnAdd, btnEdit, btnTool, btnDelete, btnSelectAll, btnUnselectAll
            });

            // DataGridView Setup with ScrollBars.Both
            dgvAccounts = new DataGridView
            {
                Dock = DockStyle.Fill,
                BackgroundColor = Color.White,
                BorderStyle = BorderStyle.None,
                RowHeadersVisible = false,
                AllowUserToAddRows = false,
                AllowUserToDeleteRows = false,
                AllowUserToResizeRows = false,
                SelectionMode = DataGridViewSelectionMode.FullRowSelect,
                MultiSelect = false,
                AutoGenerateColumns = false,
                RowTemplate = { Height = 34 },
                EnableHeadersVisualStyles = false,
                ScrollBars = ScrollBars.Both
            };

            dgvAccounts.ColumnHeadersDefaultCellStyle.BackColor = Color.FromArgb(235, 238, 242);
            dgvAccounts.ColumnHeadersDefaultCellStyle.ForeColor = Color.FromArgb(32, 40, 48);
            dgvAccounts.ColumnHeadersDefaultCellStyle.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
            dgvAccounts.ColumnHeadersHeight = 36;
            dgvAccounts.CellBorderStyle = DataGridViewCellBorderStyle.Single;
            dgvAccounts.ColumnHeadersBorderStyle = DataGridViewHeaderBorderStyle.Single;
            dgvAccounts.GridColor = Color.FromArgb(190, 198, 208);
            dgvAccounts.DefaultCellStyle.SelectionBackColor = Color.FromArgb(220, 235, 252);
            dgvAccounts.DefaultCellStyle.SelectionForeColor = Color.FromArgb(25, 45, 65);
            dgvAccounts.AlternatingRowsDefaultCellStyle.BackColor = Color.FromArgb(248, 249, 250);

            DataGridViewCheckBoxColumn colSelect = new DataGridViewCheckBoxColumn
            {
                HeaderText = "[v]",
                Width = 30,
                DataPropertyName = "Selected"
            };

            DataGridViewTextBoxColumn colId = new DataGridViewTextBoxColumn
            {
                HeaderText = "Mã ID",
                Width = 55,
                DataPropertyName = "Id",
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colDisplayName = new DataGridViewTextBoxColumn
            {
                HeaderText = "Tên gợi nhớ",
                Width = 140,
                DataPropertyName = "DisplayName",
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colUser = new DataGridViewTextBoxColumn
            {
                HeaderText = "Tài khoản",
                Width = 85,
                DataPropertyName = "Username",
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colPass = new DataGridViewTextBoxColumn
            {
                HeaderText = "Mật khẩu",
                Width = 65,
                DataPropertyName = "",
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colServer = new DataGridViewTextBoxColumn
            {
                HeaderText = "Máy chủ",
                Width = 80,
                DataPropertyName = "Server",
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colSlot = new DataGridViewTextBoxColumn
            {
                HeaderText = "Nhân vật",
                Width = 65,
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colTrade = new DataGridViewTextBoxColumn
            {
                HeaderText = "Cài đặt buôn",
                Width = 150,
                ReadOnly = true
            };

            DataGridViewCheckBoxColumn colAutoTrade = new CheckBoxColumnCustom
            {
                HeaderText = "Auto buôn",
                Width = 65,
                DataPropertyName = "AutoTradeOn"
            };

            DataGridViewCheckBoxColumn colRestart = new CheckBoxColumnCustom
            {
                HeaderText = "Watchdog",
                Width = 65,
                DataPropertyName = "AutoRestart"
            };

            DataGridViewTextBoxColumn colStatus = new DataGridViewTextBoxColumn
            {
                HeaderText = "Trạng thái",
                Width = 110,
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colUptime = new DataGridViewTextBoxColumn
            {
                HeaderText = "Thời gian",
                Width = 65,
                ReadOnly = true
            };

            DataGridViewTextBoxColumn colNotes = new DataGridViewTextBoxColumn
            {
                HeaderText = "Ghi chú",
                Width = 90,
                AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill,
                DataPropertyName = "Notes",
                ReadOnly = true
            };

            dgvAccounts.Columns.AddRange(new DataGridViewColumn[] {
                colSelect, colId, colDisplayName, colUser, colPass, colServer, colSlot, colTrade, colAutoTrade, colRestart, colStatus, colUptime, colNotes
            });
            dgvAccounts.Columns.Add(new DataGridViewTextBoxColumn { Name = "ProxyStatus", HeaderText = "Proxy SOCKS5", DataPropertyName = "ProxyStatus", ReadOnly = true, Width = 230 });
            dgvAccounts.Columns.Add(new DataGridViewTextBoxColumn { Name = "TradeGold", HeaderText = "Vàng bán hàng", DataPropertyName = "TradeGold", ReadOnly = true, Width = 130,
                DefaultCellStyle = new DataGridViewCellStyle { Format = "N0" } });

            // Keep secondary settings in the edit dialog; preserve column indices.
            colId.Visible = colDisplayName.Visible = colPass.Visible = colTrade.Visible = colRestart.Visible = colNotes.Visible = false;
            colSelect.HeaderText = "Chọn";
            colSelect.Width = 48;
            colSelect.AutoSizeMode = DataGridViewAutoSizeColumnMode.None;
            DataGridViewColumn[] flexibleColumns = { colUser, colServer, colSlot, colAutoTrade, colStatus, colUptime };
            float[] weights = { 26, 18, 12, 12, 20, 12 };
            int[] minimumWidths = { 140, 105, 80, 85, 145, 85 };
            for (int i = 0; i < flexibleColumns.Length; i++) {
                flexibleColumns[i].AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill;
                flexibleColumns[i].FillWeight = weights[i];
                flexibleColumns[i].MinimumWidth = minimumWidths[i];
            }
            colSlot.DefaultCellStyle.Alignment = DataGridViewContentAlignment.MiddleCenter;
            colUptime.DefaultCellStyle.Alignment = DataGridViewContentAlignment.MiddleCenter;
            colSelect.HeaderCell.Style.Alignment = DataGridViewContentAlignment.MiddleCenter;
            colSlot.HeaderCell.Style.Alignment = DataGridViewContentAlignment.MiddleCenter;
            colAutoTrade.HeaderCell.Style.Alignment = DataGridViewContentAlignment.MiddleCenter;
            colUptime.HeaderCell.Style.Alignment = DataGridViewContentAlignment.MiddleCenter;

            // Click vào tiêu đề cột [v] để chọn hoặc bỏ chọn tất cả
            dgvAccounts.ColumnHeaderMouseClick += delegate (object s, DataGridViewCellMouseEventArgs e)
            {
                if (e.ColumnIndex == 0)
                {
                    bool hasUnselected = false;
                    foreach (var a in accounts) { if (!a.Selected) { hasUnselected = true; break; } }
                    SetAllChecked(hasUnselected);
                }
            };

            dgvAccounts.CellFormatting += DgvAccounts_CellFormatting;
            dgvAccounts.CurrentCellDirtyStateChanged += delegate {
                if (!dgvAccounts.IsCurrentCellDirty || dgvAccounts.CurrentCell == null) return;
                int column = dgvAccounts.CurrentCell.ColumnIndex;
                if (column != 0 && column != 8) return;
                DataGridViewCell cell = dgvAccounts.CurrentCell;
                AccountItem item = cell.OwningRow.DataBoundItem as AccountItem;
                dgvAccounts.CommitEdit(DataGridViewDataErrorContexts.Commit);
                dgvAccounts.EndEdit();
                if (item == null) return;
                bool enabled = Convert.ToBoolean(cell.Value);
                if (column == 8) ApplyAccountAuto(item, enabled);
                else { item.Selected = enabled; SaveAccounts(); }
            };
            dgvAccounts.SelectionChanged += delegate { ShowSelectedAccount(); };
            dgvAccounts.CurrentCellChanged += delegate { ShowSelectedAccount(); };
            dgvAccounts.CellDoubleClick += delegate (object sender, DataGridViewCellEventArgs e) { if (e.RowIndex >= 0) EditSelected(); };

            // Context Menu
            ContextMenuStrip ctx = new ContextMenuStrip();
            ctx.Items.Add("▶ Mở tài khoản này (F5)", null, delegate { LaunchSelected(); });
            ctx.Items.Add("Khởi động lại", null, delegate { RestartSelected(); });
            ctx.Items.Add("⏹ Dừng tài khoản này", null, delegate { StopSelected(); });
            ctx.Items.Add("Dừng tất cả", null, delegate { StopAll(); });
            ctx.Items.Add(new ToolStripSeparator());
            ctx.Items.Add("✎ Chỉnh sửa...", null, delegate { EditSelected(); });
            ctx.Items.Add("Nhân bản tài khoản", null, delegate { DuplicateSelected(); });
            ctx.Items.Add("Xóa tài khoản", null, delegate { DeleteSelected(); });
            ctx.Items.Add(new ToolStripSeparator());
            ctx.Items.Add("📁 Mở thư mục dữ liệu RMS riêng", null, delegate { OpenRmsFolder(); });
            ctx.Items.Add("⊞ Xếp cửa sổ game vào vị trí", null, delegate { TileWindows(); });
            dgvAccounts.ContextMenuStrip = ctx;
            Button btnMore = CreateButton("Thao tác khác", Color.White, Color.Black);
            btnMore.Click += delegate { ctx.Show(btnMore, new Point(0, btnMore.Height)); };
            toolbarRow2.Controls.Add(btnMore);

            // Status Strip
            StatusStrip statusStrip = new StatusStrip { BackColor = Color.FromArgb(235, 237, 240) };
            lblStatus = new ToolStripStatusLabel { Text = "Sẵn sàng", Spring = true, TextAlign = ContentAlignment.MiddleLeft };
            lblSummary = new ToolStripStatusLabel { Text = "Tổng: 0 nick | Đang chạy: 0" };
            statusStrip.Items.AddRange(new ToolStripItem[] { lblStatus, lblSummary });
            StatusStrip goldStrip = new StatusStrip { Dock = DockStyle.Bottom, SizingGrip = false };
            lblGold = new ToolStripStatusLabel { Text = "Tổng vàng bán hàng: 0 (chưa trừ vốn)" };
            goldStrip.Items.Add(lblGold);

            Panel tableContainer = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(14, 10, 14, 14),
                BackColor = Color.FromArgb(240, 242, 245)
            };
            dgvAccounts.BorderStyle = BorderStyle.FixedSingle;
            tableContainer.Controls.Add(dgvAccounts);
            Controls.Add(tableContainer);
            Controls.Add(pnlToolbar);
            Controls.Add(statusStrip);
            Controls.Add(goldStrip);

            KeyPreview = true;
            KeyDown += MainForm_KeyDown;
        }

        private class CheckBoxColumnCustom : DataGridViewCheckBoxColumn { }

        private Button CreateButton(string text, Color bg, Color fg)
        {
            Button btn = new Button
            {
                Text = text,
                BackColor = text == "Mở game (F5)" ? Color.FromArgb(35, 105, 180) : Color.White,
                ForeColor = text == "Mở game (F5)" ? Color.White : Color.FromArgb(40, 48, 56),
                FlatStyle = FlatStyle.Flat,
                Height = 32,
                AutoSize = true,
                Margin = new Padding(0, 0, 5, 0),
                Cursor = Cursors.Hand
            };
            btn.FlatAppearance.BorderSize = 1;
            btn.FlatAppearance.BorderColor = Color.FromArgb(205, 211, 218);
            return btn;
        }

        private void MainForm_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.F5) { LaunchSelected(); e.Handled = true; }
            else if (e.KeyCode == Keys.F9) { TileWindows(); e.Handled = true; }
            else if (e.KeyCode == Keys.Delete) { DeleteSelected(); e.Handled = true; }
            else if (e.Control && e.KeyCode == Keys.A) { SetAllChecked(true); e.Handled = true; }
            else if (e.Control && e.KeyCode == Keys.D) { SetAllChecked(false); e.Handled = true; }
        }

        private void SetAllChecked(bool value)
        {
            foreach (var a in accounts) a.Selected = value;
            dgvAccounts.Refresh();
        }

        private void StartTimer()
        {
            updateTimer = new System.Windows.Forms.Timer { Interval = 1000 };
            updateTimer.Tick += UpdateTimer_Tick;
            updateTimer.Start();
        }

        private void UpdateTimer_Tick(object sender, EventArgs e)
        {
            int runningCount = 0;
            DateTime now = DateTime.Now;
            bool statisticsChanged = false;

            foreach (var acc in accounts)
            {
                statisticsChanged |= ReadStatistics(acc);
                if (acc.IsRunning && !string.IsNullOrEmpty(acc.ControlFile)) {
                    try {
                        string proxyFile = acc.ControlFile + ".proxy";
                        if (File.Exists(proxyFile)) {
                            string state = File.ReadAllText(proxyFile, Encoding.UTF8);
                            if (state.Length > 0 && state != acc.ProxyStatus) { acc.ProxyStatus = state; lblStatus.Text = acc.DisplayName + " — Proxy: " + state; dgvAccounts.Refresh(); }
                        }
                    } catch (IOException) { }
                } else { acc.ProxyStatus = acc.ProxyEnabled ? "Đã bật — game chưa chạy" : "Tắt"; }
                if (acc.IsRunning)
                {
                    runningCount++;

                    // Find window and set custom title
                    if (acc.WindowHandle == IntPtr.Zero)
                    {
                        acc.WindowHandle = Win32.FindMainWindow(acc.Process.Id);
                        if (acc.WindowHandle != IntPtr.Zero)
                        {
                            string title = string.Format("Knight Online [{0}] - {1} ({2})", acc.Id, acc.DisplayName, FormatSlot(acc.CharacterSlot));
                            if (settings.RenameWindowTitle) Win32.RenameWindow(acc.WindowHandle, title);
                        }
                    }
                }
                else
                {
                    acc.WindowHandle = IntPtr.Zero;

                    // Watchdog: Auto-restart if crashed
                    if (acc.AutoRestart && !acc.UserStopped && acc.Process != null && acc.Process.HasExited)
                    {
                        if (acc.NextRestartAt == DateTime.MinValue)
                        {
                            acc.NextRestartAt = now.AddSeconds(5);
                            lblStatus.Text = string.Format("Nick '{0}' đã thoát. Tự khởi động lại sau 5s...", acc.DisplayName);
                        }
                        else if (now >= acc.NextRestartAt)
                        {
                            acc.NextRestartAt = DateTime.MinValue;
                            LaunchProcess(acc);
                        }
                    }
                }
            }

            lblSummary.Text = string.Format("Tổng: {0} nick | Đang chạy: {1} | Đã chọn: {2}",
                accounts.Count, runningCount, accounts.FindAll(delegate (AccountItem a) { return a.Selected; }).Count);
            foreach (var acc in accounts) if (acc.IsRunning && acc.Background) ApplyWindowVisibility(acc, false);
            if (statisticsChanged)
            {
                SaveAccounts();
                dgvAccounts.Refresh();
            }
            decimal totalGold = 0, unmeasured = 0;
            foreach (var acc in accounts) { totalGold += acc.TradeGold; unmeasured += acc.UnmeasuredSales; }
            lblGold.Text = string.Format("Tổng vàng bán hàng: {0:N0} (chưa trừ vốn) | Chuyến chưa rõ số vàng: {1:N0}", totalGold, unmeasured);
            dgvAccounts.InvalidateColumn(13);

            dgvAccounts.InvalidateColumn(10);
            dgvAccounts.InvalidateColumn(11);
        }

        private void DgvAccounts_CellFormatting(object sender, DataGridViewCellFormattingEventArgs e)
        {
            if (e.RowIndex < 0 || e.RowIndex >= dgvAccounts.Rows.Count) return;
            AccountItem acc = dgvAccounts.Rows[e.RowIndex].DataBoundItem as AccountItem;
            if (acc == null) return;

            // Password mask (col index 4)
            if (e.ColumnIndex == 4)
            {
                e.Value = string.IsNullOrEmpty(acc.PasswordProtected) ? "" : "••••••••";
                e.FormattingApplied = true;
            }

            // Character slot (col index 6)
            if (e.ColumnIndex == 6)
            {
                e.Value = FormatSlot(acc.CharacterSlot);
            }

            // Trade settings (col index 7)
            if (e.ColumnIndex == 7)
            {
                e.Value = FormatTradeSummary(acc);
            }

            // Status column (col index 10)
            if (e.ColumnIndex == 10)
            {
                if (acc.IsRunning)
                {
                    e.Value = string.Format("● Đang chạy ({0})", acc.Process.Id);
                    e.CellStyle.ForeColor = Color.FromArgb(46, 125, 50);
                }
                else
                {
                    e.Value = "○ Đã dừng";
                    e.CellStyle.ForeColor = Color.FromArgb(158, 158, 158);
                }
            }

            // Trade gold column (col index 13)
            if (e.ColumnIndex >= 0 && e.ColumnIndex < dgvAccounts.Columns.Count && dgvAccounts.Columns[e.ColumnIndex].Name == "TradeGold")
            {
                e.Value = acc.TradeGold.ToString("N0");
                e.FormattingApplied = true;
            }

            // Uptime column (col index 11)
            if (e.ColumnIndex == 11)
            {
                if (acc.IsRunning && acc.StartTime != DateTime.MinValue)
                {
                    TimeSpan ts = DateTime.Now - acc.StartTime;
                    e.Value = string.Format("{0:D2}:{1:D2}:{2:D2}", (int)ts.TotalHours, ts.Minutes, ts.Seconds);
                    e.CellStyle.ForeColor = Color.FromArgb(33, 33, 33);
                }
                else
                {
                    e.Value = "--";
                    e.CellStyle.ForeColor = Color.FromArgb(189, 189, 189);
                }
            }
        }

        private AccountItem GetSelectedAccount()
        {
            if (dgvAccounts.SelectedRows.Count == 0) return null;
            return dgvAccounts.SelectedRows[0].DataBoundItem as AccountItem;
        }

        private void ShowSelectedAccount()
        {
            if (lblStatus == null) return;
            AccountItem acc = GetSelectedAccount();
            lblStatus.Text = acc == null ? "Chưa chọn dòng tài khoản" : string.Format(
                "Đang chọn: {0} | {1} | {2} | {3}",
                string.IsNullOrEmpty(acc.Username) ? acc.DisplayName : acc.Username,
                acc.Server, FormatSlot(acc.CharacterSlot),
                acc.IsRunning ? "PID: " + acc.Process.Id : "Chưa mở game");
        }

        private void FocusAccountWindow(AccountItem acc)
        {
            // Resolve from this row's process every time; never reuse a stale HWND.
            if (!acc.IsRunning) return;
            acc.WindowHandle = AccountWindow(acc);
            acc.Background = false;
            QueueRuntimeSettings(acc);
            if (acc.WindowHandle != IntPtr.Zero)
            {
                ApplyWindowVisibility(acc, true);
                Win32.SetForegroundWindow(acc.WindowHandle);
            }
            ShowSelectedAccount();
        }

        private void LaunchSelected()
        {
            dgvAccounts.EndEdit();
            AccountItem acc = GetSelectedAccount();
            if (acc == null)
            {
                MessageBox.Show("Vui lòng chọn một tài khoản để mở!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            if (acc.IsRunning) FocusAccountWindow(acc);
            else LaunchProcess(acc);
        }

        private void LaunchChecked()
        {
            dgvAccounts.EndEdit();
            if (isBatchLaunching)
            {
                MessageBox.Show("Đang trong quá trình khởi chạy hàng loạt!", "Chờ", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            List<AccountItem> targets = accounts.FindAll(delegate (AccountItem a) { return a.Selected && !a.IsRunning; });
            if (targets.Count == 0)
            {
                MessageBox.Show("Không có tài khoản nào được tích chọn hoặc tất cả đã đang chạy!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            isBatchLaunching = true;
            foreach (AccountItem target in targets) { target.UserStopped = false; pendingLaunches.Enqueue(target); }
            launchTimer = new System.Windows.Forms.Timer();
            launchTimer.Interval = Math.Max(1, Math.Min(300, settings.LaunchDelaySeconds)) * 1000;
            launchTimer.Tick += delegate { LaunchNextPending(); };
            launchTimer.Start();
            LaunchNextPending();
        }

        private void LaunchNextPending()
        {
            if (launchTimer != null) launchTimer.Stop();
            if (pendingLaunches.Count == 0) { CancelBatchLaunch(); return; }
            AccountItem item = pendingLaunches.Dequeue();
            if (accounts.Contains(item) && !item.UserStopped && !item.IsRunning) LaunchProcess(item);
            if (pendingLaunches.Count == 0) CancelBatchLaunch();
            else if (launchTimer != null) launchTimer.Start();
        }

        private void CancelBatchLaunch()
        {
            pendingLaunches.Clear();
            if (launchTimer != null) { launchTimer.Stop(); launchTimer.Dispose(); launchTimer = null; }
            isBatchLaunching = false;
        }

        private void EnsureProfileConfig(string profileDir, string gameJar)
        {
            try
            {
                string cfgFile = Path.Combine(profileDir, "config2.xml");
                if (!File.Exists(cfgFile))
                {
                    string xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<config>\n" +
                        "\t<devices>\n" +
                        "\t\t<device>\n" +
                        "\t\t\t<name>Resizable device</name>\n" +
                        "\t\t\t<filename/>\n" +
                        "\t\t\t<descriptor>org/microemu/device/resizable/device.xml</descriptor>\n" +
                        "\t\t\t<rectangle>\n" +
                        "\t\t\t\t<x>0</x>\n" +
                        "\t\t\t\t<y>0</y>\n" +
                        "\t\t\t\t<width>300</width>\n" +
                        "\t\t\t\t<height>300</height>\n" +
                        "\t\t\t</rectangle>\n" +
                        "\t\t</device>\n" +
                        "\t</devices>\n" +
                        "</config>\n";
                    File.WriteAllText(cfgFile, xml, Encoding.UTF8);
                }

                // Check migration from legacy nested folder if present
                string nestedDir = Path.Combine(profileDir, ".microemulator");
                if (Directory.Exists(nestedDir))
                {
                    string nestedSuite = Path.Combine(nestedDir, "suite-KnightOnline Midlet Suite");
                    string targetSuite = Path.Combine(profileDir, "suite-KnightOnline Midlet Suite");
                    if (Directory.Exists(nestedSuite) && !Directory.Exists(targetSuite))
                    {
                        Directory.Move(nestedSuite, targetSuite);
                    }
                }
            }
            catch { }
        }

        private static void WriteLaunchProperty(StreamWriter writer, string key, string value)
        {
            writer.WriteLine(key + "=" + BitConverter.ToString(Encoding.UTF8.GetBytes(value ?? "")).Replace("-", ""));
        }

        private void LaunchProcess(AccountItem acc)
        {
            if (acc.IsRunning) return;

            settings.DetectPaths();
            string java = ResolveJavaPath(settings.JavaPath);
            string microJar = ResolvePath(settings.EmulatorJar);
            string gameJar = ResolvePath(settings.GameJar);

            if (!File.Exists(java))
            {
                acc.UserStopped = true;
                MessageBox.Show("Chưa tìm thấy Java trên máy. Cài Java hoặc chép Java portable vào thư mục runtime\\bin\\java.exe bên cạnh tool, rồi mở lại.", "Lỗi Java", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }
            if (!File.Exists(microJar))
            {
                acc.UserStopped = true;
                MessageBox.Show("Không tìm thấy trình giả lập:\n" + microJar, "Thiếu tệp", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }
            if (!File.Exists(gameJar))
            {
                acc.UserStopped = true;
                MessageBox.Show("Không tìm thấy tệp game:\n" + gameJar, "Thiếu tệp", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // Prefer javaw.exe to hide terminal/console window
            string javaExe = java;
            if (javaExe.EndsWith("java.exe", StringComparison.OrdinalIgnoreCase))
            {
                string javaw = javaExe.Substring(0, javaExe.Length - 8) + "javaw.exe";
                if (File.Exists(javaw)) javaExe = javaw;
            }

            // Ensure isolated profile directory exists
            string userHome = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string profileDir = Path.Combine(userHome, ".microemulator", acc.Id);
            try
            {
                if (!Directory.Exists(profileDir)) Directory.CreateDirectory(profileDir);
                EnsureProfileConfig(profileDir, gameJar);
                List<string> vmArgs = new List<string>();
                string controlDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "control");
                Directory.CreateDirectory(controlDir);
                acc.ControlFile = Path.Combine(controlDir, Guid.NewGuid().ToString("N") + ".cmd");
                acc.SessionGold = acc.SessionUnmeasured = 0;
                acc.Background = allHidden;
                WriteRuntimeSettings(acc);
                vmArgs.Add("-Dphuc.control=\"" + acc.ControlFile + "\"");
                vmArgs.Add(string.Format("-Duser.home=\"{0}\"", userHome));

                if (acc.CharacterSlot >= -1 && acc.CharacterSlot < 3)
                    vmArgs.Add(string.Format("-Dphuc.slot={0}", acc.CharacterSlot));

                if (acc.Market >= 0 && acc.Market < 3)
                    vmArgs.Add(string.Format("-Dphuc.market={0}", acc.Market));

                if (acc.Gem >= 0 && acc.Gem < 3)
                    vmArgs.Add(string.Format("-Dphuc.gem={0}", acc.Gem));

                if (acc.Animal >= 0 && acc.Animal < 3)
                    vmArgs.Add(string.Format("-Dphuc.animal={0}", acc.Animal));

                if (acc.HpIndex >= 0 && acc.HpIndex < 4)
                    vmArgs.Add(string.Format("-Dphuc.hp={0}", acc.HpIndex));

                vmArgs.Add(string.Format("-Dphuc.autoon={0}", acc.AutoTradeOn ? 1 : 0));
                vmArgs.Add(string.Format("-Dphuc.reconnect={0}", acc.AutoReconnect ? 1 : 0));
                vmArgs.Add(string.Format("-Dphuc.automount={0}", acc.AutoMount ? 1 : 0));

                vmArgs.Add("-Dphuc.autoattack=" + (acc.AutoAttack ? "1" : "0"));
                vmArgs.Add("-Dphuc.autoheal=" + (acc.AutoHeal ? "1" : "0"));
                vmArgs.Add("-Dphuc.autoloot=" + (acc.AutoLoot ? "1" : "0"));
                vmArgs.Add("-Dphuc.healhp=" + Math.Max(1, Math.Min(100, acc.HealHpPercent)));
                vmArgs.Add("-Dphuc.healmp=" + Math.Max(1, Math.Min(100, acc.HealMpPercent)));
                vmArgs.Add("-Dphuc.rotate=" + (acc.RotateMarkets ? "1" : "0"));
                vmArgs.Add("-Dphuc.teleport=" + (acc.Teleport ? "1" : "0"));
                vmArgs.Add("-Dphuc.hud=" + (acc.ShowHud ? "1" : "0"));
                vmArgs.Add("-Dphuc.visual=" + (acc.ShowGuide ? "1" : "0"));
                vmArgs.Add("-Dphuc.fakename=" + (acc.FakeName ? "1" : "0"));
                vmArgs.Add("-Dphuc.silentbuy=" + (acc.SilentBuy ? "1" : "0"));
                vmArgs.Add("-Dphuc.lowdetail=" + (acc.LowDetail ? "1" : "0"));
                vmArgs.Add("-Dphuc.mountname=\"" + (acc.MountName ?? "").Replace("\"", "") + "\"");

                if (!string.IsNullOrEmpty(acc.Server))
                    vmArgs.Add(string.Format("-Dphuc.server=\"{0}\"", acc.Server));

                if (!string.IsNullOrEmpty(acc.Username))
                    vmArgs.Add(string.Format("-Dphuc.user=\"{0}\"", acc.Username));

                string password = acc.ReadPassword();
                string proxyPassword = acc.ProxyEnabled ? acc.ReadProxyPassword() : "";
                acc.ProxyStatus = acc.ProxyEnabled ? "Chờ kết nối game qua proxy" : "Tắt";
                string launcher = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "tools", "manager-launcher.jar");
                if (!File.Exists(launcher)) throw new FileNotFoundException("Thiếu tools/manager-launcher.jar. Hãy build lại bộ tool.");

                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = javaExe,
                    Arguments = "-cp \"" + launcher + ";" + microJar + "\" ManagerLauncher --resizableDevice 300 300 --id \"" + acc.Id + "\" \"" + gameJar + "\"",
                    RedirectStandardInput = true,
                    WorkingDirectory = AppDomain.CurrentDomain.BaseDirectory,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };

                Process p = Process.Start(psi);
                try {
                    foreach (string option in vmArgs) {
                        int eq = option.IndexOf('=');
                        WriteLaunchProperty(p.StandardInput, option.Substring(2, eq - 2), option.Substring(eq + 1).Trim('"'));
                    }
                    WriteLaunchProperty(p.StandardInput, "phuc.pass", password);
                    WriteLaunchProperty(p.StandardInput, "phuc.proxy.enabled", acc.ProxyEnabled ? "1" : "0");
                    if (acc.ProxyEnabled) {
                        WriteLaunchProperty(p.StandardInput, "phuc.proxy.host", acc.ProxyHost);
                        WriteLaunchProperty(p.StandardInput, "phuc.proxy.port", acc.ProxyPort.ToString());
                        WriteLaunchProperty(p.StandardInput, "phuc.proxy.user", acc.ProxyUsername);
                        WriteLaunchProperty(p.StandardInput, "phuc.proxy.pass", proxyPassword);
                    }
                    p.StandardInput.Close();
                } catch { try { p.Kill(); } catch { } throw; }
                if (acc.Process != null) acc.Process.Dispose();
                acc.Process = p;
                acc.HiddenWindows.Clear();
                acc.StartTime = DateTime.Now;
                acc.UserStopped = false;
                acc.NextRestartAt = DateTime.MinValue;
                acc.WindowHandle = IntPtr.Zero;

                lblStatus.Text = string.Format("Đã khởi chạy: {0} (PID: {1})", acc.DisplayName, p.Id);
                dgvAccounts.Refresh();
            }
            catch (Exception ex)
            {
                acc.UserStopped = true;
                MessageBox.Show("Lỗi khi mở bản game:\n" + ex.Message, "Lỗi khởi chạy", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void SetSelectedAuto(bool enabled)
        {
            AccountItem acc = GetSelectedAccount();
            if (acc == null) { lblStatus.Text = "Chọn tài khoản để bật/dừng auto."; return; }
            ApplyAccountAuto(acc, enabled);
        }

        private void ApplyAccountAuto(AccountItem acc, bool enabled)
        {
            try
            {
                if (acc.IsRunning)
                {
                    if (string.IsNullOrEmpty(acc.ControlFile))
                    {
                        lblStatus.Text = "Mở lại game bằng quản lí mới để nhận lệnh auto.";
                        return;
                    }
                    string temp = acc.ControlFile + ".tmp";
                    File.WriteAllText(temp, (enabled ? "1:" : "0:") + Guid.NewGuid().ToString("N"), Encoding.ASCII);
                    if (File.Exists(acc.ControlFile)) File.Replace(temp, acc.ControlFile, null);
                    else File.Move(temp, acc.ControlFile);
                }
                acc.AutoTradeOn = enabled;
                SaveAccounts();
                dgvAccounts.Refresh();
                lblStatus.Text = (acc.IsRunning ? "Đã gửi lệnh " : "Đã lưu cấu hình ")
                    + (enabled ? "bật auto: " : "dừng auto: ") + acc.DisplayName;
            }
            catch (Exception ex)
            {
                MessageBox.Show("Không gửi được lệnh auto: " + ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void StopSelected()
        {
            AccountItem acc = GetSelectedAccount();
            if (acc == null) return;
            StopAccount(acc);
        }

        private void StopAll()
        {
            CancelBatchLaunch();
            int stopped = 0;
            foreach (var acc in accounts)
            {
                bool wasRunning = acc.IsRunning;
                if (StopAccount(acc) && wasRunning) stopped++;
            }
            lblStatus.Text = string.Format("Đã dừng {0} bản game.", stopped);
        }

        private bool StopAccount(AccountItem acc)
        {
            ReadStatistics(acc);
            acc.UserStopped = true;
            acc.NextRestartAt = DateTime.MinValue;
            if (acc.IsRunning)
            {
                try
                {
                    acc.Process.Kill();
                    if (!acc.Process.WaitForExit(1500)) {
                        lblStatus.Text = "Game chưa thoát; hãy thử dừng lại: " + acc.DisplayName;
                        return false;
                    }
                }
                catch (Exception ex) {
                    if (acc.IsRunning) {
                        lblStatus.Text = "Không dừng được game: " + ex.Message;
                        return false;
                    }
                }
            }
            if (acc.Process != null) acc.Process.Dispose();
            acc.Process = null;
            acc.WindowHandle = IntPtr.Zero;
            acc.HiddenWindows.Clear();
            lblStatus.Text = string.Format("Đã dừng tài khoản: {0}", acc.DisplayName);
            dgvAccounts.Refresh();
            SaveAccounts();
            return true;
        }

        private void RestartSelected()
        {
            AccountItem acc = GetSelectedAccount();
            if (acc == null) return;
            if (!StopAccount(acc)) return;
            LaunchProcess(acc);
        }

        private IntPtr AccountWindow(AccountItem acc)
        {
            if (!acc.IsRunning) return IntPtr.Zero;
            uint pid;
            if (acc.WindowHandle != IntPtr.Zero && Win32.IsWindow(acc.WindowHandle)) {
                Win32.GetWindowThreadProcessId(acc.WindowHandle, out pid);
                if (pid == acc.Process.Id) return acc.WindowHandle;
            }
            return acc.WindowHandle = Win32.FindMainWindow(acc.Process.Id);
        }

        private bool ReadStatistics(AccountItem acc)
        {
            if (string.IsNullOrEmpty(acc.ControlFile)) return false;
            try {
                string path = acc.ControlFile + ".stats";
                return File.Exists(path) && acc.ReadTradeSnapshot(File.ReadAllText(path));
            } catch (IOException) { return false; } catch (UnauthorizedAccessException) { return false; }
        }

        private void WriteRuntimeSettings(AccountItem acc)
        {
            if (string.IsNullOrEmpty(acc.ControlFile)) return;
            string target = acc.ControlFile + ".runtime", temp = target + ".tmp";
            File.WriteAllText(temp, (acc.Background ? "1:" : "0:") + Math.Max(0, Math.Min(120, settings.MemoryCleanupMinutes)), Encoding.ASCII);
            if (File.Exists(target)) File.Replace(temp, target, null); else File.Move(temp, target);
        }

        private void SetAllVisible(bool visible)
        {
            allHidden = !visible;
            foreach (var acc in accounts) {
                if (!acc.IsRunning) continue;
                IntPtr handle = AccountWindow(acc);
                acc.Background = !visible;
                ApplyWindowVisibility(acc, visible);
                QueueRuntimeSettings(acc);
            }
            lblStatus.Text = visible ? "Đã hiện tất cả game." : "Đã ẩn tất cả game; auto vẫn chạy nền.";
        }

        private void QueueRuntimeSettings(AccountItem acc)
        {
            ThreadPool.QueueUserWorkItem(delegate {
                try { lock (acc) { WriteRuntimeSettings(acc); } }
                catch (Exception ex) {
                    try { if (!IsDisposed && IsHandleCreated) BeginInvoke(new MethodInvoker(delegate {
                        lblStatus.Text = "Đã gửi lệnh cửa sổ; chưa lưu được chế độ tiết kiệm CPU: " + ex.Message;
                    })); } catch (InvalidOperationException) { }
                }
            });
        }

        private void ApplyWindowVisibility(AccountItem acc, bool visible)
        {
            if (!acc.IsRunning) return;
            int pid = acc.Process.Id;
            if (!visible) {
                foreach (IntPtr h in Win32.VisibleWindows(pid)) {
                    if (!acc.HiddenWindows.Contains(h)) acc.HiddenWindows.Add(h);
                    Win32.ShowWindowAsync(h, 0);
                }
            } else {
                if (acc.WindowHandle != IntPtr.Zero && !acc.HiddenWindows.Contains(acc.WindowHandle)) acc.HiddenWindows.Add(acc.WindowHandle);
                foreach (IntPtr h in acc.HiddenWindows) {
                    uint owner; Win32.GetWindowThreadProcessId(h, out owner);
                    if (owner == pid && Win32.IsWindow(h)) Win32.ShowWindowAsync(h, Win32.SW_RESTORE);
                }
                acc.HiddenWindows.Clear();
            }
        }

        private void TileWindows()
        {
            SetAllVisible(true);
            List<IntPtr> handles = new List<IntPtr>();
            foreach (var acc in accounts)
            {
                if (acc.IsRunning)
                {
                    acc.WindowHandle = AccountWindow(acc);
                    if (acc.WindowHandle != IntPtr.Zero) handles.Add(acc.WindowHandle);
                }
            }

            if (handles.Count == 0)
            {
                MessageBox.Show("Hiện không có bản game nào đang chạy để sắp xếp!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Rectangle screen = Screen.FromControl(this).WorkingArea;
            int count = handles.Count;
            Rectangle[] layout = WindowLayout.Arrange(screen, count);

            for (int i = 0; i < count; i++)
            {
                IntPtr h = handles[i];
                Win32.ShowWindowAsync(h, Win32.SW_RESTORE);
                Rectangle box = layout[i];
                Win32.MoveWindow(h, box.X, box.Y, box.Width, box.Height, true);
            }

            lblStatus.Text = string.Format("Đã xếp sát {0} cửa sổ trên màn hình hiện tại.", count);
        }

        private void OpenRmsFolder()
        {
            AccountItem acc = GetSelectedAccount();
            string userHome = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string folder = acc != null
                ? Path.Combine(userHome, ".microemulator", acc.Id)
                : Path.Combine(userHome, ".microemulator");

            if (!Directory.Exists(folder)) Directory.CreateDirectory(folder);
            Process.Start("explorer.exe", folder);
        }

        private void AddAccount()
        {
            if (accountsLoadFailed) {
                lblStatus.Text = "Chưa thể thêm tài khoản: sửa accounts.json rồi mở lại quản lí để tránh mất dữ liệu.";
                return;
            }
            string newId = AccountItem.NewProfileId();
            AccountItem item = new AccountItem
            {
                Id = newId,
                DisplayName = "Tài khoản " + (accounts.Count + 1),
                Server = "Chiến Thần",
                CharacterSlot = 0,
                Market = 0,
                Gem = 0,
                Animal = 2,
                HpIndex = 1,
                AutoTradeOn = true,
                AutoReconnect = true,
                AutoMount = true,
                AutoRestart = false,
                Notes = "Cổng Địa Ngục - Bò vàng 24v"
            };

            using (AccountEditDialog dlg = new AccountEditDialog(item, true))
            {
                if (dlg.ShowDialog() == DialogResult.OK)
                {
                    accounts.Add(dlg.Account);
                    SaveAccounts();
                    RefreshGrid();
                }
            }
        }

        private void EditSelected(bool toolSettings = false)
        {
            AccountItem acc = GetSelectedAccount();
            if (acc == null) return;

            using (AccountEditDialog dlg = new AccountEditDialog(acc, false))
            {
                if (toolSettings) dlg.ShowToolSettings();
                if (dlg.ShowDialog() == DialogResult.OK)
                {
                    SaveAccounts();
                    RefreshGrid();
                    lblStatus.Text = "Đã lưu cấu hình riêng: " + acc.DisplayName + (acc.IsRunning ? ". Mở lại tab game để áp dụng." : ". Áp dụng ở lần mở game tiếp theo.");
                }
            }
        }

        private void DuplicateSelected()
        {
            AccountItem acc = GetSelectedAccount();
            if (acc == null) return;

            string copyId = AccountItem.NewProfileId();

            AccountItem dup = new AccountItem
            {
                Id = copyId,
                DisplayName = acc.DisplayName + " (Copy)",
                Username = acc.Username,
                PasswordProtected = acc.PasswordProtected,
                Server = acc.Server,
                CharacterSlot = acc.CharacterSlot,
                Market = acc.Market,
                Gem = acc.Gem,
                Animal = acc.Animal,
                HpIndex = acc.HpIndex,
                AutoTradeOn = acc.AutoTradeOn,
                AutoReconnect = acc.AutoReconnect,
                AutoMount = acc.AutoMount,
                AutoRestart = acc.AutoRestart,
                RotateMarkets = acc.RotateMarkets,
                Teleport = acc.Teleport,
                ShowHud = acc.ShowHud,
                ShowGuide = acc.ShowGuide,
                FakeName = acc.FakeName,
                SilentBuy = acc.SilentBuy,
                LowDetail = acc.LowDetail,
                AutoAttack = acc.AutoAttack,
                AutoHeal = acc.AutoHeal,
                AutoLoot = acc.AutoLoot,
                HealHpPercent = acc.HealHpPercent,
                HealMpPercent = acc.HealMpPercent,
                MountName = acc.MountName,
                Notes = acc.Notes
            };

            accounts.Add(dup);
            SaveAccounts();
            RefreshGrid();
            lblStatus.Text = "Đã nhân bản: " + dup.DisplayName;
        }

        private void DeleteSelected()
        {
            AccountItem acc = GetSelectedAccount();
            if (acc == null) return;

            DialogResult r = MessageBox.Show(
                string.Format("Bạn có chắc chắn muốn xóa tài khoản '{0}' khỏi danh sách?", acc.DisplayName),
                "Xác nhận xóa", MessageBoxButtons.YesNo, MessageBoxIcon.Question);

            if (r == DialogResult.Yes)
            {
                if (!StopAccount(acc)) return;
                accounts.Remove(acc);
                SaveAccounts();
                RefreshGrid();
                lblStatus.Text = "Đã xóa tài khoản: " + acc.DisplayName;
            }
        }

        private void OpenSettings()
        {
            using (SettingsDialog dlg = new SettingsDialog(settings))
            {
                if (dlg.ShowDialog() == DialogResult.OK)
                {
                    lblStatus.Text = "Đã lưu cài đặt mới.";
                    foreach (var acc in accounts) if (acc.IsRunning) QueueRuntimeSettings(acc);
                }
            }
        }

        private void RefreshGrid()
        {
            BindingList<AccountItem> list = new BindingList<AccountItem>(accounts);
            dgvAccounts.DataSource = list;
            dgvAccounts.Refresh();
        }

        private void LoadAccounts()
        {
            try
            {
                if (File.Exists(AccountsFile))
                {
                    string json = File.ReadAllText(AccountsFile, Encoding.UTF8);
                    JavaScriptSerializer jss = new JavaScriptSerializer();
                    List<AccountItem> list = jss.Deserialize<List<AccountItem>>(json);
                    if (list != null && !list.Contains(null))
                    {
                        accounts = list;
                        AccountItem.EnsureUniqueProfiles(accounts);
                        SaveAccounts(); // Migrate legacy plaintext passwords to Windows-protected storage.
                        RefreshGrid();
                        return;
                    }
                    throw new InvalidDataException("Danh sách tài khoản không hợp lệ.");
                }
            }
            catch (Exception ex) {
                accountsLoadFailed = true;
                accounts = new List<AccountItem>();
                RefreshGrid();
                lblStatus.Text = "Không đọc được accounts.json; giữ nguyên file. Sửa file rồi mở lại quản lí. " + ex.Message;
                return;
            }

            // Default sample accounts
            accounts = new List<AccountItem>
            {
                new AccountItem
                {
                    Id = "acc1",
                    DisplayName = "Nick 1 - Cổng Địa Ngục",
                    Username = "",
                    Password = "",
                    Server = "Chiến Thần",
                    CharacterSlot = 0,
                    Market = 0,
                    Gem = 0,
                    Animal = 2,
                    HpIndex = 1,
                    AutoTradeOn = true,
                    AutoReconnect = true,
                    AutoMount = true,
                    AutoRestart = false,
                    Notes = "Bò vàng 24v - Đá Kim Cương"
                },
                new AccountItem
                {
                    Id = "acc2",
                    DisplayName = "Nick 2 - Cửa Biển",
                    Username = "",
                    Password = "",
                    Server = "Chiến Thần",
                    CharacterSlot = 0,
                    Market = 1,
                    Gem = 1,
                    Animal = 1,
                    HpIndex = 1,
                    AutoTradeOn = true,
                    AutoReconnect = true,
                    AutoMount = true,
                    AutoRestart = false,
                    Notes = "Lạc đà vàng 12v - Đá Rubi"
                }
            };
            SaveAccounts();
            RefreshGrid();
        }

        private void SaveAccounts()
        {
            if (accountsLoadFailed) return;
            try
            {
                JavaScriptSerializer jss = new JavaScriptSerializer();
                string json = jss.Serialize(accounts);
                AtomicFile.Write(AccountsFile, FormatJson(json));
            }
            catch (Exception ex) { lblStatus.Text = "Không lưu được tài khoản: " + ex.Message; }
        }

        private static string FormatJson(string json)
        {
            if (string.IsNullOrEmpty(json)) return "";
            StringBuilder sb = new StringBuilder();
            bool inQuote = false;
            int indent = 0;
            for (int i = 0; i < json.Length; i++)
            {
                char ch = json[i];
                if (ch == '\"')
                {
                    bool escaped = false;
                    int j = i - 1;
                    while (j >= 0 && json[j] == '\\') { escaped = !escaped; j--; }
                    if (!escaped) inQuote = !inQuote;
                    sb.Append(ch);
                }
                else if (!inQuote)
                {
                    if (ch == '{' || ch == '[')
                    {
                        sb.Append(ch);
                        sb.AppendLine();
                        sb.Append(new string(' ', ++indent * 2));
                    }
                    else if (ch == '}' || ch == ']')
                    {
                        sb.AppendLine();
                        sb.Append(new string(' ', --indent * 2));
                        sb.Append(ch);
                    }
                    else if (ch == ',')
                    {
                        sb.Append(ch);
                        sb.AppendLine();
                        sb.Append(new string(' ', indent * 2));
                    }
                    else if (ch == ':')
                    {
                        sb.Append(": ");
                    }
                    else if (!char.IsWhiteSpace(ch))
                    {
                        sb.Append(ch);
                    }
                }
                else
                {
                    sb.Append(ch);
                }
            }
            return sb.ToString();
        }

        private string ResolveJavaPath(string path)
        {
            if (string.IsNullOrEmpty(path)) return "";
            if (Path.IsPathRooted(path)) return path;
            string local = ResolvePath(path);
            if (File.Exists(local)) return local;
            foreach (string folder in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
            {
                try
                {
                    string candidate = Path.Combine(folder.Trim().Trim('"'), path);
                    if (File.Exists(candidate)) return candidate;
                }
                catch (ArgumentException) { }
            }
            return path;
        }

        private string ResolvePath(string path)
        {
            if (string.IsNullOrEmpty(path)) return "";
            if (Path.IsPathRooted(path)) return path;
            return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, path);
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            int running = 0;
            foreach (var a in accounts) if (a.IsRunning) running++;

            if (running > 0)
            {
                DialogResult r = MessageBox.Show(
                    string.Format("Đang có {0} bản game đang chạy.\nBạn có muốn đóng tất cả bản game trước khi thoát không?", running),
                    "Xác nhận thoát", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question);

                if (r == DialogResult.Cancel)
                {
                    e.Cancel = true;
                    return;
                }
                if (r == DialogResult.Yes)
                {
                    StopAll();
                }
                else SetAllVisible(true); // Do not leave inaccessible hidden games after manager exits.
            }

            CancelBatchLaunch();
            updateTimer.Stop();
            SaveAccounts();
            base.OnFormClosing(e);
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing) {
                CancelBatchLaunch();
                if (updateTimer != null) updateTimer.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm(args.Length > 0 ? args[0] : null));
        }
    }
}
