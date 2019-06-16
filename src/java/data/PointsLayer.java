package data;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.util.Pair;
import maps.MapLayer;

import java.util.LinkedList;
import java.util.Optional;

public class PointsLayer extends MapLayer {
    private LinkedList<Pair<MapPoint, Node>> points = new LinkedList<>();
    private static Pair<MapPoint, Node> lastUsedPoint;

    public void addPoint(MapPoint p, Node icon) {
        Pair<MapPoint, Node> np = new Pair<>(p, icon);
        points.addFirst(np);
        lastUsedPoint = np;
        this.getChildren().add(icon);
        this.markDirty();
    }

    /**
     * Method used only in temporary layer with one point
     */
    public void deleteTempPoint() {
        //selects only exactly tempPoint from all Nodes in Region
        try {
            if (points.isEmpty()) return;
            Optional<Node> op = this.getChildren().stream().filter(node -> node == points.get(0).getValue()).findFirst();
            op.ifPresent(node -> node.setVisible(false));
            points.clear();
            markDirty();
        } catch (NullPointerException e) {
        }
    }

    /**
     * Rewrite points when screen is moving, method is invoked in parent MapLayer class by JFX animation pulse
     */
    @Override
    public void layoutLayer() {
        for (Pair<MapPoint, Node> p : points) {
            MapPoint point = p.getKey();
            Node icon = p.getValue();
            Point2D mapPoint = baseMap.getMapPointFromDegreesToXY(point.getLatitude(), point.getLongitude());
            icon.setVisible(true);
            // Set point shifting
            icon.setTranslateX(mapPoint.getX());
            icon.setTranslateY(mapPoint.getY());
        }
    }

    public LinkedList<Pair<MapPoint, Node>> getPoints() {
        return points;
    }

    public Pair<MapPoint, Node> getLastUsedPoint() {
        return lastUsedPoint;
    }

    public void setLastUsedPoint(Pair<MapPoint, Node> pair) {
        lastUsedPoint = pair;
    }
}