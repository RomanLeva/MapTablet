package controller;
import data.MapPoint;
import data.PoiLayersData;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.util.Pair;
import maps.LinesLayer;
import maps.MapLayer;
import maps.PointsLayer;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Optional;

// Useful only in JavaFX applications, uses its specific methods. Works in pair only with MapView controller.
public class JfxGuiController implements GUIController {
    public Button btnLaser;
    public Button btnTriangul;
    public Button btnMyposition;
    public Button btnPrev;
    public Button btnFwd;
    public Button btnTarget;
    public Button btnDelete;
    public Button btnConnect;
    public ToggleButton btnMissed;
    public Button btnZoomOut;
    public Button btnZoomIn;
    public ToggleButton btnDownload;
    public Button btnDestroyed;
    public TextField txtDir;
    public TextField txtDist;
    public TextField txtLon;
    public TextField txtLat;
    public TextArea txtInfo;
    public BorderPane borderPane;
    public BorderPane pane;
    public ToggleButton btnLine;
    public Button btnGun;
    public Button btnMark;
    private MapViewController mapViewController; // default controller for onMap actions
    private AppLogicController appLogicController; // controller of entire app logic and decisions
    private PoiLayersData poiLayersData;
    private int targetMaxIndex, targetNextIndex = 0;
    private boolean somethingPressed = false;
    private DecimalFormat df_coord_dir = new DecimalFormat("#.###");

    public void clickTarget() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected()) {
            if (mapViewController.readyFire) { // Point is not selected by mouse, but selected by buttons << >>.
                ((MapPoint) poiLayersData.getFocusedPair().getKey()).setCommand(MapPoint.Commands.FIRE);
                btnTarget.setText("WORK");
                appLogicController.processUserInputMessage(((MapPoint) poiLayersData.getFocusedPair().getKey()));
            } else { // Create new target point and pin it on the map.
                if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
                MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
                t.setCommand(MapPoint.Commands.TARGET);
                poiLayersData.getTargetPointsLayer().addPoint(t, new Circle(7, Color.RED));
                poiLayersData.getTempPointLayer().deleteTempPoint();
                mapViewController.setFocusedColor(Color.RED);
                poiLayersData.setFocusedPair(poiLayersData.getTargetPointsLayer().getPoints().getFirst());
                ((Shape) poiLayersData.getTargetPointsLayer().getPoints().getFirst().getValue()).setFill(Color.BLUE);
                mapViewController.setPointSelected(true);
                appLogicController.processUserInputMessage(t);
            }
        } else if (mapViewController.readyFire) { // Point is selected by mouse and ready to be strafed.
            ((MapPoint) poiLayersData.getFocusedPair().getKey()).setCommand(MapPoint.Commands.FIRE);
            btnTarget.setText("WORK");
            appLogicController.processUserInputMessage(((MapPoint) poiLayersData.getFocusedPair().getKey()));
        }
    }

    public void clickDestroyed() {
        if (somethingPressed) return;
        if (mapViewController.isSelectingMissed()) {
            mapViewController.setSelectingMissed(false);
            btnMissed.setSelected(false);
            poiLayersData.getMissedPointsLayer().getPoints().forEach(pair -> {
                ((Shape) pair.getValue()).setFill(Color.TRANSPARENT);
                pair.getValue().setVisible(false);
            });
            poiLayersData.getMissedPointsLayer().getPoints().clear();
            poiLayersData.setFocusedPair(new Pair<>(null, null));
        } else {
            clickDelete();
        }
    }

    public void clickDelete() {
        if (somethingPressed) return;
        Pair p = poiLayersData.getFocusedPair();
        for (MapLayer poiLayer : poiLayersData.getLayers()) {
            if (poiLayer instanceof LinesLayer) { // Line was selected
                if (poiLayersData.getFocusedLine().getStroke().equals(Color.BLUE)) {
                    Line l = poiLayersData.getFocusedLine();
                    Optional<Pair<Pair<MapPoint, MapPoint>, Node>> op = poiLayersData.getLinesLayer().getLines().stream().filter(pair -> pair.getValue() == l).findFirst();
                    op.ifPresent(pairNodePair -> {
                        poiLayersData.getLinesLayer().getLines().remove(pairNodePair);
                        ((Shape) op.get().getValue()).setStroke(Color.TRANSPARENT);
                        op.get().getValue().setVisible(false);
                    });
                } else break;
            } else if (poiLayer instanceof PointsLayer) { // Point was selected
                Optional op = ((PointsLayer) poiLayer).getPoints().stream().filter(mapPointNodePair -> mapPointNodePair == p).findFirst();
                if (op.isPresent()) {
                    Pair pp = (Pair) op.get();
                    MapPoint t = (MapPoint) pp.getKey();
                    t.setCommand(MapPoint.Commands.DESTROYED);
                    if (!((pp.getValue()) instanceof Polygon)) { // don't send triangular point
                        appLogicController.processUserInputMessage(t);
                    } else {
                        ((Shape) pp.getValue()).setFill(Color.TRANSPARENT);
                        ((Shape) pp.getValue()).setVisible(false);
                    }
                    poiLayersData.setFocusedPair(new Pair<>(null, null));
                    mapViewController.setPointSelected(false);
                    break;
                }
            }
        }
    }

    public void clickMissed() {
        if (mapViewController.isPointSelected() && ((MapPoint) poiLayersData.getFocusedPair().getKey()).getCommand().equals(MapPoint.Commands.FIRE)) {
            if (!mapViewController.isSelectingMissed()) {
                somethingPressed = true;
                mapViewController.setSelectingMissed(true);
                btnMissed.setSelected(true);
            } else {
                somethingPressed = false;
                mapViewController.setSelectingMissed(false);
                btnMissed.setSelected(false);
                appLogicController.processAdjustments();
                poiLayersData.getMissedPointsLayer().getPoints().forEach(pair -> {
                    ((Shape) pair.getValue()).setFill(Color.TRANSPARENT);
                    pair.getValue().setVisible(false);
                });
                poiLayersData.getMissedPointsLayer().getPoints().clear();
            }
        } else {
            btnMissed.setSelected(false);
            txtInfo.setText("Select working\ntarget");
        }
    }

    public void clickFwd() {
        if (somethingPressed) return;
        mapViewController.setPointSelected(true);
        LinkedList<Pair<MapPoint, Node>> l = poiLayersData.getTargetPointsLayer().getPoints();
        if (l.isEmpty()) return;
        targetMaxIndex = l.size() - 1;
        targetNextIndex--;
        if (targetNextIndex > targetMaxIndex) targetNextIndex = targetMaxIndex;
        if (targetNextIndex < 0) targetNextIndex = targetMaxIndex;
        Pair p = l.get(targetNextIndex);
        if (poiLayersData.getFocusedPair().getValue() != null)
            ((Shape) poiLayersData.getFocusedPair().getValue()).setFill(Color.RED);
        poiLayersData.setFocusedPair(p);
        ((Shape) p.getValue()).setFill(Color.BLUE);
        mapViewController.setFocusedColor(Color.RED);
        mapViewController.showDistanceToSelectedPoint();
        mapViewController.flyTo(((MapPoint) p.getKey()));
    }

    public void clickPrev() {
        if (somethingPressed) return;
        mapViewController.setPointSelected(true);
        LinkedList<Pair<MapPoint, Node>> l = poiLayersData.getTargetPointsLayer().getPoints();
        if (l.isEmpty()) return;
        targetMaxIndex = l.size() - 1;
        targetNextIndex++;
        if (targetNextIndex > targetMaxIndex) targetNextIndex = 0;
        if (targetNextIndex < 0) targetNextIndex = 0;
        Pair p = l.get(targetNextIndex);
        if (poiLayersData.getFocusedPair().getValue() != null)
            ((Shape) poiLayersData.getFocusedPair().getValue()).setFill(Color.RED);
        poiLayersData.setFocusedPair(p);
        ((Shape) p.getValue()).setFill(Color.BLUE);
        mapViewController.setFocusedColor(Color.RED);
        mapViewController.showDistanceToSelectedPoint();
        mapViewController.flyTo(((MapPoint) p.getKey()));
    }

    public void clickMyposition() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected() & !poiLayersData.getTempPointLayer().getPoints().isEmpty()) {
            MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
            if (!poiLayersData.getMyPosPointLayer().getPoints().isEmpty()) {
                poiLayersData.getMyPosPointLayer().getPoints().getFirst().getValue().setVisible(false);
                poiLayersData.getMyPosPointLayer().getPoints().clear();
            }
            poiLayersData.getMyPosPointLayer().addPoint(new MapPoint(t.getLatitude(), t.getLongitude()), new Circle(7, Color.AQUAMARINE));
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
    }

    public void clickTriangul() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected()) {
            if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
            MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
            Polygon polygon = new Polygon();
            polygon.getPoints().addAll(
                    0.0, -6.0,
                    -6.0, 6.0,
                    6.0, 6.0);
            polygon.setFill(Color.BLACK);
            MapPoint p = new MapPoint(t.getLatitude(), t.getLongitude());
            p.setCommand(MapPoint.Commands.NOWEAPON);
            poiLayersData.getTriangPointsLayer().addPoint(p, polygon);
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
    }

    public void clickDownload() {
        if (!mapViewController.isSelectingDownload() & !mapViewController.isSelectingMissed()) {
            mapViewController.setSelectingDownload(true);
            btnDownload.setSelected(true);
            somethingPressed = true;
        } else {
            somethingPressed = false;
            mapViewController.setSelectingDownload(false);
            btnDownload.setSelected(false);
            poiLayersData.rectangle.setWidth(0);
            poiLayersData.rectangle.setHeight(0);
            appLogicController.selectToDownload();
        }
    }

    public void clickConnect() {
        appLogicController.usedAsHQ = false;
        btnGun.setDisable(true);
        appLogicController.connectTo();
    }

    public void clickCreate() {
        appLogicController.usedAsHQ = true;
        btnGun.setDisable(false);
        appLogicController.createHeadQuarter();
    }

    public void clickLaser() {
        if (somethingPressed) return;
        // Use laser rangefinder, compass and your position point on the map to mark a landmark.
    }

    public void clickGun() {
        if (somethingPressed) return;
        if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
        MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
        MapPoint wp = new MapPoint(t.getLatitude(), t.getLongitude());
        wp.setCommand(MapPoint.Commands.READY);
        poiLayersData.getWeaponPointsLayer().addPoint(wp, new Circle(7, Color.ORCHID));
        poiLayersData.getTempPointLayer().deleteTempPoint();
    }

    public void clickMark() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected()) {
            if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
            MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
            Circle circle = new Circle(10);
            circle.setFill(Color.TRANSPARENT);
            circle.setStroke(Color.AQUA);
            circle.setStrokeWidth(4);
            MapPoint p = new MapPoint(t.getLatitude(), t.getLongitude());
            p.setCommand(MapPoint.Commands.NOWEAPON);
            poiLayersData.getMarksPointsLayer().addPoint(p, circle);
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
    }

    public void clickZoomOut() {
        poiLayersData.getDownloadCornerPoints().clear();
        poiLayersData.rectangle.setWidth(0);
        poiLayersData.rectangle.setHeight(0);
        mapViewController.getBaseMap().zoom(-1, mapViewController.getWidth() / 2, mapViewController.getHeight() / 2);
        poiLayersData.getLayers().forEach(MapLayer::markDirty);
    }

    public void clickZoomIn() {
        poiLayersData.getDownloadCornerPoints().clear();
        poiLayersData.rectangle.setWidth(0);
        poiLayersData.rectangle.setHeight(0);
        mapViewController.getBaseMap().zoom(1, mapViewController.getWidth() / 2, mapViewController.getHeight() / 2);
        poiLayersData.getLayers().forEach(MapLayer::markDirty);
    }

    public void clickLine() {
        if (!mapViewController.isSelectingLine()) {
            setInfo("Select two line points,\n than press LINE again.");
            if (poiLayersData.getTempPointLayer().getPoints().size() != 0)
                poiLayersData.getTempPointLayer().deleteTempPoint();
            btnLine.setSelected(true);
            somethingPressed = true;
            mapViewController.setSelectingLine(true);
            poiLayersData.getLineStartEndPoints().clear();
        } else {
            btnLine.setSelected(false);
            somethingPressed = false;
            mapViewController.setSelectingLine(false);
            if (poiLayersData.getTempPointLayer().getPoints().size() != 0)
                poiLayersData.getTempPointLayer().deleteTempPoint();
            try {
                poiLayersData.getLinesLayer().addLine(poiLayersData.getLineStartEndPoints().get(0), poiLayersData.getLineStartEndPoints().get(1), Color.RED);
            } catch (IndexOutOfBoundsException | NullPointerException e) {
                poiLayersData.getLineStartEndPoints().clear();
                mapViewController.refreshSelection();
                setInfo("Repeat line.");
                return;
            }
            mapViewController.refreshSelection();
            setInfo("Line created.");
        }
    }

    @Override
    public void setReadyFire(boolean readyFire) {
        mapViewController.readyFire = readyFire;
    }

    public void setMapViewController(MapViewController view) {
        this.mapViewController = view;
    }

    public void setPoiLayersData(PoiLayersData poiLayersData) {
        this.poiLayersData = poiLayersData;
    }

    public void setAppLogicController(AppLogicController appLogicController) {
        this.appLogicController = appLogicController;
    }

    public void clickUnit(ActionEvent actionEvent) {
    }

    @Override
    public void setLatitude(double latitude) {
        df_coord_dir.setRoundingMode(RoundingMode.CEILING);
        txtLat.setText(String.valueOf(df_coord_dir.format(latitude)) + " lat");
    }

    @Override
    public void setLongitude(double longitude) {
        df_coord_dir.setRoundingMode(RoundingMode.CEILING);
        txtLon.setText(String.valueOf(df_coord_dir.format(longitude)) + " lon");
    }

    @Override
    public void setButtonText(String buttonText) {
        btnTarget.setText(buttonText);
    }

    @Override
    public void setDirection(double direction) {
        df_coord_dir.setRoundingMode(RoundingMode.CEILING);
        txtDir.setText(String.valueOf(df_coord_dir.format(direction)) + "\u00b0");
    }

    @Override
    public void setDistance(double distance) {
        DecimalFormat df_dist = new DecimalFormat("###,###,###");
        txtDist.setText(String.valueOf(df_dist.format(distance)) + " m");
    }

    @Override
    public void setInfo(String info) {
        txtInfo.setText(info);
        txtInfo.selectPositionCaret(txtInfo.getLength());
        txtInfo.deselect();
    }

    @Override
    public void eraseFields() {
        txtInfo.setText("");
        txtDist.setText("");
        txtDir.setText("");
        txtLon.setText("");
        txtLat.setText("");
        btnTarget.setText("TARG");
    }
}
