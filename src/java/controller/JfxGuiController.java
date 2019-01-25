package controller;
import data.MapPoint;
import data.PoiLayersData;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.util.Pair;
import maps.MapLayer;
import maps.PoiLayer;

import java.util.LinkedList;
import java.util.Optional;

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
    public Button btnArrow;
    public Button btnLine;
    public Button btnRound;
    private MapViewController mapViewController; // default controller for onMap actions
    private AppLogicController appLogicController; // controller of entire app logic and decisions
    private PoiLayersData poiLayersData;
    private int targetMaxIndex, targetNextIndex = 0;
    boolean readyFire = false;

    public void clickTarget() {
        if (!mapViewController.isPointSelected()) {
            if (readyFire) {
                ((MapPoint) poiLayersData.getFocusedPair().getKey()).setCommand(MapPoint.Commands.FIRE);
                btnTarget.setText("WORKING");
                appLogicController.pushPointToHQ(((MapPoint) poiLayersData.getFocusedPair().getKey()));
            } else {
                if (poiLayersData.getTempPointLayer().getPoints().isEmpty()) return;
                MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
                t.setCommand(MapPoint.Commands.TARGET);
                poiLayersData.getTargetPointsLayer().addPoint(t, new Circle(7, Color.RED));
                poiLayersData.getTempPointLayer().deleteTempPoint();
                mapViewController.setFocusedColor(Color.RED);
                poiLayersData.setFocusedPair(poiLayersData.getTargetPointsLayer().getPoints().getFirst());
                ((Shape) poiLayersData.getTargetPointsLayer().getPoints().getFirst().getValue()).setFill(Color.BLUE);
                mapViewController.setPointSelected(true);
                appLogicController.pushPointToHQ(t);
            }
        } else if (readyFire) {
            ((MapPoint) poiLayersData.getFocusedPair().getKey()).setCommand(MapPoint.Commands.FIRE);
            btnTarget.setText("WORK");
            appLogicController.pushPointToHQ(((MapPoint) poiLayersData.getFocusedPair().getKey()));
        }
    }

    public void clickDestroyed() {
        if (mapViewController.isSelectingMissed()) {
            mapViewController.setSelectingMissed(false);
            btnMissed.setSelected(false);
            setDisabledButtons(false);
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
        Pair p = poiLayersData.getFocusedPair();
        for (MapLayer poiLayer : poiLayersData.getLayers()) {
            Optional op = ((PoiLayer) poiLayer).getPoints().stream().filter(mapPointNodePair -> mapPointNodePair == p).findFirst();
            if (op.isPresent()) {
                Pair pp = (Pair) op.get();
                MapPoint t = (MapPoint) pp.getKey();
                t.setCommand(MapPoint.Commands.DESTROYED);
                if (!((pp.getValue()) instanceof Polygon)) { // don't send triangular point
                    appLogicController.pushPointToHQ(t);
                }
                ((Shape) pp.getValue()).setFill(Color.TRANSPARENT);
                ((Shape) pp.getValue()).setVisible(false);
                ((PoiLayer) poiLayer).getPoints().remove(pp);
                poiLayersData.setFocusedPair(new Pair<>(null, null));
                mapViewController.setPointSelected(false);
                break;
            }
        }
    }

    public void clickMissed() {
        if (mapViewController.isPointSelected() && ((MapPoint) poiLayersData.getFocusedPair().getKey()).getCommand().equals(MapPoint.Commands.FIRE)) {
            if (!mapViewController.isSelectingMissed()) {
                mapViewController.setSelectingMissed(true);
                btnMissed.setSelected(true);
                setDisabledButtons(true);
            } else {
                setDisabledButtons(false);
                mapViewController.setSelectingMissed(false);
                btnMissed.setSelected(false);
                appLogicController.calculateAndPushAdjustments();
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
        if (!mapViewController.isPointSelected()) {
            MapPoint t = poiLayersData.getTempPointLayer().getPoints().get(0).getKey();
            if (!poiLayersData.getMyPosPointLayer().getPoints().isEmpty()) {
                poiLayersData.getMyPosPointLayer().getPoints().getFirst().getValue().setVisible(false);
                poiLayersData.getMyPosPointLayer().getPoints().clear();
            }
            poiLayersData.getMyPosPointLayer().addPoint(new MapPoint(t.getLatitude(), t.getLongitude()), new Circle(7, Color.VIOLET));
            poiLayersData.getTempPointLayer().deleteTempPoint();
        }
    }

    public void clickTriangul() {
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
            setDisabledButtons(true);
        } else {
            setDisabledButtons(false);
            mapViewController.setSelectingDownload(false);
            btnDownload.setSelected(false);
            poiLayersData.rectangle.setWidth(0);
            poiLayersData.rectangle.setHeight(0);
            appLogicController.selectToDownload();
        }
    }

    private void setDisabledButtons(boolean disable) {
        if (mapViewController.isSelectingMissed()) {
            btnTarget.setDisable(disable);
            btnPrev.setDisable(disable);
            btnFwd.setDisable(disable);
            btnTriangul.setDisable(disable);
            btnMyposition.setDisable(disable);
            btnDelete.setDisable(disable);
            btnDownload.setDisable(disable);
        } else {
            btnTarget.setDisable(disable);
            btnDestroyed.setDisable(disable);
            btnMissed.setDisable(disable);
            btnPrev.setDisable(disable);
            btnFwd.setDisable(disable);
            btnTriangul.setDisable(disable);
            btnMyposition.setDisable(disable);
            btnDelete.setDisable(disable);
            btnConnect.setDisable(disable);
        }
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

    public void clickConnect() {
        appLogicController.createConnection();
    }

    public void clickNotch(ActionEvent actionEvent) {
    }

    public void clickGun(ActionEvent actionEvent) {
    }

    public void clickMark(ActionEvent actionEvent) {
    }

    public void clickZoomOut() {
        poiLayersData.getDownloadCornerPoints().clear();
        poiLayersData.rectangle.setWidth(0);
        poiLayersData.rectangle.setHeight(0);
        mapViewController.setZoom(-1);
//        baseMap.zoom(t.getDeltaY() > 1 ? 1 : -1, t.getX(), t.getY());
    }

    public void clickZoomIn() {
        poiLayersData.getDownloadCornerPoints().clear();
        poiLayersData.rectangle.setWidth(0);
        poiLayersData.rectangle.setHeight(0);
        mapViewController.setZoom(1);
    }

    public void clickArrow(ActionEvent actionEvent) {
    }

    public void clickLine(ActionEvent actionEvent) {
    }

    public void clickRound(ActionEvent actionEvent) {
    }
}
