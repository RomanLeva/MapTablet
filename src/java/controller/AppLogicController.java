package controller;
import data.MapPoint;
import data.PoiLayersData;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import javafx.util.Pair;
import maps.*;
import network.NetworkDuplexClient;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class AppLogicController {
    private NetworkDuplexClient client;
    boolean usedAsClient = true; // false if used as server
    private GUIController guiController;
    private PoiLayersData poiLayersData;
    private Channel channel;
    private BaseMap baseMap;
    private Map<MapPoint, ChannelHandlerContext> targetChannelMap = new HashMap<>();
    private Map<MapPoint, MapPoint> targetWeaponMap = new HashMap<>();
    private static AtomicLong ID = new AtomicLong(1);

    public AppLogicController(BaseMap baseMap, PoiLayersData poiLayersData) {
        this.baseMap = baseMap;
        this.poiLayersData = poiLayersData;
    }

    // Main application logic method, all the events from UI input or remote client are processing here!
    public synchronized void processIncomingMessage(MapPoint point, ChannelHandlerContext channelContext) {
        switch (point.getCommand()) {
            case TARGET: {
                ID.compareAndSet(Long.MAX_VALUE, 1);
                point.setId(ID.getAndIncrement());
                Platform.runLater(() -> { // Draw circle on the map
                    if (channelContext != null) { // Message from network, for example new target from a spotter
                        Circle circle = new Circle(7, Color.RED);
                        poiLayersData.getTargetPointsLayer().addPoint(point, circle);
                    }
                });
                // If there is a weapon in 30 km range, return it, else return special command point NOWEAPON
                if (!poiLayersData.getWeaponPointsLayer().getPoints().isEmpty()) { // Test if there are some weapons in your department.
                    MapPoint weapon = nearestGunToTarget(point); // So, there is at most one weapon in your department.
                    if (!weapon.getCommand().equals(MapPoint.Commands.NOWEAPON)) { // Watch is this weapon is near enough to fire.
                        // So it is ready to fire!
                        weapon.setCommand(MapPoint.Commands.BUSY);
                        point.setCommand(MapPoint.Commands.READY);
                        targetWeaponMap.put(point, weapon);
                        poiLayersData.getWeaponsAdjustmentsMap().put(weapon, new ArrayList<>());
                        calculateAiming(weapon, point);
                        Platform.runLater(() -> poiLayersData.getLinesLayer().addLine(weapon, point, Color.BLACK)); // Draw line between points
                        if (channelContext == null) { // If context is null its means that target came from user input and not from other spotter client from the network.
                            processNewTarget(point, weapon); // Response to gun from your department, do all the calculations!
                        } else {
                            targetChannelMap.put(point, channelContext);
                            client.pushCommandPointTo(point, channelContext); // Response to other client
                        }
                    } else { // No near weapon in your department.
                        spreadPoint(point, channelContext);
                    }
                } else { // Any weapon in your dept.
                    if (channelContext == null) {
                        spreadPoint(point, null);
                    } else {
                        MapPoint n = new MapPoint(0, 0);
                        n.setCommand(MapPoint.Commands.NOWEAPON);
                        client.pushCommandPointTo(n, channelContext); // Response to other client
                    }
                }
                break;
            }
            case ADJUSTMENT: {
                calculateAdjustments(point);
                break;
            }
            case DESTROYED: {
                Platform.runLater(() -> destroyPoint(point));
                break;
            }
            case STOP: {
                break;
            }
            case FIRE: {
                if (channelContext == null & targetChannelMap.containsKey(point)) {
                    client.pushCommandPointTo(point, targetChannelMap.get(point));
                    break;
                }
                Optional<Pair<Pair<MapPoint, MapPoint>, Node>> lo = poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                        l.getKey().getValue().getLatitude() == point.getLatitude() & l.getKey().getValue().getLongitude() == point.getLongitude()).findFirst();
                lo.ifPresent(pairNodePair -> ((Line) pairNodePair.getValue()).setStroke(Color.ORANGE));
                // Next, push here the command directly to the cannoneer from your department... somehow.
                break;
            }
            case READY: {
                Platform.runLater(() -> {
                    displayMessage("Gun ready!");
                    Optional<Pair<MapPoint, Node>> ot = poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                            p.getKey().getLatitude() == point.getLatitude() & p.getKey().getLongitude() == point.getLongitude()).findFirst();
                    ot.ifPresent(mapPointNodePair -> {
                        mapPointNodePair.getKey().setCommand(MapPoint.Commands.READY);
                        mapPointNodePair.getKey().setId(point.getId());
                        targetChannelMap.put(mapPointNodePair.getKey(), channelContext);
                    });
                    MapPoint current = ((MapPoint) poiLayersData.getFocusedPair().getKey());
                    if (current != null && current.getLatitude() == point.getLatitude() & current.getLongitude() == point.getLongitude()) {
                        guiController.setButtonText("FIRE!");
                        guiController.setReadyFire(true);
                    }
                });
                break;
            }
            case NOWEAPON: {
                Platform.runLater(() -> displayMessage("Out of 30 km range\n or no gun."));
                break;
            }
        }
    }

    private void spreadPoint(MapPoint point, ChannelHandlerContext context) {
        if (context == null) { // context == null if target created by the user input, else if came from network from an other spotter
            if (usedAsClient & client.getChannel() != null) {
                client.getChannel().writeAndFlush(point, client.getChannel().voidPromise()); // Push the new target to the Head quarters ! Inside the HQ it will spread among others if needed.
            } else
                client.spreadPointAmongSpotters(point); // Target created by user input, spread it among other clients.
        } else {
            MapPoint p = new MapPoint(0, 0);
            p.setCommand(MapPoint.Commands.NOWEAPON);
            client.pushCommandPointTo(p, context); // Send NOWEAPON response back, they will decide what to do.
        }
    }

    private void processNewTarget(MapPoint target, MapPoint weapon) {
        displayMessage("Gun ready!");
        MapPoint current = ((MapPoint) poiLayersData.getFocusedPair().getKey());
        if (current.getLatitude() == target.getLatitude() & current.getLongitude() == target.getLongitude()) {
            guiController.setButtonText("FIRE!");
            guiController.setReadyFire(true);
        }
    }

    void processAdjustments() {
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
        MapPoint focusedTarg = ((MapPoint) poiLayersData.getFocusedPair().getKey());
        if (targetChannelMap.containsKey(focusedTarg)) {
            client.pushCommandPointTo(mapPoint, targetChannelMap.get(focusedTarg));
        } else processIncomingMessage(mapPoint, null);
    }

    private void destroyPoint(MapPoint point) {
        List<Pair> list = new LinkedList<>();
        for (MapLayer layer : poiLayersData.getLayers()) {
            if (layer instanceof LinesLayer) continue;
            if (list.size() > 0) break;
            ((PointsLayer) layer).getPoints().stream().filter(pair ->
                    pair.getKey().getLatitude() == point.getLatitude() & pair.getKey().getLongitude() == point.getLongitude()).forEach(list::add);
        }
        if (list.isEmpty()) return;
        Pair<MapPoint, Node> pair = list.get(0);
        MapPoint selected = pair.getKey();
        ((Shape) pair.getValue()).setFill(Color.TRANSPARENT);
        pair.getValue().setVisible(false);
        Optional<Pair<MapPoint, Node>> ot = poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                p.getKey().getLatitude() == selected.getLatitude() & p.getKey().getLongitude() == selected.getLongitude()).findFirst();
        if (ot.isPresent()) { // target point was selected
            if (targetChannelMap.containsKey(point)) client.pushCommandPointTo(point, targetChannelMap.get(point));
            if (targetWeaponMap.containsKey(selected)) {
                Optional<Pair<Pair<MapPoint, MapPoint>, Node>> lo = poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                        l.getKey().getValue().getLatitude() == selected.getLatitude() & l.getKey().getValue().getLongitude() == selected.getLongitude()).findFirst();
                if (lo.isPresent()) { // delete line pointer if it was
                    poiLayersData.getWeaponsAdjustmentsMap().get(lo.get().getKey().getKey()).clear();
                    ((Line) lo.get().getValue()).setStroke(Color.TRANSPARENT);
                    pair.getValue().setVisible(false);
                    poiLayersData.getLinesLayer().getLines().remove(lo.get());
                }
                MapPoint w = targetWeaponMap.get(selected);
                w.setCommand(MapPoint.Commands.READY);
                targetWeaponMap.remove(selected);
            }
            poiLayersData.getTargetPointsLayer().getPoints().remove(ot.get());
            try {
                targetChannelMap.remove(selected);
            } catch (NullPointerException e) {
            }
        }
        Optional ow = poiLayersData.getWeaponPointsLayer().getPoints().stream().filter(p ->
                p.getKey().getLatitude() == selected.getLatitude() & p.getKey().getLongitude() == selected.getLongitude()).findFirst();
        if (ow.isPresent()) { // weapon point was selected
            Optional<Pair<Pair<MapPoint, MapPoint>, Node>> lo = poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                    l.getKey().getKey().getLatitude() == selected.getLatitude() & l.getKey().getKey().getLongitude() == selected.getLongitude()).findFirst();
            if (lo.isPresent()) { // delete line pointer if it was
                poiLayersData.getWeaponsAdjustmentsMap().get(lo.get().getKey().getKey()).clear();
                ((Line) lo.get().getValue()).setStroke(Color.TRANSPARENT);
                pair.getValue().setVisible(false);
                poiLayersData.getLinesLayer().getLines().remove(lo.get());
            }
            poiLayersData.getWeaponPointsLayer().getPoints().remove(pair);
        }
    }

    private synchronized MapPoint nearestGunToTarget(MapPoint target) {
        ArrayDeque<MapPoint> wp = new ArrayDeque<>(1);
        ArrayDeque<Double> dis = new ArrayDeque<>(1);
        Optional<Pair<MapPoint, Node>> op = poiLayersData.getWeaponPointsLayer().getPoints().stream().filter(p -> p.getKey().getCommand().equals(MapPoint.Commands.READY)).findFirst();
        if (op.isPresent()) {
            MapPoint weapon = op.get().getKey();
            if (wp.isEmpty()) {
                wp.add(weapon);
                dis.add(distance(weapon, target));
            } else {
                double nextDist = distance(weapon, target);
                if (nextDist < dis.getFirst()) {
                    wp.removeFirst();
                    wp.add(weapon);
                    dis.removeFirst();
                    dis.add(nextDist);
                }
            }
        }
        if (!dis.isEmpty() && dis.getFirst() <= 300000) { // 300 km //TODO
            return wp.getFirst();
        } else {
            MapPoint p = new MapPoint(0, 0);
            p.setCommand(MapPoint.Commands.NOWEAPON);
            return p;
        }
    }

    private double distance(MapPoint weapon, MapPoint target) {
        double x_deg = Math.abs(weapon.getLongitude() - target.getLongitude());
        double mid = (weapon.getLatitude() + target.getLatitude()) / 2;
        double x = x_deg * 111120 * Math.abs(Math.cos(Math.toRadians(mid))); //cos то шо на экваторе поворот вектора по Х на точку равен самому себе... , а ближе к полюсу вектор короче на cos(широты) ибо WSG-84
        double y_deg = Math.abs(weapon.getLatitude() - target.getLatitude());
        double y = y_deg * 111120; //111120 м - средняя длина одного градуса широты
        return (double) Math.round(Math.sqrt(x * x + y * y));
    }

    private void calculateAiming(MapPoint weapon, MapPoint target) {
        boolean right = false, up = false;
        double direction = 0;
        double x_deg = Math.abs(weapon.getLongitude() - target.getLongitude());
        double mid = (weapon.getLatitude() + target.getLatitude()) / 2;
        double x = x_deg * 111120 * Math.abs(Math.cos(Math.toRadians(mid))); //cos то шо на экваторе поворот вектора по Х на точку равен самому себе... , а ближе к полюсу вектор короче на cos(широты) ибо WSG-84
        double y_deg = Math.abs(weapon.getLatitude() - target.getLatitude());
        double y = y_deg * 111120; //111120 м - средняя длина одного градуса широты
        double c = Math.round(Math.sqrt(x * x + y * y)); // Расстояние
        Point2D sel_point = baseMap.getMapPointFromDegreesToXY(target.getLatitude(), target.getLongitude());
        Point2D weapon_point = baseMap.getMapPointFromDegreesToXY(weapon.getLatitude(), weapon.getLongitude());
        if (sel_point.getX() >= weapon_point.getX()) right = true;
        if (sel_point.getY() <= weapon_point.getY()) up = true;
        if (right & up) {
            direction = 90 - Math.toDegrees(Math.acos(x / c));
        } else if (right & !up) {
            direction = 180 - Math.toDegrees(Math.acos(y / c));
        } else if (!up) {
            direction = 180 + Math.toDegrees(Math.acos(y / c));
        } else {
            direction = 270 + Math.toDegrees(Math.acos(x / c));
        }
        List<Double> l = poiLayersData.getWeaponsAdjustmentsMap().get(weapon);
        if (target.getCommand().equals(MapPoint.Commands.ADJUSTMENT)) l.clear();
        l.add(direction);
        l.add(c);
        Platform.runLater(() -> {
            guiController.setDirection(l.get(0));
            guiController.setDistance(l.get(1));
            guiController.setLongitude(weapon.getLongitude());
            guiController.setLatitude(weapon.getLatitude());
        });
    }

    private void calculateAdjustments(MapPoint adjustment) {
        Optional<Pair<MapPoint, Node>> ot = poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                p.getKey().getId() == adjustment.getId()).findFirst();
        if (ot.isPresent()) {
            try {
                Point2D adjust2d = baseMap.getMapPointFromDegreesToXY(adjustment.getLatitude(), adjustment.getLongitude());
                Point2D target2d = baseMap.getMapPointFromDegreesToXY(ot.get().getKey().getLatitude(), ot.get().getKey().getLongitude());
                MapPoint weaponToAdjust = targetWeaponMap.get(ot.get().getKey());
                double deltaX = Math.abs(target2d.getX() - adjust2d.getX());
                double deltaY = Math.abs(target2d.getY() - adjust2d.getY());
                boolean right = false, up = false;
                if (adjust2d.getX() >= target2d.getX()) right = true;
                if (adjust2d.getY() <= target2d.getY()) up = true;
                MapPoint adj;
                if (right & up) {
                    adj = baseMap.getMapPointFromXYtoDegrees(target2d.getX() - deltaX, target2d.getY() + deltaY);
                } else if (right & !up) {
                    adj = baseMap.getMapPointFromXYtoDegrees(target2d.getX() - deltaX, target2d.getY() - deltaY);
                } else if (!up) {
                    adj = baseMap.getMapPointFromXYtoDegrees(target2d.getX() + deltaX, target2d.getY() - deltaY);
                } else {
                    adj = baseMap.getMapPointFromXYtoDegrees(target2d.getX() + deltaX, target2d.getY() + deltaY);
                }
                adj.setCommand(MapPoint.Commands.ADJUSTMENT);
                calculateAiming(weaponToAdjust, adj);
            } catch (NullPointerException e) {
                // maybe weapon was deleted
            }
        }
    }

    void selectToDownload() {
        if (poiLayersData.getDownloadCornerPoints().size() == 0) return;
        MapPoint firstCornerPoint = poiLayersData.getDownloadCornerPoints().get(0);
        MapPoint secondCornerPoint = poiLayersData.getDownloadCornerPoints().get(1);
        try {
            ImageRetriever.downloadSelectedSquareToCacheFolder(firstCornerPoint, secondCornerPoint);
            displayMessage("Downloading...");
        } catch (IllegalAccessException e) {
            displayMessage("No access to file system.");
        }
    }

    private boolean testConnection() {
        channel = client.getChannel();
        if (channel != null) {
            return channel.isWritable();
        } else {
            displayMessage("No connection!");
            return false;
        }
    }

    public void displayMessage(String msg) {
        guiController.setInfo(msg);
    }

    void connectTo() {
        displayMessage("Connecting to...");
        client.establishConnection("127.0.0.1", "8007"); //Will run the client in a new thread
    }

    void createHeadQuarter() {
        displayMessage("Creating server...");
        client.createServer("8007");
    }

    public void setClient(NetworkDuplexClient client) {
        this.client = client;
    }

    public void setGuiController(GUIController guiController) {
        this.guiController = guiController;
    }
}
