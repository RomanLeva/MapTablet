package maps;
import com.sun.javafx.tk.Toolkit;
import controller.MapViewController;
import data.MapPoint;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.logging.Logger;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;

/**
 * This is the top logic element of maps component, provides the underlying maptiles.
 * On top of this, additional layers can be rendered.
 */
public class BaseMap extends Group {
    private static final Logger logger = Logger.getLogger(BaseMap.class.getName());
    private static final int MAX_ZOOM = 20; //The maximum zoom level open street map supports.
    private final Map<Long, SoftReference<MapTile>>[] tiles = new HashMap[MAX_ZOOM]; // Cache for map tiles
    private double lat;
    private double lon;
    private boolean abortedTileLoad;
    private final Rectangle area;
    private final ReadOnlyDoubleWrapper centerLon = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper centerLat = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper();
    private final DoubleProperty prefCenterLon = new SimpleDoubleProperty();
    private final DoubleProperty prefCenterLat = new SimpleDoubleProperty();
    private final DoubleProperty prefZoom = new SimpleDoubleProperty();
    private double x0, y0;
    private boolean dirty = true;
    private ChangeListener<Scene> sceneListener;

    public BaseMap() {
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new HashMap<>();
        }
        area = new Rectangle(0, 0, 0, 0);
        area.setVisible(false);
        prefCenterLat.addListener(o -> doSetCenter(prefCenterLat.get(), prefCenterLon.get()));
        prefCenterLon.addListener(o -> doSetCenter(prefCenterLat.get(), prefCenterLon.get()));
        prefZoom.addListener(o -> doZoom(prefZoom.get()));
        ChangeListener<Number> resizeListener = (o, oldValue, newValue) -> markDirty();
        area.widthProperty().addListener(resizeListener);
        area.heightProperty().addListener(resizeListener);
        area.translateXProperty().bind(translateXProperty().multiply(-1));
        area.translateYProperty().bind(translateYProperty().multiply(-1));
        if (sceneListener == null) {
            sceneListener = (o, oldScene, newScene) -> {
                if (newScene != null) {
                    getParent().layoutBoundsProperty().addListener(e -> {
                        area.setWidth(getParent().getLayoutBounds().getWidth());
                        area.setHeight(getParent().getLayoutBounds().getHeight());
                    });
                    markDirty();
                }
                if (abortedTileLoad) {
                    abortedTileLoad = false;
                    doSetCenter(lat, lon);
                }
            };
        }
        this.sceneProperty().addListener(sceneListener);
    }

    //Move the center of this map to the specified coordinates
    public void setCenter(double lat, double lon) {
        prefCenterLat.set(lat);
        prefCenterLon.set(lon);
    }

    public void setCenter(Point2D center) {
        prefCenterLat.set(center.getX());
        prefCenterLon.set(center.getY());
    }

    public Point2D getCenter() {
        return new Point2D(prefCenterLat.get(), prefCenterLon.get());
    }

    private void doSetCenter(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        if (getScene() == null) {
            abortedTileLoad = true;
            return;
        }
        double activeZoom = zoom.get();
        double n = Math.pow(2, activeZoom);
        double lat_rad = Math.PI * lat / 180;
        double id = n / 360. * (180 + lon);
        double jd = n * (1 - (Math.log(Math.tan(lat_rad) + 1 / Math.cos(lat_rad)) / Math.PI)) / 2;
        double mex = id * 256;
        double mey = jd * 256;
        double ttx = mex - this.getMyWidth() / 2;
        double tty = mey - this.getMyHeight() / 2;
        setTranslateX(-1 * ttx);
        setTranslateY(-1 * tty);
        logger.config("setCenter, tx = " + this.getTranslateX() + ", with = " + this.getMyWidth() / 2 + ", mex = " + mex);
        markDirty();
    }

    /**
     * Move the center of the map horizontally by a number of pixels. After this
     * operation, it will be checked if new tiles need to be downloaded
     *
     * @param dx the number of pixels
     */
    public void moveX(double dx) {
        setTranslateX(getTranslateX() - dx);
        markDirty();
    }

    /**
     * Move the center of the map vertically by a number of pixels. After this
     * operation, it will be checked if new tiles need to be downloaded
     *
     * @param dy the number of pixels
     */
    public void moveY(double dy) {
        double z = zoom.get();
        double maxty = 256 * Math.pow(2, z) - getMyHeight();
        logger.config("ty = " + getTranslateY() + " and dy = " + dy);
        if (getTranslateY() <= 0) {
            if (getTranslateY() + maxty >= 0) {
                setTranslateY(Math.min(0, getTranslateY() - dy));
            } else {
                setTranslateY(-maxty + 1);
            }
        } else {
            setTranslateY(0);
        }
        markDirty();
    }

    public void setZoom(double z) {
        logger.fine("setZoom called");
        prefZoom.set(z);
    }

    private void doZoom(double z) {
        zoom.set(z);
        doSetCenter(this.lat, this.lon);
        markDirty();
    }

    public void zoom(double delta, double pivotX, double pivotY) {
        double zf = 1 - Math.pow(2, delta);// zoom in = (-1), zoom out = 1/2
        if ((zoom.get() == 19 & zf == -1) | (zoom.get() == 4 & zf == 0.5)) return;
        double totX = (pivotX + (-getTranslateX())) * zf;
        double totY = (pivotY + (-getTranslateY())) * zf;
        if ((delta > 0)) { // zooming in
            if (zoom.get() < MAX_ZOOM) {
                setTranslateX(getTranslateX() + totX); // Translate X and Y becomes bigger with going to the smaller scale (by zoom()), because the map begin to contain more tiles.
                setTranslateY(getTranslateY() + totY);
                zoom.set(zoom.get() + delta);
                markDirty();
            }
        } else if (zoom.get() > 1) { // zooming out, if zoom = 2, no need to zoom out more
            if (Math.pow(2, zoom.get() + delta) * 256 > getMyHeight()) {
                setTranslateX(getTranslateX() + totX);
                setTranslateY(getTranslateY() + totY);
                zoom.set(zoom.get() + delta);
                markDirty();
            }
        }
    }

    public MapPoint getMapPosition(double sceneX, double sceneY) {
        final SimpleDoubleProperty _lat = new SimpleDoubleProperty();
        final SimpleDoubleProperty _lon = new SimpleDoubleProperty();
        calculateCoords(sceneX - getTranslateX(), sceneY - getTranslateY(), _lat, _lon);
        return new MapPoint(_lat.get(), _lon.get());
    }

    public Point2D getMapPointFromDegreesToXY(double lat, double lon) {
        if (this.getScene() == null) return null;
        //Широта Lon от экватора на север (+) или на юг (-), долгота от Гринвича на запад (-) или восток (+), n - is number of tiles in current zoom level
        long n = (long) Math.pow(2, zoom.get()); //X goes from 0 (left edge is 180 °W) to 2^zoom − 1 (right edge is 180 °E) Y goes from 0 (top edge is 85.0511 °N) to 2^zoom − 1 (bottom edge is 85.0511 °S) in a Mercator projection
        double lat_rad = Math.PI * lat / 180; //долгота в радианах
        double xtn = n * ((180 + lon) / 360); //X-tile номер
        double ytn = n * (1 - (Math.log(Math.tan(lat_rad) + 1 / Math.cos(lat_rad)) / Math.PI)) / 2;//Y-tile номер
        double x = this.getTranslateX() + (xtn * 256);//256 пикселей размер листа карты, получиться координата в пикселях для отображения на Сцене (номер тайла дробный то шо точка на тайле не четко сидит). Номер тайла зависит от масштаба, кол-во тайлов на данном масштабе = 2^zoom.
        double y = this.getTranslateY() + (ytn * 256);//Транслейт используется для смещения точки учитывая экран отображения, например если экран в лев. верхн. углу карты то точка рисуется на расстоянии Х, У от угла экрана. Если экран сместился вправо (а точнее карта под экраном влево), то точка будет (Х - смещение) от угла экрана.
        return new Point2D(x, y);
    }

    public MapPoint getMapPointFromXYtoDegrees(double x, double y) {
        x -= this.getTranslateX(); // minuses because every translating is negative, so - on - is +
        y -= this.getTranslateY(); // translating added if yor view window is displaced from left-up corner
        double xtile = x / 256;
        double ytile = y / 256; // tile number, 256 pixels is size of one map tile
        long n = (long) Math.pow(2, zoom.get()); // number of tiles that available on this zoom level
        double lon = xtile * 360 / n - 180;
        double lat = Math.atan(Math.sinh(Math.PI - (2 * Math.PI * ytile) / n)) * (180 / Math.PI);
//        System.out.println("lat " + lat + " " + "lon " + lon);
        return new MapPoint(lat, lon);
    }

    // Used for tile addressing when downloading the selected region
    public long[] getTileNumberFromMapPoint(MapPoint point, double zoom) {
        long[] tn = new long[2];
        long n = (long) Math.pow(2, zoom); // n - is number of tiles in current zoom level
        double lat_rad = Math.PI * point.getLatitude() / 180; //latitude in radians
        tn[0] = (long) (n * ((180 + point.getLongitude()) / 360)); //X-tile номер
        tn[1] = (long) (n * (1 - (Math.log(Math.tan(lat_rad) + 1 / Math.cos(lat_rad)) / Math.PI)) / 2);//Y-tile номер
        return tn;
    }

    private int[] getTileNumbers(double lat, double lon) {
        int[] result = new int[2];
        int n = (int) Math.pow(2, zoom.get());
        int xtile = (int) Math.floor((lon + 180) / 360 * n);
        int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * n);
        //тайлы нумеруются от 0 до (2^zoom - 1) по вертикали и по горизонтали поэтому округляем и ограничиваем
        if (xtile < 0)
            xtile = 0;
        if (xtile >= n)
            xtile = (n - 1);
        if (ytile < 0)
            ytile = 0;
        if (ytile >= n)
            ytile = (n - 1);
        result[0] = xtile;
        result[1] = ytile;
        return result;
//        return ("" + zoom + "/" + xtile + "/" + ytile);//это окончание адресса тайла для сервера типа https://tile.openstreetmap.org/z/x/y
    }

    public ReadOnlyDoubleProperty centerLon() {
        return centerLon.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty centerLat() {
        return centerLat.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty zoom() {
        return zoom.getReadOnlyProperty();
    }

    public DoubleProperty prefCenterLon() {
        return prefCenterLon;
    }

    public DoubleProperty prefCenterLat() {
        return prefCenterLat;
    }

    private void loadTiles() {
        long max = (long) Math.pow(2, zoom.get()); // In the selected zoom there are (2^zoom) tiles. Each tile is 256*256 pixels. The Map has only 20 zoom levels.
        long xmin = Math.max(0, (long) (-getTranslateX() / 256) - 1); //trX is the screen offset, for example if your screen offsets left on 1024 pixels / 256, you skipped 4 map tiles, you need
        long ymin = Math.max(0, (long) (-getTranslateY() / 256) - 1); // to start loading skipping those 4 tiles numeric addresses
        long xmax = Math.min(max, xmin + (long) (getMyWidth() / 256) + 3); // You need to download only tiles that can be placed in your screen plus some more
        long ymax = Math.min(max, ymin + (long) (getMyHeight() / 256) + 3); // Load max tile number corresponding to screen size
        for (long i = xmin; i < xmax; i++) {
            for (long j = ymin; j < ymax; j++) {
                Long key = i * max + j; // tiles are stored with the key, they will be retrieved by it too
                SoftReference<MapTile> ref = tiles[(int) zoom.get()].get(key);
                if ((ref == null) || (ref.get() == null)) {
                    MapTile tile = new MapTile(this, (int) zoom.get(), i, j); //Create tiles, they will be downloaded from file or network when created
                    tiles[(int) zoom.get()].put(key, new SoftReference<>(tile));
                    getChildren().add(tile);
                } else {
                    MapTile tile = ref.get();
                    if (!getChildren().contains(tile)) {
                        getChildren().add(tile);
                    }
                }
            }
        }
        cleanupTiles();
    }

    /**
     * Return a specific tile
     */
    private MapTile findTile(int zoom, long i, long j) {
        Long key = i * (1 << zoom) + j;
        SoftReference<MapTile> exists = tiles[zoom].get(key);
        return (exists == null) ? null : exists.get();
    }

    private void cleanupTiles() {
        List<MapTile> toRemove = new LinkedList<>();
        ObservableList<Node> children = this.getChildren();
        for (Node child : children) {
            if (child instanceof MapTile) {
                MapTile tile = (MapTile) child;
                boolean intersects = tile.getBoundsInParent().intersects(area.getBoundsInParent());
                if (!intersects) {
                    boolean loading = tile.loading();
                    if (!loading) {
                        toRemove.add(tile);
                    }
                } else if (tile.getZoomLevel() > ceil(zoom.get())) {
                    toRemove.add(tile);
                } else if ((tile.getZoomLevel() < floor(zoom.get() + 0.2)) && (!(ceil(zoom.get()) >= MAX_ZOOM))) {
                    toRemove.add(tile);
                }
            }
        }
        getChildren().removeAll(toRemove);
        logger.fine("DONE CLEANUP " + getChildren().size());
    }

    private void clearTiles() {
        List<Node> toRemove = new ArrayList<>();
        ObservableList<Node> children = this.getChildren();
        for (Node child : children) {
            if (child instanceof MapTile) {
                toRemove.add(child);
            }
        }
        getChildren().removeAll(children);
        for (int i = 0; i < tiles.length; i++) {
            tiles[i].clear();
        }
    }

    /**
     * Called by the JavaFX Application Thread when a pulse is running.
     * In case the dirty flag has been set, we know that something has changed
     * and we need to reload/clean the tiles.
     */
    @Override
    protected void layoutChildren() {
        if (dirty) {
            loadTiles();
            dirty = false;
        }
        super.layoutChildren();
    }

    private void calculateCenterCoords() {
        double x = ((MapViewController) this.getParent()).getWidth() / 2 - this.getTranslateX();
        double y = ((MapViewController) this.getParent()).getHeight() / 2 - this.getTranslateY();
        calculateCoords(x, y, centerLat, centerLon);
    }

    private void calculateCoords(double x, double y, SimpleDoubleProperty lat, SimpleDoubleProperty lon) {
        double z = zoom.get();
        double latrad = Math.PI - (2.0 * Math.PI * y) / (Math.pow(2, z) * 256.);
        double mlat = Math.toDegrees(Math.atan(Math.sinh(latrad)));
        double mlon = x / (256 * Math.pow(2, z)) * 360 - 180;
        lon.set(mlon);
        lat.set(mlat);
    }

    /**
     * When something changes that would lead to a change in UI representation
     * (e.g. map is dragged or zoomed), this method should be called.
     * This method will NOT update the map immediately, but it will set a
     * flag and request a next pulse.
     * This is much more performant than redrawing the map on each input event.
     */
    private void markDirty() {
        this.dirty = true;
        calculateCenterCoords();
        this.setNeedsLayout(true);
        Toolkit.getToolkit().requestNextPulse();
    }

    private double getMyWidth() {
        return this.getParent().getLayoutBounds().getWidth();
    }

    private double getMyHeight() {
        return this.getParent().getLayoutBounds().getHeight();
    }

    public double getX0() {
        return x0;
    }

    public void setX0(double x0) {
        this.x0 = x0;
    }

    public double getY0() {
        return y0;
    }

    public void setY0(double y0) {
        this.y0 = y0;
    }
}