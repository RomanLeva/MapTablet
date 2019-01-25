package data;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Pair;
import maps.BaseMap;
import maps.MapLayer;
import maps.PoiLayer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PoiLayersData {
    private PoiLayer targetPointsLayer, triangPointsLayer, missedPointsLayer, tempPointLayer, myPosPointLayer;
    private final List<MapLayer> layers = new LinkedList<>();
    private Pair<MapPoint, Node> focusedPair = new Pair<>(null, null);
    private ArrayList<MapPoint> downloadCornerPoints = new ArrayList<>(2);
    public Rectangle rectangle = new Rectangle();

    public PoiLayersData(BaseMap baseMap) {
        targetPointsLayer = new PoiLayer();
        triangPointsLayer = new PoiLayer();
        missedPointsLayer = new PoiLayer();
        tempPointLayer = new PoiLayer();
        myPosPointLayer = new PoiLayer();
        targetPointsLayer.setBaseMap(baseMap);
        triangPointsLayer.setBaseMap(baseMap);
        missedPointsLayer.setBaseMap(baseMap);
        tempPointLayer.setBaseMap(baseMap);
        myPosPointLayer.setBaseMap(baseMap);
        layers.add(targetPointsLayer);
        layers.add(triangPointsLayer);
        layers.add(missedPointsLayer);
        layers.add(tempPointLayer);
        layers.add(myPosPointLayer);
        rectangle.setFill(null); // transparent
        rectangle.setStroke(Color.BLACK); // border
        rectangle.getStrokeDashArray().add(5.0);
    }

    public PoiLayer getTargetPointsLayer() {
        return targetPointsLayer;
    }

    public PoiLayer getTriangPointsLayer() {
        return triangPointsLayer;
    }

    public PoiLayer getMissedPointsLayer() {
        return missedPointsLayer;
    }

    public PoiLayer getTempPointLayer() {
        return tempPointLayer;
    }

    public PoiLayer getMyPosPointLayer() {
        return myPosPointLayer;
    }

    public List<MapLayer> getLayers() {
        return layers;
    }

    public Pair getFocusedPair() {
        return focusedPair;
    }

    public void setFocusedPair(Pair focusedPair) {
        this.focusedPair = focusedPair;
    }

    public ArrayList<MapPoint> getDownloadCornerPoints() {
        return downloadCornerPoints;
    }
}
