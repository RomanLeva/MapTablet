package controller;
import data.MapPoint;
import data.PoiLayersData;
import io.netty.channel.Channel;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.util.Pair;
import maps.BaseMap;
import maps.ImageRetriever;
import network.NetworkClient;

import java.util.Optional;

public class AppLogicController {
    private NetworkClient client;
    private JfxGuiController guiController;
    private PoiLayersData poiLayersData;
    private Channel channel;
    private BaseMap baseMap;

    public AppLogicController(BaseMap baseMap) {
        this.baseMap = baseMap;
    }

    void pushPointToHQ(MapPoint point) {
        if (testConnection()) {
            channel.writeAndFlush(point);
            Platform.runLater(() -> displayMessage("Pushing to server.", false));
        }
    }

    public void processIncomingMessage(MapPoint point) {
        switch (point.getCommand()) {
            case READY: {
                Platform.runLater(() -> {
                    guiController.txtInfo.setText(guiController.txtInfo.getText() + "\n" + "Gun ready!");
                    Optional<Pair<MapPoint, Node>> ot = poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                            p.getKey().getLatitude() == point.getLatitude() & p.getKey().getLongitude() == point.getLongitude()).findFirst();
                    ot.ifPresent(mapPointNodePair -> {
                        mapPointNodePair.getKey().setCommand(MapPoint.Commands.READY);
                        mapPointNodePair.getKey().setId(point.getId());
                    });
                    MapPoint current = ((MapPoint) poiLayersData.getFocusedPair().getKey());
                    if (current.getLatitude() == point.getLatitude() & current.getLongitude() == point.getLongitude()) {
                        guiController.btnTarget.setText("FIRE!");
                        guiController.readyFire = true;
                    }
                });
                break;
            }
            case NOWEAPON: {
                Platform.runLater(() -> guiController.txtInfo.setText(guiController.txtInfo.getText() + "\n" + "Out of 30 km range / no gun."));
                break;
            }
        }
    }

    void calculateAndPushAdjustments() {
        int allX = 0, allY = 0;
        for (Pair<MapPoint, Node> p : poiLayersData.getMissedPointsLayer().getPoints()) {
            Point2D point2D = baseMap.getMapPointFromDegreesToXY(p.getKey().getLatitude(), p.getKey().getLongitude());
            allX += point2D.getX();
            allY += point2D.getY();
        }
        int middleX = allX / poiLayersData.getMissedPointsLayer().getPoints().size();
        int middleY = allY / poiLayersData.getMissedPointsLayer().getPoints().size();
        MapPoint mapPoint = baseMap.getMapPointFromXYtoDegrees(middleX, middleY);
        mapPoint.setCommand(MapPoint.Commands.ADJUSTMENT); // Middle point of all missed points
        long currentTargetID = ((MapPoint) poiLayersData.getFocusedPair().getKey()).getId();
        mapPoint.setId(currentTargetID);
        pushPointToHQ(mapPoint);
    }

    void selectToDownload() {
        MapPoint firstCornerPoint = poiLayersData.getDownloadCornerPoints().get(0);
        MapPoint secondCornerPoint = poiLayersData.getDownloadCornerPoints().get(1);
        try {
            ImageRetriever.downloadSelectedSquareToCacheFolder(firstCornerPoint, secondCornerPoint);
            displayMessage("Downloading...", true);
        } catch (IllegalAccessException e) {
            displayMessage("No access to file system.", true);
        }
    }

    private boolean testConnection() {
        channel = client.getChannel();
        if (channel != null) {
            return channel.isWritable();
        } else {
            displayMessage("No connection!", true);
            return false;
        }
    }

    public void displayMessage(String msg, boolean clearBefore) {
        if (clearBefore) {
            guiController.txtInfo.clear();
        } else {
            guiController.txtInfo.setText(guiController.txtInfo.getText() + "\n" + msg);
            guiController.txtInfo.selectPositionCaret(guiController.txtInfo.getLength());
            guiController.txtInfo.deselect();
        }
    }

    void createConnection() {
        client.establishConnection("127.0.0.1", "8007"); //Will run the client in a new thread
    }

    public void setClient(NetworkClient client) {
        this.client = client;
    }

    public void setGuiController(JfxGuiController guiController) {
        this.guiController = guiController;
    }

    public void setPoiLayersData(PoiLayersData poiLayersData) {
        this.poiLayersData = poiLayersData;
    }
}
