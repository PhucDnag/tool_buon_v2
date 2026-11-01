package autotrade;
import CLib.mVector;
import GameObjects.MainObject;

/** Match the visible world objects, never a cached or stopped animal. */
public final class OwnedAnimal {
    public static MainObject find(MainObject player,mVector objects) {
        if (player==null || player.name==null || player.name.length()==0 || objects==null) return null;
        MainObject found=null;
        for (int i=0;i<objects.size();i++) {
            MainObject candidate=(MainObject)objects.elementAt(i);
            if (candidate==null || candidate.isRemove || candidate.isStop || candidate.hp<=0
                    || !candidate.isMonstervantieu() || !player.name.equals(candidate.getnameOwner())) continue;
            // During replacement/map sync, do not follow an arbitrary duplicate.
            if (found!=null && found!=candidate) return null;
            found=candidate;
        }
        return found;
    }
}
