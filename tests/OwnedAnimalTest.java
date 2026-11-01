import autotrade.OwnedAnimal;
import CLib.mVector;
import GameObjects.MainObject;
public final class OwnedAnimalTest {
    static void check(boolean ok) { if(!ok) throw new AssertionError("Owned animal selection"); }
    static MainObject beast(String owner,int x) { MainObject b=new MainObject(); b.tradeAnimal=true; b.owner=owner; b.x=x; return b; }
    public static void main(String[] args) {
        MainObject player=new MainObject(); player.name="player"; player.x=100;
        MainObject ghost=beast("player",900), nearby=beast("player",110), other=beast("other",101);
        ghost.isStop=true;
        mVector objects=new mVector(); objects.addElement(ghost); objects.addElement(other); objects.addElement(nearby);
        check(OwnedAnimal.find(player,objects)==nearby);
        check(MainObject.getDistance(player.x,player.y,OwnedAnimal.find(player,objects).x,nearby.y)==10);
        ghost.isStop=false; ghost.isRemove=true; check(OwnedAnimal.find(player,objects)==nearby);
        ghost.isRemove=false; ghost.hp=0; check(OwnedAnimal.find(player,objects)==nearby);
        ghost.hp=100; check(OwnedAnimal.find(player,objects)==null);
        objects.removeAllElements(); objects.addElement(other); check(OwnedAnimal.find(player,objects)==null);
        objects.removeAllElements(); check(OwnedAnimal.find(player,objects)==null);
        System.out.println("PASS stopped/removed/dead ghosts, owner isolation, duplicate and map-clear selection");
    }
}
