package maps;
import data.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.util.Pair;

import java.util.LinkedList;
import java.util.Optional;

/**
 * A layer that allows to visualise points of interest.
 */
public class PoiLayer extends MapLayer {
    private LinkedList<Pair<MapPoint, Node>> points = new LinkedList<>();
    private static Pair<MapPoint, Node> lastUsedPoint;

    public void addPoint(MapPoint p, Node icon) {
        Pair<MapPoint, Node> np = new Pair<>(p, icon);
        points.addFirst(np);
        lastUsedPoint = np;
        this.getChildren().add(icon);
        this.markDirty();
    }

    //  Method used only in temporary layer with one point
    public void deleteTempPoint() {
        //selects only exactly tempPoint from all Nodes in Region
        try {
            Optional<Node> op = this.getChildren().stream().filter(node -> node == points.get(0).getValue()).findFirst();
            op.ifPresent(node -> node.setVisible(false));
            points.clear();
            markDirty();
        } catch (NullPointerException e) {
        }
    }

    //Перерисовует точки когда двигается экран
    @Override
    public void layoutLayer() {
        for (Pair<MapPoint, Node> candidate : points) {
            MapPoint point = candidate.getKey();
            Node icon = candidate.getValue();
            Point2D mapPoint = baseMap.getMapPointFromDegreesToXY(point.getLatitude(), point.getLongitude());
            icon.setVisible(true);
//            Задает смещение иконки точки
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