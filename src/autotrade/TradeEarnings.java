package autotrade;

/** Gross sale receipts only. Never infer income from unrelated wallet changes. */
public final class TradeEarnings {
    public long gold, unmeasured;
    public void complete(long amount) {
        if (amount>=0 && gold<=Long.MAX_VALUE-amount) gold+=amount;
        else unmeasured++;
    }
    public static long amount(String text) {
        String s=TradeStock.normalize(text);
        int end=s.indexOf(" vang");
        if (end<0) end=s.indexOf(" xu");
        if (end<0) end=s.indexOf(" coin");
        if (end<0) return -1;
        int start=end;
        while (start>0) {
            char c=s.charAt(start-1);
            if ((c>='0' && c<='9') || c=='.' || c==',') start--; else break;
        }
        if (start==end) return -1;
        String number=s.substring(start,end);
        // Only whole numbers or well-formed thousands groups are unambiguous.
        if (!number.matches("[0-9]+|[0-9]{1,3}([.,][0-9]{3})+")) return -1;
        try { return Long.parseLong(number.replaceAll("[.,]","")); }
        catch (Exception ignored) { return -1; }
    }
}
