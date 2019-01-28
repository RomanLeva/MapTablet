package maps;
import data.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.LinkedList;

public class ArrowsLinesLayer extends MapLayer {
    private LinkedList<Pair<Pair<MapPoint, MapPoint>, Node>> lines = new LinkedList<>();

    public void addLine(MapPoint start, MapPoint end) {
        Line line = new Line();
        line.setStrokeWidth(2);
        Point2D s = baseMap.getMapPointFromDegreesToXY(start.getLatitude(), start.getLongitude());
        Point2D e = baseMap.getMapPointFromDegreesToXY(end.getLatitude(), end.getLongitude());
        line.setStartX(s.getX());
        line.setStartY(s.getY());
        line.setEndX(e.getX());
        line.setEndY(e.getY());
        line.setStroke(Color.ORANGERED);
        lines.addFirst(new Pair<>(new Pair<>(start, end), line));
        this.getChildren().add(line);
        this.markDirty();
    }

    @Override
    public void layoutLayer() {
        for (Pair<Pair<MapPoint, MapPoint>, Node> candidate : lines) { // Redraw lines
            Pair<MapPoint, MapPoint> p = candidate.getKey();
            Point2D sp = baseMap.getMapPointFromDegreesToXY(p.getKey().getLatitude(), p.getKey().getLongitude());
            Point2D ep = baseMap.getMapPointFromDegreesToXY(p.getValue().getLatitude(), p.getValue().getLongitude());
            Line line = ((Line) candidate.getValue());
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
