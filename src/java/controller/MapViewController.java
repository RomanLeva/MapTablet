package controller;
import data.MapPoint;
import data.PoiLayersData;
import javafx.animation.Animation.Status;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.util.Duration;
import javafx.util.Pair;
import maps.ArrowsLinesLayer;
import maps.BaseMap;
import maps.MapLayer;
import maps.PoiLayer;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.logging.Logger;

import static data.MapPoint.Commands.FIRE;
import static data.MapPoint.Commands.READY;

/**
 * This is the top UI element of the map component. Useful only in JavaFX desktop applications.
 */
public class MapViewController extends Region {
    private static final Logger logger = Logger.getLogger(MapViewController.class.getName());
    private BaseMap baseMap;
    private Timeline timeline;
    private final Rectangle clip;
    private MapPoint centerPoint = null;
    private boolean zooming = false;
    private boolean enableDragging = false;
    private boolean manageLayerObject = true;
    private Paint focusedColor;
    private boolean isPointSelected;
    private boolean selectingMissed = false;
    private boolean selectingDownload = false;
    private boolean selectingLine = false;
    private PoiLayersData poiLayersData;
    private JfxGuiController jfxGuiController;
    private Point2D anchor2D;

    public MapViewController(BaseMap baseMap, PoiLayersData poiLayersData) {
        this.baseMap = baseMap;
        this.poiLayersData = poiLayersData;
        getChildren().add(baseMap);
        poiLayersData.getLayers().forEach(l -> this.getChildren().add(l));
        registerInputListeners();
        baseMap.centerLat().addListener(o -> markDirty());
        baseMap.centerLon().addListener(o -> markDirty());
        clip = new Rectangle();
        this.setClip(clip);
        this.layoutBoundsProperty().addListener(e -> {
            // in case our assigned space changes, AND in case we are requested
            // to center at a specific point, we need to re-center.
            if (centerPoint != null) {
                // We will set the center to a slightly different location first, in order
                // to trigger the invalidationListeners to calculate tile positions.
                setCenter(centerPoint.getLatitude() + .00001, centerPoint.getLongitude() + .00001);
                setCenter(centerPoint);
            }
        });
        baseMap.setCenter(60, 0); // Start watching map here
    }

    // Register mouse events on map view node, creating or selecting points
    private void registerInputListeners() {
        setOnMousePressed(t -> {
            manageLayerObject = true;
            if (zooming) return;
            baseMap.setX0(t.getX());
            baseMap.setY0(t.getY());
            centerPoint = null; // once the user starts moving, we don't track the center anymore.
            // dragging is enabled only after a pressed event, to prevent dragging right after zooming
            enableDragging = true;
            if (selectingDownload) { // start creating the rectangle for downloading region
                if (getChildren().contains(poiLayersData.rectangle)) {
                    getChildren().remove(poiLayersData.rectangle);
                    poiLayersData.rectangle.setWidth(0);
                    poiLayersData.rectangle.setHeight(0);
                }
                poiLayersData.getDownloadCornerPoints().clear();
                poiLayersData.rectangle.setX(t.getX());
                poiLayersData.rectangle.setY(t.getY());
                anchor2D = new Point2D(t.getX(), t.getY());
                getChildren().add(poiLayersData.rectangle);
            }
        });
        setOnMouseDragged(t -> {
            if (selectingLine) return;
            if (selectingDownload) {
                double width = Math.abs(t.getX() - anchor2D.getX());
                double height = Math.abs(t.getY() - anchor2D.getY());
                long n = (long) Math.pow(2, baseMap.zoom().get()) * 2;
                if (width > n) width = n;
                if (height > n) height = n;
                poiLayersData.rectangle.setWidth(width);
                poiLayersData.rectangle.setHeight(height);
                poiLayersData.rectangle.setX(Math.min(anchor2D.getX(), t.getX()));
                poiLayersData.rectangle.setY(Math.min(anchor2D.getY(), t.getY()));
                markDirty();
            } else {
                manageLayerObject = false;
                if (zooming || !enableDragging) {
                    return;
                }
                baseMap.moveX(baseMap.getX0() - t.getX());
                baseMap.moveY(baseMap.getY0() - t.getY());
                baseMap.setX0(t.getX());
                baseMap.setY0(t.getY());
            }
        });
        setOnMouseReleased(t -> {
            enableDragging = false;
            if (selectingMissed) {//Mark missing points if button MISSED is pressed
                Rectangle r = new Rectangle(10, 10, Color.YELLOW);
                r.setLayoutX(-5);
                r.setLayoutY(-5);
                poiLayersData.getMissedPointsLayer().addPoint(baseMap.getMapPointFromXYtoDegrees(t.getX(), t.getY()), r);
            } else if (selectingDownload) { // Over creating the rectangle for downloading region, swap corner points that it creates useful rectangle for downloading, firstXY always < secondXY
                if (anchor2D.getX() <= t.getX() & anchor2D.getY() <= t.getY()) {
                    MapPoint f = baseMap.getMapPointFromXYtoDegrees(anchor2D.getX(), anchor2D.getY());
                    MapPoint s = baseMap.getMapPointFromXYtoDegrees(t.getX(), t.getY());
                    poiLayersData.getDownloadCornerPoints().add(f); // add the first corner point
                    poiLayersData.getDownloadCornerPoints().add(s); // second corner point of the selecting rectangle
                } else if (anchor2D.getX() > t.getX() & anchor2D.getY() > t.getY()) {
                    MapPoint f = baseMap.getMapPointFromXYtoDegrees(anchor2D.getX(), anchor2D.getY());
                    MapPoint s = baseMap.getMapPointFromXYtoDegrees(t.getX(), t.getY());
                    poiLayersData.getDownloadCornerPoints().add(s); // add the first corner point
                    poiLayersData.getDownloadCornerPoints().add(f); // second corner point of the selecting rectangle
                } else if (anchor2D.getX() > t.getX() & anchor2D.getY() <= t.getY()) {
                    double x = anchor2D.getX();
                    anchor2D = new Point2D(t.getX(), anchor2D.getY());
                    MapPoint f = baseMap.getMapPointFromXYtoDegrees(anchor2D.getX(), anchor2D.getY());
                    MapPoint s = baseMap.getMapPointFromXYtoDegrees(x, t.getY());
                    poiLayersData.getDownloadCornerPoints().add(f); // add the first corner point
                    poiLayersData.getDownloadCornerPoints().add(s); // second corner point of the selecting rectangle
                } else if (anchor2D.getX() <= t.getX() & anchor2D.getY() > t.getY()) {
                    double y = anchor2D.getY();
                    anchor2D = new Point2D(anchor2D.getX(), t.getY());
                    MapPoint f = baseMap.getMapPointFromXYtoDegrees(anchor2D.getX(), anchor2D.getY());
                    MapPoint s = baseMap.getMapPointFromXYtoDegrees(t.getX(), y);
                    poiLayersData.getDownloadCornerPoints().add(f); // add the first corner point
                    poiLayersData.getDownloadCornerPoints().add(s); // second corner point of the selecting rectangle
                }
            } else if (selectingLine) { // Create two point for line
                switch (poiLayersData.getLineStartEndPoints().size()) {
                    case 0:
                        if (!t.getTarget().equals(this)) {
                            selectPoint(t);
                            poiLayersData.getLineStartEndPoints().add(((MapPoint) poiLayersData.getFocusedPair().getKey()));
                        } else {
                            createNewPoint(t);
                            poiLayersData.getLineStartEndPoints().add(poiLayersData.getTempPointLayer().getPoints().getFirst().getKey());
                            poiLayersData.getTempPointLayer().deleteTempPoint();
                        }
                        break;
                    case 1:
                        if (!t.getTarget().equals(this)) {
                            selectPoint(t);
                            poiLayersData.getLineStartEndPoints().add(((MapPoint) poiLayersData.getFocusedPair().getKey()));
                        } else {
                            createNewPoint(t);
                            poiLayersData.getLineStartEndPoints().add(poiLayersData.getTempPointLayer().getPoints().getFirst().getKey());
                        }
                        poiLayersData.setFocusedPair(new Pair<>(null, null));
                        poiLayersData.getTempPointLayer().deleteTempPoint();
                        break;
                    case 2:
                        poiLayersData.getLineStartEndPoints().clear();
                        if (!t.getTarget().equals(this)) {
                            selectPoint(t);
                            poiLayersData.getLineStartEndPoints().add(((MapPoint) poiLayersData.getFocusedPair().getKey()));
                        } else {
                            createNewPoint(t);
                            poiLayersData.getLineStartEndPoints().add(poiLayersData.getTempPointLayer().getPoints().getFirst().getKey());
                        }
                        break;
                    default:
                        selectingLine = false;
                        poiLayersData.getLineStartEndPoints().clear();
                }
            } else if (manageLayerObject) { // Do something with map event, it can be point/line selection or temporary point creation.
                if (!t.getTarget().equals(this)) {
                    if (t.getTarget() instanceof Line) {
                        selectLine(t);
                    } else if (t.getTarget() instanceof Circle | t.getTarget() instanceof Polygon) {
                        selectPoint(t);
                    }
                } else {
                    createNewPoint(t);
                }
            }
        });
        setOnZoomStarted(t -> {
            zooming = true;
            enableDragging = false;
        });
        setOnZoomFinished(t -> zooming = false);
        setOnScroll(t -> {
            poiLayersData.getDownloadCornerPoints().clear();
            poiLayersData.rectangle.setWidth(0);
            poiLayersData.rectangle.setHeight(0);
            baseMap.zoom(t.getDeltaY() > 1 ? 1 : -1, t.getX(), t.getY());
        });
//        setOnZoom(t -> baseMap.zoom(t.getZoomFactor() - 1, t.getX(), t.getY()));
//        if (Platform.isDesktop()) {
//
//        }
    }

    //Erase selected and temporary points, make point color back as it was before
    private void refreshSelection() {
        if (!poiLayersData.getTempPointLayer().getPoints().isEmpty()) {
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
        if (poiLayersData.getFocusedPair().getValue() != null) {
            ((Shape) poiLayersData.getFocusedPair().getValue()).setFill(focusedColor);
            poiLayersData.setFocusedPair(new Pair<>(null, null));
        }
        poiLayersData.getFocusedLine().setStroke(Color.RED);
        jfxGuiController.readyFire = false;
        jfxGuiController.txtInfo.setText("");
        jfxGuiController.txtDist.setText("");
        jfxGuiController.txtDir.setText("");
        jfxGuiController.txtLon.setText("");
        jfxGuiController.txtLat.setText("");
        jfxGuiController.btnTarget.setText("TARG");
    }

    private void selectLine(MouseEvent t) {
        refreshSelection();
        Optional<Pair<Pair<MapPoint, MapPoint>, Node>> op = poiLayersData.getLinesLayer().getLines().stream().filter(pair -> pair.getValue() == t.getTarget()).findFirst();
        op.ifPresent(pairNodePair -> {
            poiLayersData.setFocusedLine(((Line) pairNodePair.getValue()));
            ((Shape) pairNodePair.getValue()).setStroke(Color.BLUE);
        });
    }

    private void selectPoint(MouseEvent t) {
        refreshSelection();
        for (MapLayer layer : poiLayersData.getLayers()) {
            if (layer instanceof ArrowsLinesLayer) continue;
            Optional<Pair<MapPoint, Node>> op = ((PoiLayer) layer).getPoints().stream().filter(pair -> pair.getValue() == t.getTarget()).findFirst();
            if (op.isPresent()) {
                Pair<MapPoint, Node> pair = op.get();
                focusedColor = ((Shape) pair.getValue()).getFill();
                if (focusedColor.equals(Color.RED)) {
                    switch (pair.getKey().getCommand()) {
                        case READY:
                            jfxGuiController.btnTarget.setText("FIRE!");
                            jfxGuiController.readyFire = true;
                            break;
                        case FIRE:
                            jfxGuiController.btnTarget.setText("WORK");
                            break;
                    }
                }
                if (focusedColor == Color.YELLOW) return;
                poiLayersData.setFocusedPair(pair);
                isPointSelected = true;
                ((Shape) pair.getValue()).setFill(Color.BLUE);
                showDistanceToSelectedPoint();
            }
        }
    }

    private void createNewPoint(MouseEvent t) {
        isPointSelected = false;
        refreshSelection();
        Circle c = new Circle(7, Color.GRAY);
        poiLayersData.getTempPointLayer().addPoint(baseMap.getMapPointFromXYtoDegrees(t.getX(), t.getY()), c);
        showDistanceToSelectedPoint();
    }

    /**
     * Request the map to set its zoom level to the specified value. The map
     * considers this request, but it does not guarantee the zoom level will be
     * set to the provided value
     *
     * @param zoom the requested zoom level
     */
    public void setZoom(double zoom) {
        baseMap.setZoom(zoom);
    }

    //Request the map to position itself around the specified center
    void setCenter(MapPoint mapPoint) {
        setCenter(mapPoint.getLatitude(), mapPoint.getLongitude());
    }

    void setCenter(double lat, double lon) {
        this.centerPoint = new MapPoint(lat, lon);
        baseMap.setCenter(lat, lon);
    }

    Point2D getCenter() {
        return baseMap.getMapPointFromDegreesToXY(centerPoint.getLatitude(), centerPoint.getLongitude());
    }

    //Wait a bit, then move to the specified mapPoint in seconds time
    void flyTo(MapPoint mapPoint) {
        if ((timeline != null) && (timeline.getStatus() == Status.RUNNING)) {
            timeline.stop();
        }
        double currentLat = baseMap.centerLat().get();
        double currentLon = baseMap.centerLon().get();
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(baseMap.prefCenterLat(), currentLat), new KeyValue(baseMap.prefCenterLon(), currentLon)),
                new KeyFrame(Duration.seconds((double) 0), new KeyValue(baseMap.prefCenterLat(), currentLat), new KeyValue(baseMap.prefCenterLon(), currentLon)),
                new KeyFrame(Duration.seconds((double) 0 + (double) 1), new KeyValue(baseMap.prefCenterLat(), mapPoint.getLatitude()), new KeyValue(baseMap.prefCenterLon(), mapPoint.getLongitude(), Interpolator.EASE_BOTH))
        );
        timeline.play();
    }

    private boolean dirty = false;

    // Causes redrawing of map when it was scrolled
    private void markDirty() {
        dirty = true;
        this.setNeedsLayout(true);
    }

    // Causes redrawing of points layers when it was changed
    @Override
    public void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        if (dirty) {
            for (MapLayer layer : poiLayersData.getLayers()) {
                layer.layoutLayer();
            }
        }
        super.layoutChildren();
        dirty = false;
        // we need to get these values or we won't be notified on new changes
        baseMap.centerLon().get();
        baseMap.centerLat().get();
        // update clip
        clip.setWidth(w);
        clip.setHeight(h);
    }

    // Show distance between point only if you have your position point and put (or select) another point on map
    void showDistanceToSelectedPoint() {
        jfxGuiController.txtDist.setText("");
        jfxGuiController.txtDir.setText("");
        DecimalFormat df = new DecimalFormat("#.###");
        DecimalFormat df_dist = new DecimalFormat("###,###,###");
        df.setRoundingMode(RoundingMode.CEILING);
        MapPoint selected_deg;
        if (isPointSelected) {
            selected_deg = ((MapPoint) poiLayersData.getFocusedPair().getKey());
        } else {
            selected_deg = poiLayersData.getTempPointLayer().getPoints().getFirst().getKey();
        }
        if (!poiLayersData.getMyPosPointLayer().getPoints().isEmpty()) {
            MapPoint myPos_deg = poiLayersData.getMyPosPointLayer().getPoints().getFirst().getKey();
            double x_deg = Math.abs(myPos_deg.getLongitude() - selected_deg.getLongitude());
            double mid = (myPos_deg.getLatitude() + selected_deg.getLatitude()) / 2;
            double x = x_deg * 111120 * Math.abs(Math.cos(Math.toRadians(mid))); //cos то шо на экваторе поворот вектора по Х на точку равен самому себе... , а ближе к полюсу вектор короче на cos(широты) ибо WSG-84
            double y_deg = Math.abs(myPos_deg.getLatitude() - selected_deg.getLatitude());
            double y = y_deg * 111120; //111120 м - средняя длина одного градуса широты
            double c = Math.round(Math.sqrt(x * x + y * y));
            int ci = ((int) c);
            jfxGuiController.txtDist.setText(df_dist.format(ci) + " m");
            // Next will show the direction on selected point, first we need to define in what quarter the target is displaced
            boolean right = false;
            boolean up = false;
            double angle;
            Point2D sel_point = baseMap.getMapPointFromDegreesToXY(selected_deg.getLatitude(), selected_deg.getLongitude());
            Point2D my_point = baseMap.getMapPointFromDegreesToXY(myPos_deg.getLatitude(), myPos_deg.getLongitude());
            if (sel_point.getX() >= my_point.getX()) right = true;
            if (sel_point.getY() <= my_point.getY()) up = true;
            double a = Math.toDegrees(Math.acos(x / c));
            double b = Math.toDegrees(Math.acos(y / c));
            if (Double.isNaN(a)) a = 0;
            if (Double.isNaN(b)) b = 0;
            if (right & up) {
                angle = 90 - a;
            } else if (right & !up) {
                angle = 180 - b;
            } else if (!up) {
                angle = 180 + b;
            } else {
                angle = 270 + a;
            }
            if (ci == 0) angle = 0;
            jfxGuiController.txtDir.setText(String.valueOf(df.format(angle)) + "\u00b0");
        }
        jfxGuiController.txtLon.setText(String.valueOf(df.format(selected_deg.getLongitude())) + " lon");
        jfxGuiController.txtLat.setText(String.valueOf(df.format(selected_deg.getLatitude())) + " lat");
    }

    boolean isPointSelected() {
        return isPointSelected;
    }

    void setPointSelected(boolean isSelected) {
        isPointSelected = isSelected;
    }

    boolean isSelectingMissed() {
        return selectingMissed;
    }

    void setSelectingMissed(boolean selectingMissed) {
        this.selectingMissed = selectingMissed;
    }

    public void setGuiController(JfxGuiController jfxGuiController) {
        this.jfxGuiController = jfxGuiController;
    }

    void setFocusedColor(Color color) {
        focusedColor = color;
    }

    boolean isSelectingDownload() {
        return selectingDownload;
    }

    void setSelectingDownload(boolean selectingDownload) {
        this.selectingDownload = selectingDownload;
    }

    BaseMap getBaseMap() {
        return baseMap;
    }

    public boolean isSelectingLine() {
        return selectingLine;
    }

    public void setSelectingLine(boolean selectingLine) {
        this.selectingLine = selectingLine;
    }
}