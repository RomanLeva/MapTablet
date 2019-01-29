package data;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Pair;
import maps.LinesLayer;
import maps.BaseMap;
import maps.MapLayer;
import maps.PointsLayer;

import java.util.*;

public class PoiLayersData {
    private PointsLayer targetPointsLayer, weaponPointsLayer, triangPointsLayer, marksPointsLayer, missedPointsLayer, tempPointLayer, myPosPointLayer;
    private LinesLayer linesLayer;
    private final List<MapLayer> layers = new LinkedList<>();
    private Pair<MapPoint, Node> focusedPair = new Pair<>(null, null);
    private Line focusedLine = new Line();
    private ArrayList<MapPoint> downloadCornerPoints = new ArrayList<>(2);
    private ArrayList<MapPoint> lineStartEndPoints = new ArrayList<>(2);
    public Rectangle rectangle = new Rectangle();
    private Map<MapPoint, List<Double>> weaponsAdjustmentsMap = new HashMap<>();

    public PoiLayersData(BaseMap baseMap) {
        targetPointsLayer = new PointsLayer();
        weaponPointsLayer = new PointsLayer();
        triangPointsLayer = new PointsLayer();
        marksPointsLayer = new PointsLayer();
        missedPointsLayer = new PointsLayer();
        tempPointLayer = new PointsLayer();
        myPosPointLayer = new PointsLayer();
        linesLayer = new LinesLayer();
        targetPointsLayer.setBaseMap(baseMap);
        weaponPointsLayer.setBaseMap(baseMap);
        triangPointsLayer.setBaseMap(baseMap);
        marksPointsLayer.setBaseMap(baseMap);
        missedPointsLayer.setBaseMap(baseMap);
        tempPointLayer.setBaseMap(baseMap);
        myPosPointLayer.setBaseMap(baseMap);
        linesLayer.setBaseMap(baseMap);
        layers.add(targetPointsLayer);
        layers.add(weaponPointsLayer);
        layers.add(triangPointsLayer);
        layers.add(marksPointsLayer);
        layers.add(missedPointsLayer);
        layers.add(tempPointLayer);
        layers.add(myPosPointLayer);
        layers.add(linesLayer);
        rectangle.setFill(null); // transparent
        rectangle.setStroke(Color.BLACK); // border
        rectangle.getStrokeDashArray().add(5.0);
    }

    public PointsLayer getTargetPointsLayer() {
        return targetPointsLayer;
    }

    public PointsLayer getTriangPointsLayer() {
        return triangPointsLayer;
    }

    public PointsLayer getMarksPointsLayer() {
        return marksPointsLayer;
    }

    public PointsLayer getWeaponPointsLayer() {
        return weaponPointsLayer;
    }

    public PointsLayer getMissedPointsLayer() {
        return missedPointsLayer;
    }

    public PointsLayer getTempPointLayer() {
        return tempPointLayer;
    }

    public PointsLayer getMyPosPointLayer() {
        return myPosPointLayer;
    }

    public LinesLayer getLinesLayer() {
        return linesLayer;
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

    public ArrayList<MapPoint> getLineStartEndPoints() {
        return lineStartEndPoints;
    }

    public void setLineStartEndPoints(ArrayList<MapPoint> lineStartEndPoints) {
        this.lineStartEndPoints = lineStartEndPoints;
    }

    public Line getFocusedLine() {
        return focusedLine;
    }

    public void setFocusedLine(Line focusedLine) {
        this.focusedLine = focusedLine;
    }

    public Map<MapPoint, List<Double>> getWeaponsAdjustmentsMap() {
        return weaponsAdjustmentsMap;
    }
}
