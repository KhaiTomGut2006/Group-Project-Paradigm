package Project3_6813107;
public class MapTime { public static void main(String[] a){ for (GameData.MapType t: GameData.MapType.values()){ long s=System.nanoTime(); GameMap m=new GameMap(t,t.seed); System.out.println(t+" "+(System.nanoTime()-s)/1000000+"ms "+m.getWidthPx()+"x"+m.getHeightPx()+" props="+m.getProps().size()); } } }
