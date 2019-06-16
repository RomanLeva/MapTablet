package data;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Pair;
import maps.MapLayer;

import java.util.LinkedList;

public class LinesLayer extends MapLayer {
    private LinkedList<Pair<Pair<MapPoint, MapPoint>, Node>> lines = new LinkedList<>();

    public void addLine(MapPoint start, MapPoint end, Color color) {
        Line line = new Line();
        line.setStrokeWidth(3);
        Point2D s = baseMap.getMapPointFromDegreesToXY(start.getLatitude(), start.getLongitude());
        Point2D e = baseMap.getMapPointFromDegreesToXY(end.getLatitude(), end.getLongitude());
        line.setStartX(s.getX());
        line.setStartY(s.getY());
        line.setEndX(e.getX());
        line.setEndY(e.getY());
        line.setStroke(color);
        lines.addFirst(new Pair<>(new Pair<>(start, end), line));
        this.getChildren().add(line);
        this.markDirty();
    }
    // Used in redrawing lines after scrolling or zooming.
    @Override
    public void layoutLayer() {
        for (Pair<Pair<MapPoint, MapPoint>, Node> ln : lines) { // Redraw lines
            Pair<MapPoint, MapPoint> p = ln.getKey();
            Point2D sp = baseMap.getMapPointFromDegreesToXY(p.getKey().getLatitude(), p.getKey().getLongitude());
            Point2D ep = baseMap.getMapPointFromDegreesToXY(p.getValue().getLatitude(), p.getValue().getLongitude());
            Line line = ((Line) ln.getValue());
            line.setStartX(sp.getX());
            line.setStartY(sp.getY());
            line.setEndX(ep.getX());
            line.setEndY(ep.getY());
            line.setVisible(true);
        }
    }

    public LinkedList<Pair<Pair<MapPoint, MapPoint>, Node>> getLines() {
        return lines;
    }
}
