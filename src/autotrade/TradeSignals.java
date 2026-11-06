package autotrade;

/** Pure matching rules, shared by live adapter and tests. */
public final class TradeSignals {
    public static boolean animalNotFound(String text) {
        String s=TradeStock.normalize(text).replace('\n',' ').replace('\r',' ');
        while (s.indexOf("  ")>=0) s=s.replaceAll("  "," ");
        return s.indexOf("khong thay")>=0
                && (s.indexOf("con vat buon")>=0 || s.indexOf("thu buon")>=0)
                && s.indexOf("co muon")<0;
    }
    public static boolean replaceAnimalConfirmation(String text) {
        String s=TradeStock.normalize(text).replace('\n',' ').replace('\r',' ').replace('\u00a0',' ');
        while (s.indexOf("  ")>=0) s=s.replaceAll("  "," ");
        return s.indexOf("con vat buon")>=0 && s.indexOf("co muon huy")>=0
                && s.indexOf("thu cu")>=0 && s.indexOf("tao con thu moi")>=0;
    }
    public static boolean animalUseConfirmation(String text) {
        String s=TradeStock.normalize(text);
        return s.indexOf("co muon su dung")>=0 && s.indexOf("thoi gian")>=0
                && s.indexOf("di buon")>=0 && s.indexOf("con lai")>=0;
    }
    public static boolean startTradeConfirmation(String text) {
        String raw=TradeStock.normalize(text); StringBuffer compact=new StringBuffer();
        boolean space=false;
        for (int i=0;i<raw.length();i++) {
            char c=raw.charAt(i);
            if (c<=' ' || c=='\u00a0') { if (!space) compact.append(' '); space=true; }
            else { compact.append(c); space=false; }
        }
        String s=compact.toString();
        return contains(s,new String[]{"chuyen di buon","chuyen buon"})
                && s.indexOf("hoan thanh")>=0 && s.indexOf("thoi han")>=0
                && s.indexOf("da quy")>=0 && s.indexOf("co muon di")>=0;
    }

    public static boolean contains(String text, String[] words) {
        for (int i = 0; i < words.length; i++) if (text.indexOf(words[i]) >= 0) return true;
        return false;
    }

    public static boolean full(String text) {
        String s = TradeStock.normalize(text);
        return s.equals("khong the mua") || s.equals("khong the mua!") || s.equals("khong the mua.") || contains(s, new String[]{
            "cho da day", "cho day hang", "thu da day", "khong the mua them",
            "khong the cho them", "vuot qua suc cho", "day hang", "da day",
            "suc chua toi da", "day roi"
        });
    }

    public static boolean depleted(String text) {
        String s = TradeStock.normalize(text);
        return contains(s, new String[]{"het hang", "het da quy", "khong con hang", "khong con da quy", "da het hang", "da het", "vat pham da het"}) && s.indexOf("chua het") < 0;
    }

    public static boolean tooFast(String text) {
        if (text == null) return false;
        String s = TradeStock.normalize(text);
        return contains(s, new String[]{
            "thao tac qua nhanh", "qua nhanh", "xin cho vai giay", "xin cho giay lat", "vui long cho vai giay"
        });
    }

    public static int cargo(String text) {
        String s = TradeStock.normalize(text);
        String[] labels = {
            "dang cho", "so hang:", "so hang", "so luong hang:", "so luong hang",
            "so luong:", "so luong", "hang hoa:", "hang hoa", "suc cho:", "suc cho",
            "suc chua:", "suc chua", "cho hang:", "cho hang", "hang tren thu:", "hang tren thu",
            "cho:", "hang:"
        };
        for (int i = 0; i < labels.length; i++) {
            int p = s.indexOf(labels[i]);
            if (p < 0) continue;
            p += labels[i].length();
            while (p < s.length() && (s.charAt(p) == ' ' || s.charAt(p) == ':')) p++;
            int start = p;
            long value = 0;
            while (p < s.length() && s.charAt(p) >= '0' && s.charAt(p) <= '9') {
                value = value * 10 + s.charAt(p++) - '0';
                if (value > Integer.MAX_VALUE) return -1;
            }
            if (p > start && (p == s.length() || s.charAt(p) == ' ' || s.charAt(p) == '/' || s.charAt(p) == '\n' || s.charAt(p) == ')')) {
                return (int)value;
            }
        }
        int v = s.indexOf("vien");
        if (v > 0) {
            int end = v;
            while (end > 0 && s.charAt(end - 1) == ' ') end--;
            int start = end;
            while (start > 0 && s.charAt(start - 1) >= '0' && s.charAt(start - 1) <= '9') start--;
            if (start < end) {
                String before = s.substring(0, start).trim();
                if (before.indexOf("hp") < 0 && before.indexOf("mp") < 0 && before.indexOf("gia") < 0) {
                    long val = 0;
                    for (int i = start; i < end; i++) val = val * 10 + s.charAt(i) - '0';
                    if (val <= Integer.MAX_VALUE) return (int)val;
                }
            }
        }
        int slash = s.indexOf('/');
        if (slash > 0) {
            int end = slash;
            while (end > 0 && s.charAt(end - 1) == ' ') end--;
            int start = end;
            while (start > 0 && s.charAt(start - 1) >= '0' && s.charAt(start - 1) <= '9') start--;
            if (start < end) {
                String before = s.substring(0, start).trim();
                if (before.indexOf("hp") < 0 && before.indexOf("mp") < 0) {
                    long val = 0;
                    for (int i = start; i < end; i++) val = val * 10 + s.charAt(i) - '0';
                    if (val <= Integer.MAX_VALUE) return (int)val;
                }
            }
        }
        if (contains(s, new String[]{"chua co hang", "khong cho hang", "thu chua cho hang", "0 vien", "0/"})) return 0;
        return -1;
    }

    public static boolean gem(String text, int gem) {
        String s = TradeStock.normalize(text);
        if (s.indexOf("ngoc trai") >= 0) return false;
        return gem == 0 ? s.indexOf("kim cuong") >= 0 : gem == 1 ? contains(s, new String[]{"rubi", "ruby", "riuby"}) : contains(s, new String[]{"saphia", "sapphire"});
    }

    public static boolean animal(String text, int animal) {
        String s = TradeStock.normalize(text);
        if (s.indexOf("trang") >= 0 || s.indexOf("den") >= 0) return false;
        if (s.indexOf(TradeStock.normalize(TradeConfig.ANIMALS[animal])) >= 0) return true;
        if (animal == 0) return s.indexOf("lua") >= 0 && (s.indexOf("vang") >= 0 || s.indexOf("buon") >= 0);
        if (animal == 1) return s.indexOf("lac da") >= 0 && (s.indexOf("vang") >= 0 || s.indexOf("buon") >= 0);
        if (animal == 2) return (s.indexOf("bo") >= 0 || s.indexOf("bo rung") >= 0) && (s.indexOf("vang") >= 0 || s.indexOf("buon") >= 0);
        return false;
    }

    public static boolean sell(String s) {
        if (s == null) return false;
        String norm = TradeStock.normalize(s);
        return contains(norm, new String[]{
            "ban hang", "ban da quy", "ban toan bo hang", "ban tat ca hang",
            "giao hang", "tra hang", "ban hang hoa", "ban toan bo", "ban tat ca",
            "muon ban", "ban voi gia"
        }) || (norm.indexOf("ban") >= 0 && (norm.indexOf("vang") >= 0 || norm.indexOf("gia") >= 0));
    }

    public static boolean recall(String s) {
        return contains(s, new String[]{"thu hoi thu", "cat thu", "thu thu buon", "cat thu buon", "huy thu", "huy con", "huy chuyen di buon"});
    }

    public static boolean bought(String text) {
        if (text == null) return false;
        String s = TradeStock.normalize(text);
        if (contains(s, new String[]{"mua thanh cong", "da mua thanh cong", "mua thanh cong vat pham", "giao dich thanh cong"})) return true;
        if (s.indexOf("thanh cong") >= 0 && (s.indexOf("mua") >= 0 || s.indexOf("kim cuong") >= 0 || s.indexOf("rubi") >= 0 || s.indexOf("ruby") >= 0 || s.indexOf("saphia") >= 0)) return true;
        if (s.indexOf("nhan duoc") >= 0 && (s.indexOf("kim cuong") >= 0 || s.indexOf("rubi") >= 0 || s.indexOf("ruby") >= 0 || s.indexOf("saphia") >= 0 || s.indexOf("da quy") >= 0)) return true;
        if (s.indexOf("da mua") >= 0 && (s.indexOf("kim cuong") >= 0 || s.indexOf("rubi") >= 0 || s.indexOf("ruby") >= 0 || s.indexOf("saphia") >= 0)) return true;
        return false;
    }

    public static boolean sold(String s) {
        return contains(s, new String[]{
            "ban hang thanh cong", "ban thanh cong", "da ban het hang", "ban het hang",
            "giao hang thanh cong", "nhan duoc", "ban duoc"
        });
    }

    public static boolean isConfirmButton(String caption) {
        if (caption == null) return false;
        String s = TradeStock.normalize(caption).trim();
        return s.equals("ok") || s.equals("dong y") || s.equals("xac nhan") || s.equals("co")
                || s.equals("chap nhan") || s.equals("mua") || s.equals("ban") || s.equals("yes")
                || s.equals("nhan");
    }
}
