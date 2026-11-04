package autotrade;
public final class LowDetail {
    public static boolean walkable(int[] cells,int width,int height,int x,int y) {
        if (cells==null || width<=0 || height<=0 || (long)width*height>cells.length
                || x<0 || y<0 || x>=width || y>=height) return false;
        int cell=cells[y*width+x]; return cell!=1 && cell!=-1;
    }
    public static boolean showObject(boolean self,boolean npc,int type) { return self || npc || type!=0; }
}
