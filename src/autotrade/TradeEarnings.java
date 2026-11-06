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
        for (int end=1; end<s.length(); end++) {
        int currencyLength=s.startsWith(" vang",end) ? 5 : s.startsWith(" xu",end) ? 3 : s.startsWith(" coin",end) ? 5 : 0;
        if (currencyLength==0) continue;
        int after=end+currencyLength;
        if (after<s.length() && Character.isLetter(s.charAt(after))) continue;
        int start=end;
        while (start>0) {
            char c=s.charAt(start-1);
            if ((c>='0' && c<='9') || c=='.' || c==',') start--; else break;
        }
        if (start==end) continue;
        String number=s.substring(start,end);
        // Only whole numbers or well-formed thousands groups are unambiguous.
        if (!number.matches("[0-9]+|[0-9]{1,3}([.,][0-9]{3})+")) return -1;
        try { return Long.parseLong(number.replaceAll("[.,]","")); }
        catch (Exception ignored) { return -1; }
        }
        return -1;
    }
}
