package autotrade;

/** Unknown is deliberately different from zero. Never infer stock from prices. */
public final class TradeStock {
    public final int[] count = {-1, -1, -1};
    public boolean pearls;

    public static String normalize(String input) {
        if (input == null) return "";
        String s = input.toLowerCase();
        String[] groups = {"àáạảãâầấậẩẫăằắặẳẵ", "èéẹẻẽêềếệểễ", "ìíịỉĩ", "òóọỏõôồốộổỗơờớợởỡ", "ùúụủũưừứựửữ", "ỳýỵỷỹ", "đ"};
        String replacement = "aeiouyd";
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x300 && c <= 0x36f) continue;
            for (int j = 0; j < groups.length; j++) {
                if (groups[j].indexOf(c) >= 0) { c = replacement.charAt(j); break; }
            }
            if (space(c)) {
                if (out.length()>0 && out.charAt(out.length()-1)!=' ') out.append(' ');
            } else out.append(c);
        }
        return out.toString().trim();
    }

    public static TradeStock parse(String text) {
        TradeStock stock = new TradeStock();
        String s = normalize(text);
        stock.pearls = s.indexOf("ngoc trai") >= 0;
        String[][] names = {{"kim cuong"}, {"rubi", "ruby", "riuby"}, {"saphia", "sapphire"}};
        boolean stockSentence = s.indexOf("ta chi con") >= 0 || s.indexOf("ta con") >= 0 || s.indexOf("ton kho") >= 0;
        boolean mentionsGem = false;
        for (int g = 0; g < names.length; g++) {
            int p = -1;
            String label = "";
            for (int a = 0; a < names[g].length; a++) {
                int found = wordIndex(s, names[g][a]);
                if (found >= 0 && (p < 0 || found < p)) { p = found; label = names[g][a]; }
            }
            if (p < 0) continue;
            mentionsGem = true;
            if (stockSentence) {
                int before = quantityBefore(s, p);
                if (before >= 0) { stock.count[g] = before; continue; }
            }
            int end = s.length();
            String[] boundaries = {"kim cuong", "rubi", "ruby", "riuby", "saphia", "sapphire", "ngoc trai", "\n", ";"};
            for (int b = 0; b < boundaries.length; b++) {
                int q = s.indexOf(boundaries[b], p + label.length());
                if (q >= 0 && q < end) end = q;
            }
            String tail = s.substring(p + label.length(), end).trim();
            // A quantity belonging to the next entry must not become this gem's stock.
            if (stockSentence && !tail.startsWith(":") && !tail.startsWith("=") && !tail.startsWith("con ")
                    && !tail.startsWith("so luong:") && !tail.startsWith("het hang") && !tail.startsWith("da het")) continue;
            if (tail.startsWith(":") || tail.startsWith("=")) tail = tail.substring(1).trim();
            if (tail.startsWith("con ")) tail = tail.substring(4).trim();
            if (tail.startsWith("so luong:")) tail = tail.substring(9).trim();
            if (tail.startsWith("het hang") || tail.startsWith("da het")) { stock.count[g] = 0; continue; }
            int i = 0;
            long amount = 0;
            while (i < tail.length() && tail.charAt(i) >= '0' && tail.charAt(i) <= '9') {
                amount = amount * 10 + tail.charAt(i++) - '0';
                if (amount > Integer.MAX_VALUE) break;
            }
            if (i == 0 || amount > Integer.MAX_VALUE) continue;
            String rest = tail.substring(i).trim();
            if (rest.length() == 0 || rest.equals(",") || rest.startsWith("vien") || rest.startsWith("cai")) stock.count[g] = (int)amount;
        }
        int pearlIndex = wordIndex(s, "ngoc trai");
        boolean onlyCountedPearls = s.indexOf("ta chi con") >= 0 && pearlIndex >= 0 && quantityBefore(s, pearlIndex, false) >= 0;
        if (stock.choose(0) < 0 && !mentionsGem && (s.indexOf("het sach da quy") >= 0 || s.indexOf("chi con ngoc trai") >= 0 || onlyCountedPearls
                || s.indexOf("ta da het hang")>=0 || s.indexOf("ta het hang roi")>=0
                || s.indexOf("ta khong con hang")>=0 || s.indexOf("ta khong con da quy")>=0)) {
            for (int i = 0; i < 3; i++) stock.count[i] = 0;
        }
        return stock;
    }

    private static int wordIndex(String text, String word) {
        int from = 0;
        while (from < text.length()) {
            int p = text.indexOf(word, from);
            if (p < 0) return -1;
            int end = p + word.length();
            if ((p == 0 || !letter(text.charAt(p - 1))) && (end == text.length() || !letter(text.charAt(end)))) return p;
            from = end;
        }
        return -1;
    }

    private static boolean letter(char c) { return c >= 'a' && c <= 'z'; }
    private static boolean space(char c) { return c <= ' ' || c == '\u00a0'; }

    /** Parses the complete integer directly before "vien <gem>", never a price. */
    private static int quantityBefore(String text, int gemStart) {
        return quantityBefore(text,gemStart,true);
    }
    private static int quantityBefore(String text, int gemStart,boolean unitRequired) {
        int end = gemStart;
        while (end > 0 && space(text.charAt(end - 1))) end--;
        if (end>=4 && text.substring(end-4,end).equals("vien")) {
            end-=4;
            if (end==0 || !space(text.charAt(end-1))) return -1;
        } else if (unitRequired) return -1;
        while (end > 0 && space(text.charAt(end - 1))) end--;
        int start = end;
        while (start > 0 && text.charAt(start - 1) >= '0' && text.charAt(start - 1) <= '9') start--;
        if (start == end) return -1;
        if (start > 0) {
            char previous = text.charAt(start - 1);
            if (!space(previous) && previous != ':' && previous != '(') return -1;
        }
        long amount = 0;
        for (int i = start; i < end; i++) {
            amount = amount * 10 + text.charAt(i) - '0';
            if (amount > Integer.MAX_VALUE) return -1;
        }
        return (int)amount;
    }

    public int choose(int preferred) {
        int first=Math.max(0,Math.min(2,preferred));
        for (int i=first;i<3;i++) if (count[i]>0) return i;
        return -1;
    }

    public boolean exhausted(int preferred) {
        int first=Math.max(0,Math.min(2,preferred));
        for (int i=first;i<3;i++) if (count[i]!=0) return false;
        return true;
    }
    public boolean empty() { return exhausted(0); }
}
