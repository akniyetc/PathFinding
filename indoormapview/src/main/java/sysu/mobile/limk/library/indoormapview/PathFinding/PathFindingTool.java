package sysu.mobile.limk.library.indoormapview.PathFinding;

import java.util.List;

/**
 * Created by Clement on 30/01/2018.
 */

public class PathFindingTool {

    Map<ExampleNode> myMap;

    public PathFindingTool(int tailleMapX, int tailleMapY) {
        myMap = new Map<ExampleNode>(tailleMapX, tailleMapY, new ExampleFactory());
    }

    public void setBlock(int x, int y, boolean block) {
        myMap.setWalkable(x, y, !block);
    }

    public List<ExampleNode> findPath(int departX, int departY, int arriveeX, int arriveeY) {
        return myMap.findPath(departX, departY, arriveeX, arriveeY);
    }
}

