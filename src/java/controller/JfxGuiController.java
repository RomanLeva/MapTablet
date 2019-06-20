package controller;
import data.MapPoint;
import data.PoiLayersData;
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
import data.LinesLayer;
import maps.MapLayer;
import data.PointsLayer;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Optional;

/**
 * GUI controller useful only in JavaFX applications, uses its specific methods. Works in pair only with MapViewController class.
 */
public class JfxGuiController {
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
    public Button btnUnit;
    public Button btnCreate;
    public ToggleButton btnAim;
    public ToggleButton btnMove;
    private MapViewController mapViewController; // default controller for onMap actions
    private AppLogicController appLogicController; // controller of entire app logic and decisions
    private PoiLayersData poiLayersData;
    private int targetMaxIndex, targetNextIndex = 0;
    private boolean somethingPressed = false;
    private DecimalFormat df_coord_dir = new DecimalFormat("#.###");
    enum ButtonType {
        NONE, MISSED, DOWNLOAD, LINE, AIM
    }
    ButtonType buttonPressedType = ButtonType.NONE;

    public void clickTarget() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected()) {
            if (mapViewController.readyFire) { // Point is not selected by mouse, but selected by buttons << >>.
                poiLayersData.getFocusedPoint().setCommand(MapPoint.Commands.FIRE);
                btnTarget.setText("WORK");
                appLogicController.processUserInputMessage(poiLayersData.getFocusedPoint());
            } else { // Create new target point and pin it on the map.
                if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
                MapPoint t = poiLayersData.getTempPoint();
                t.setCommand(MapPoint.Commands.TARGET);
                t.setPointType(MapPoint.PointType.TARGET);
                poiLayersData.getTargetPointsLayer().addPoint(t, new Circle(9, Color.RED));
                poiLayersData.getTempPointLayer().deleteTempPoint();
                mapViewController.setFocusedColor(Color.RED);
                poiLayersData.setFocusedPair(poiLayersData.getTargetPointsLayer().getPoints().getFirst());
                ((Shape) poiLayersData.getTargetPointsLayer().getPoints().getFirst().getValue()).setFill(Color.BLUE);
                mapViewController.setPointSelected(true);
                appLogicController.processUserInputMessage(t);
            }
        } else if (mapViewController.readyFire) { // Point is selected by mouse and ready to be strafed.
            poiLayersData.getFocusedPoint().setCommand(MapPoint.Commands.FIRE);
            btnTarget.setText("WORK");
            appLogicController.processUserInputMessage(poiLayersData.getFocusedPoint());
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
        if (!mapViewController.isPointSelected()) {
            resetButtons(btnMissed);
            return;
        }
        switch (buttonPressedType) {
            case NONE:
                if (!poiLayersData.getFocusedPoint().getCommand().equals(MapPoint.Commands.FIRE)) {
                    setInfo("Select fired target");
                    resetButtons(btnMissed);
                    return;
                }
                somethingPressed = true;
                btnMissed.setSelected(true);
                buttonPressedType = ButtonType.MISSED;
                break;
            case MISSED:
                appLogicController.processAdjustments();
                poiLayersData.getMissedPointsLayer().getPoints().forEach(pair -> {
                    ((Shape) pair.getValue()).setFill(Color.TRANSPARENT);
                    pair.getValue().setVisible(false);
                });
                poiLayersData.getMissedPointsLayer().getPoints().clear();
                resetButtons(btnMissed);
                break;
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
            poiLayersData.getFocusedNode().setFill(Color.RED);
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
            poiLayersData.getFocusedNode().setFill(Color.RED);
        poiLayersData.setFocusedPair(p);
        ((Shape) p.getValue()).setFill(Color.BLUE);
        mapViewController.setFocusedColor(Color.RED);
        mapViewController.showDistanceToSelectedPoint();
        mapViewController.flyTo(((MapPoint) p.getKey()));
    }

    public void clickMyposition() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected() & !poiLayersData.getTempPointLayer().getPoints().isEmpty()) {
            MapPoint t = poiLayersData.getTempPoint();
            if (!poiLayersData.getMyPosPointLayer().getPoints().isEmpty()) {
                poiLayersData.getMyPosPointLayer().getPoints().getFirst().getValue().setVisible(false);
                poiLayersData.getMyPosPointLayer().getPoints().clear();
            }
            MapPoint p = new MapPoint(t.getLatitude(), t.getLongitude());
            p.setPointType(MapPoint.PointType.MYPOS);
            poiLayersData.getMyPosPointLayer().addPoint(p, new Circle(9, Color.AQUAMARINE));
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
    }

    public void clickTriangul() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected()) {
            if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
            MapPoint t = poiLayersData.getTempPoint();
            Polygon polygon = new Polygon();
            polygon.getPoints().addAll(
                    0.0, -7.0,
                    -7.0, 7.0,
                    7.0, 7.0);
            polygon.setFill(Color.BLACK);
            MapPoint p = new MapPoint(t.getLatitude(), t.getLongitude());
            p.setCommand(MapPoint.Commands.NOWEAPON);
            p.setPointType(MapPoint.PointType.TRIANG);
            poiLayersData.getTriangPointsLayer().addPoint(p, polygon);
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
    }

    public void clickDownload() {
        switch (buttonPressedType){
            case NONE:
                buttonPressedType = ButtonType.DOWNLOAD;
                btnDownload.setSelected(true);
                somethingPressed = true;
                break;
            case DOWNLOAD:
                resetButtons(btnDownload);
                poiLayersData.rectangle.setWidth(0);
                poiLayersData.rectangle.setHeight(0);
                appLogicController.selectToDownload();
                break;
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
        // Use laser rangefinder, compass and your position point on the map to mark a landmark.
    }

    public void clickGun() {
        if (somethingPressed) return;
        if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
        MapPoint t = poiLayersData.getTempPoint();
        MapPoint wp = new MapPoint(t.getLatitude(), t.getLongitude());
        wp.setCommand(MapPoint.Commands.READY);
        wp.setPointType(MapPoint.PointType.GUN);
        poiLayersData.getWeaponPointsLayer().addPoint(wp, new Circle(9, Color.ORCHID));
        poiLayersData.getTempPointLayer().deleteTempPoint();
    }

    public void clickMark() {
        if (somethingPressed) return;
        if (!mapViewController.isPointSelected()) {
            if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
            MapPoint t = poiLayersData.getTempPoint();
            Circle circle = new Circle(10);
            circle.setFill(Color.TRANSPARENT);
            circle.setStroke(Color.AQUA);
            circle.setStrokeWidth(4);
            MapPoint p = new MapPoint(t.getLatitude(), t.getLongitude());
            p.setCommand(MapPoint.Commands.NOWEAPON);
            p.setPointType(MapPoint.PointType.MARK);
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
        switch (buttonPressedType){
            case NONE:
                setInfo("Select two line points,\n than press LINE again.");
                if (poiLayersData.getTempPointLayer().getPoints().size() != 0)
                    poiLayersData.getTempPointLayer().deleteTempPoint();
                btnLine.setSelected(true);
                somethingPressed = true;
                buttonPressedType = ButtonType.LINE;
                poiLayersData.getLineStartEndPoints().clear();
                break;
            case LINE:
                resetButtons(btnLine);
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
                break;
        }
    }

    MapPoint aimingWeapon;
    boolean targSelected = false;

    public void clickAim() {
        if (!mapViewController.isPointSelected()) {
            resetButtons(btnAim);
            return;
        }
        switch (buttonPressedType) {
            case NONE:
                if (poiLayersData.getFocusedPoint().getPointType().equals(MapPoint.PointType.GUN)) {
                    buttonPressedType = ButtonType.AIM;
                    btnAim.setSelected(true);
                    somethingPressed = true;
                    Optional<Pair<MapPoint, Node>> op = poiLayersData.getWeaponPointsLayer().getPoints().stream().filter(pair -> pair.getValue() == poiLayersData.getFocusedNode()).findFirst();
                    op.ifPresent(mapPointNodePair -> aimingWeapon = mapPointNodePair.getKey());
                    setInfo("Select target and than\n press AIM.");
                } else {
                    resetButtons(btnAim);
                    setInfo("Select weapon, than press\n AIM and select target.");
                    return;
                }
                break;
            case AIM:
                if (targSelected) {
                    if (aimingWeapon.getCommand().equals(MapPoint.Commands.BUSY)){ // Re aim weapon
                        appLogicController.clearWeaponAiming(aimingWeapon);
                        appLogicController.aimWeaponOnTarget(poiLayersData.getFocusedPoint(), aimingWeapon);
                        resetButtons(btnAim);
                    } else {
                        appLogicController.aimWeaponOnTarget(poiLayersData.getFocusedPoint(), aimingWeapon);
                        resetButtons(btnAim);
                    }
                } else {
                    setInfo("Cancel.");
                    targSelected = false;
                    resetButtons(btnAim);
                    break;
                }
        }
    }

    public void clickMove() {
        setInfo("Not implemented yet.");
    }

    /**
     * Reset button view, reset something pressed boolean and pressed type to NONE
     * @param b
     */
    private void resetButtons(ToggleButton b) {
        b.setSelected(false);
        somethingPressed = false;
        buttonPressedType = ButtonType.NONE;
    }

    /**
     * Mark
     * @param readyFire
     */
    void setReadyFire(boolean readyFire) {
        mapViewController.readyFire = readyFire;
    }

    /**
     * Set map controller.
     * @param view
     */
    public void setMapViewController(MapViewController view) {
        this.mapViewController = view;
    }

    /**
     * Set poi layer container
     * @param poiLayersData
     */
    public void setPoiLayersData(PoiLayersData poiLayersData) {
        this.poiLayersData = poiLayersData;
    }

    /**
     * Set application logic controller that will do application calculations, decisions and so on.
     * @param appLogicController
     */
    public void setAppLogicController(AppLogicController appLogicController) {
        this.appLogicController = appLogicController;
    }

    public void clickUnit() {
        setInfo("Not implemented yet.");
    }



    void setLatitude(double latitude) {
        df_coord_dir.setRoundingMode(RoundingMode.CEILING);
        txtLat.setText(String.valueOf(df_coord_dir.format(latitude)) + " lat");
    }

    void setLongitude(double longitude) {
        df_coord_dir.setRoundingMode(RoundingMode.CEILING);
        txtLon.setText(String.valueOf(df_coord_dir.format(longitude)) + " lon");
    }

    void setButtonText(String buttonText) {
        btnTarget.setText(buttonText);
    }

    void setDirection(double direction) {
        df_coord_dir.setRoundingMode(RoundingMode.CEILING);
        txtDir.setText(String.valueOf(df_coord_dir.format(direction)) + "\u00b0");
    }

    void setDistance(double distance) {
        DecimalFormat df_dist = new DecimalFormat("###,###,###");
        txtDist.setText(String.valueOf(df_dist.format(distance)) + " m");
    }

    void setInfo(String info) {
        txtInfo.setText(info);
        txtInfo.selectPositionCaret(txtInfo.getLength());
        txtInfo.deselect();
    }

    void eraseFields() {
        txtInfo.setText("");
        txtDist.setText("");
        txtDir.setText("");
        txtLon.setText("");
        txtLat.setText("");
        btnTarget.setText("TARG");
    }
}
