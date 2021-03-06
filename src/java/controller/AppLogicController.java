package controller;
import data.LinesLayer;
import data.MapPoint;
import data.PoiLayersData;
import data.PointsLayer;
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
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class AppLogicController {
    private NetworkDuplexClient client;
    boolean usedAsHQ = true; // false if used as server
    private JfxGuiController guiController;
    private PoiLayersData poiLayersData;
    private Channel channel;
    private BaseMap baseMap;
    private Map<MapPoint, ChannelHandlerContext> targetChannelMap = new HashMap<>();
    private MultiValuedMap<MapPoint, MapPoint> targetWeaponMap = new ArrayListValuedHashMap<>();
    private static AtomicLong ID = new AtomicLong(1);

    public AppLogicController(BaseMap baseMap, PoiLayersData poiLayersData) {
        this.baseMap = baseMap;
        this.poiLayersData = poiLayersData;
    }

    /**
     * Main application logic method, all events from remote client are processing here!
     *
     * @param point
     * @param channelContext
     */
    public synchronized void processIncomingMessage(MapPoint point, ChannelHandlerContext channelContext) {
        switch (point.getCommand()) {
            case TARGET: {
                createTargetPointOnMap(point);
                MapPoint weapon = nearestGunToTarget(point);// If there is a weapon in 30 km range, return it, else return special command point NOWEAPON
                if (!weapon.getCommand().equals(MapPoint.Commands.NOWEAPON)) { // Watch is this weapon is near enough to fire.
                    weaponReady(point, weapon);
                    targetChannelMap.put(point, channelContext);
                    client.pushCommandPointByChannel(point, channelContext); // Response to spotter client READY command
                } else { // Any weapon in your department, send point to others.
                    point.setCommand(MapPoint.Commands.NOWEAPON);
                    client.pushCommandPointByChannel(point, channelContext); // Response to spotter client NOWEAPON command
                }
                break;
            }
            case ADJUSTMENT: {
                calculateAdjustments(point);
                break;
            }
            case DESTROYED: {
                Platform.runLater(() -> {
                    destroyPoint(point);
                    targetChannelMap.remove(point);
                    client.pushCommandPointByChannel(point, targetChannelMap.get(point));
                });
                break;
            }
            case STOP: {
                break;
            }
            case FIRE: {
                if (channelContext == null & targetChannelMap.containsKey(point)) {
                    client.pushCommandPointByChannel(point, targetChannelMap.get(point));
                    break;
                }
                poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                        l.getKey().getValue().getLatitude() == point.getLatitude() & l.getKey().getValue().getLongitude() == point.getLongitude()).forEach(l -> {
                    ((Line) l.getValue()).setStroke(Color.ORANGE);
                });
                poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                        p.getKey().getLatitude() == point.getLatitude() & p.getKey().getLongitude() == point.getLongitude()).findFirst()
                        .ifPresent(mapPointNodePair -> {
                            mapPointNodePair.getKey().setCommand(MapPoint.Commands.FIRE);
                            guiController.setButtonText("FIRE!");
                        });
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

    /**
     * Another main application logic method, all events from user input are processing here!
     *
     * @param point
     */
    void processUserInputMessage(MapPoint point) {
        switch (point.getCommand()) {
            case TARGET: {
                MapPoint weapon = nearestGunToTarget(point);// If there is a weapon in 30 km range, return it, else return special command point NOWEAPON
                if (!weapon.getCommand().equals(MapPoint.Commands.NOWEAPON)) { // Watch is this weapon is near enough to fire.
                    aimWeaponOnTarget(point, weapon); // Response to gun from your department, do all the calculations!
                } else { // No near weapon in your department. Push it to upper HeadQuarters if application used as spotter or local HQ.
                    if (!usedAsHQ) {
                        client.pushCommandPointByChannel(point, client.getChannel());
                    } else displayMessage("Out of 30 km range\n or no gun.");
                }
                break;
            }
            case ADJUSTMENT: {
                if (!usedAsHQ) {
                    client.pushCommandPointByChannel(point, client.getChannel());
                } else calculateAdjustments(point);
                break;
            }
            case DESTROYED: {
                Platform.runLater(() -> destroyPoint(point));
                if (!usedAsHQ) client.pushCommandPointByChannel(point, client.getChannel());
                break;
            }
            case STOP: {
                break;
            }
            case FIRE: {
                poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                        l.getKey().getValue().getLatitude() == point.getLatitude() & l.getKey().getValue().getLongitude() == point.getLongitude()).forEach(l -> {
                    ((Line) l.getValue()).setStroke(Color.ORANGE);
                });
                if (!usedAsHQ) {
                    client.pushCommandPointByChannel(point, client.getChannel());
                } else {
                    displayMessage("Fire!");
                    // Push here the command directly to the cannoneer from your department... somehow.
                }
                break;
            }
            case READY: {
                gunReady(point);
                break;
            }
            case NOWEAPON: {
                Platform.runLater(() -> displayMessage("Out of 30 km range\n or no gun."));
                break;
            }
        }
    }

    /**
     * Send this point to others
     *
     * @param point
     * @param context
     */
    private void spreadPoint(MapPoint point, ChannelHandlerContext context) {
        if (context == null) { // context == null if target created by the user input, else if came from network from an other spotter
            if (usedAsHQ & client.getChannel() != null) {
                client.getChannel().writeAndFlush(point, client.getChannel().voidPromise()); // Push the new target to the Head quarters ! Inside the HQ it will spread among others if needed.
            } else
                client.spreadPointAmongOthers(point); // Target created by user input, spread it among other clients.
        } else {
            MapPoint p = new MapPoint(0, 0);
            p.setCommand(MapPoint.Commands.NOWEAPON);
            client.pushCommandPointByChannel(p, context); // Send NOWEAPON response back, they will decide what to do.
        }
    }

    private void weaponReady(MapPoint target, MapPoint weapon) {
        weapon.setCommand(MapPoint.Commands.BUSY);
        target.setCommand(MapPoint.Commands.READY);
        targetWeaponMap.put(target, weapon);
        poiLayersData.getWeaponsAdjustmentsMap().put(weapon, new ArrayList<>());
        calculateAiming(weapon, target);
        Platform.runLater(() -> poiLayersData.getLinesLayer().addLine(weapon, target, Color.BLACK)); // Draw line between points
    }

    private void gunReady(MapPoint point) {
        Platform.runLater(() -> {
            displayMessage("Gun ready!");
            Optional<Pair<MapPoint, Node>> ot = poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                    p.getKey().getLatitude() == point.getLatitude() & p.getKey().getLongitude() == point.getLongitude()).findFirst();
            ot.ifPresent(mapPointNodePair -> {
                mapPointNodePair.getKey().setCommand(MapPoint.Commands.READY);
                mapPointNodePair.getKey().setId(point.getId());
            });
            MapPoint current = ((MapPoint) poiLayersData.getFocusedPair().getKey());
            if (current != null && current.getLatitude() == point.getLatitude() & current.getLongitude() == point.getLongitude()) {
                guiController.setButtonText("FIRE!");
                guiController.setReadyFire(true);
            }
        });
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
        displayMessage("Aiming adjusted.");
        if (targetChannelMap.containsKey(focusedTarg)) {
            client.pushCommandPointByChannel(mapPoint, targetChannelMap.get(focusedTarg));
        } else processUserInputMessage(mapPoint);
    }

    private void destroyPoint(MapPoint point) {
        for (MapLayer layer : poiLayersData.getLayers()) {
            if (layer instanceof LinesLayer) continue;
            Optional<Pair<MapPoint, Node>> op = ((PointsLayer) layer).getPoints().stream().filter(pair -> pair.getKey().getLatitude() == point.getLatitude() & pair.getKey().getLongitude() == point.getLongitude()).findFirst();
            if (op.isPresent()) {
                Pair<MapPoint, Node> pair = op.get();
                MapPoint selected = pair.getKey();
                ((Shape) pair.getValue()).setFill(Color.TRANSPARENT);
                pair.getValue().setVisible(false);
                List<Pair<Pair<MapPoint, MapPoint>, Node>> toRemove = new ArrayList<>(1);
                Optional<Pair<MapPoint, Node>> ot = poiLayersData.getTargetPointsLayer().getPoints().stream().filter(p ->
                        p.getKey().getLatitude() == selected.getLatitude() & p.getKey().getLongitude() == selected.getLongitude()).findFirst();
                if (ot.isPresent()) { // target point was selected, so delete its corresponding data
                    if (targetWeaponMap.containsKey(selected)) {
                        poiLayersData.getLinesLayer().getLines().stream().filter(l -> l.getKey().getValue().getLatitude() == selected.getLatitude() & l.getKey().getValue().getLongitude() == selected.getLongitude())
                                .forEach(lo -> {
                                    poiLayersData.getWeaponsAdjustmentsMap().remove(lo.getKey().getKey());
                                    ((Line) lo.getValue()).setStroke(Color.TRANSPARENT);
                                    pair.getValue().setVisible(false);
                                    toRemove.add(lo);
                                });
                        poiLayersData.getLinesLayer().getLines().remove(toRemove.get(0));
                        toRemove.clear();
                        targetWeaponMap.get(selected).forEach(w -> {
                            w.setCommand(MapPoint.Commands.READY);
                        });
                        targetWeaponMap.remove(selected);
                        guiController.setInfo("Target destroyed.");
                    }
                }
                Optional ow = poiLayersData.getWeaponPointsLayer().getPoints().stream().filter(p ->
                        p.getKey().getLatitude() == selected.getLatitude() & p.getKey().getLongitude() == selected.getLongitude()).findFirst();
                if (ow.isPresent()) { // weapon point was selected, so delete its corresponding data
                    Optional<Pair<Pair<MapPoint, MapPoint>, Node>> lo = poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                            l.getKey().getKey().getLatitude() == selected.getLatitude() & l.getKey().getKey().getLongitude() == selected.getLongitude()).findFirst();
                    lo.ifPresent(pairNodePair -> {// delete line pointer if it was
                        poiLayersData.getWeaponsAdjustmentsMap().get(pairNodePair.getKey().getKey()).clear();
                        ((Line) pairNodePair.getValue()).setStroke(Color.TRANSPARENT);
                        pair.getValue().setVisible(false);
                        toRemove.add(pairNodePair);
                    });
                    poiLayersData.getLinesLayer().getLines().remove(toRemove.get(0));
                }
                ((PointsLayer) layer).getPoints().remove(pair);
            }
        }
    }

    /**
     * Aim weapon on that target!
     *
     * @param target
     * @param weapon
     */
    void aimWeaponOnTarget(MapPoint target, MapPoint weapon) {
        weapon.setCommand(MapPoint.Commands.BUSY);
        target.setCommand(MapPoint.Commands.READY);
        targetWeaponMap.put(target, weapon);
        poiLayersData.getWeaponsAdjustmentsMap().put(weapon, new ArrayList<>());
        calculateAiming(weapon, target);
        Platform.runLater(() -> {
            poiLayersData.getLinesLayer().addLine(weapon, target, Color.BLACK);
        }); // Draw line between points
        displayMessage("Gun ready!");
        MapPoint current = (poiLayersData.getFocusedPoint());
        if (current.getLatitude() == target.getLatitude() & current.getLongitude() == target.getLongitude()) {
            guiController.setButtonText("FIRE!");
            guiController.setReadyFire(true);
        }
    }

    void clearWeaponAiming(MapPoint weapon) {
        List<MapPoint> pn = new ArrayList<>(1);
        List<Pair<Pair<MapPoint, MapPoint>, Node>> ln = new ArrayList<>(1);
        poiLayersData.getLinesLayer().getLines().stream().filter(l ->
                (l.getKey().getKey().getLatitude() == weapon.getLatitude() & l.getKey().getKey().getLongitude() == weapon.getLongitude())).findFirst().
                ifPresent(l -> {
                    ln.add(l); // add line to remove list
                    poiLayersData.getWeaponsAdjustmentsMap().remove(l.getKey().getKey());
                    ((Line) l.getValue()).setStroke(Color.TRANSPARENT);
                    l.getValue().setVisible(false);
                });
        targetWeaponMap.asMap().forEach((t, w) -> {
            w.stream().filter(wp -> wp.getLongitude() == weapon.getLongitude() & wp.getLatitude() == weapon.getLatitude()).forEach(wp -> {
                pn.add(t); // add target to remove list
                t.setCommand(MapPoint.Commands.TARGET);
            });
        });
        if (pn.size() > 0) targetWeaponMap.remove(pn.get(0));
        if (ln.size() > 0) poiLayersData.getLinesLayer().getLines().remove(ln.get(0));
    }

    private void createTargetPointOnMap(MapPoint point) {
//        ID.compareAndSet(Long.MAX_VALUE, 1);
        point.setId(ID.getAndIncrement());
        Platform.runLater(() -> { // Draw circle on the map
            Circle circle = new Circle(9, Color.RED);
            poiLayersData.getTargetPointsLayer().addPoint(point, circle);
        });
    }

    private synchronized MapPoint nearestGunToTarget(MapPoint target) {
        ArrayDeque<MapPoint> wp = new ArrayDeque<>(1);
        ArrayDeque<Double> dis = new ArrayDeque<>(1);
        poiLayersData.getWeaponPointsLayer().getPoints().stream().filter(p -> p.getKey().getCommand().equals(MapPoint.Commands.READY)).forEach(p -> {
            MapPoint weapon = p.getKey();
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
        });
        if (!dis.isEmpty() && dis.getFirst() <= 300000) { // 300 km //TODO 30 km
            return wp.getFirst();
        } else {
            MapPoint p = new MapPoint(0, 0);
            p.setCommand(MapPoint.Commands.NOWEAPON);
            return p;
        }
    }

    /**
     * Measure distance between two points
     *
     * @param weapon
     * @param target
     * @return
     */
    double distance(MapPoint weapon, MapPoint target) {
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
        ot.ifPresent(mapPointNodePair -> {
            MapPoint point = mapPointNodePair.getKey();
            try {
                Point2D adjust2d = baseMap.getMapPointFromDegreesToXY(adjustment.getLatitude(), adjustment.getLongitude());
                Point2D target2d = baseMap.getMapPointFromDegreesToXY(mapPointNodePair.getKey().getLatitude(), mapPointNodePair.getKey().getLongitude());
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
                targetWeaponMap.asMap().forEach((t, w) -> {
                    if (t == point) {
                        w.forEach(wpn -> {
                            calculateAiming(wpn, adj);
                        });
                    }
                });
            } catch (NullPointerException e) {
                // maybe weapon was deleted
            }
        });
    }

    void selectToDownload() {
        if (poiLayersData.getDownloadCornerPoints().size() == 0) return;
        MapPoint firstCornerPoint = poiLayersData.getDownloadCornerPoints().get(0);
        MapPoint secondCornerPoint = poiLayersData.getDownloadCornerPoints().get(1);
        try {
            ImageDownloader.downloadSelectedSquareToCacheFolder(firstCornerPoint, secondCornerPoint);
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
        client.connectToHeadQuarters("127.0.0.1", "8007"); //Will run the client in a new thread
    }

    void createHeadQuarter() {
        displayMessage("Creating server...");
        client.createHeadQuarters("8007");
    }

    public void setClient(NetworkDuplexClient client) {
        this.client = client;
    }

    public void setGuiController(JfxGuiController guiController) {
        this.guiController = guiController;
    }
}
