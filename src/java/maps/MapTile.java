package maps;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

class MapTile extends Region {
    private static final Logger logger = Logger.getLogger(MapTile.class.getName());
    private final int myZoom;
    private final long i, j;
    private final BaseMap baseMap;
    private InvalidationListener zl = o -> calculatePosition();
    private ReadOnlyDoubleProperty progress;

    MapTile(BaseMap baseMap, int nearestZoom, long i, long j) {
        this.baseMap = baseMap;
        this.myZoom = nearestZoom;
        this.i = i;
        this.j = j;
        ImageView iv = new ImageView();
        iv.setMouseTransparent(true);
        this.progress = ImageRetriever.fillImage(iv, myZoom, i, j);
        getChildren().addAll(iv); // Add tile image to JFX Node to be rendered
        this.progress.addListener(o -> { // Each map tile has it's loading progress, if progress becomes 1 -> requestNextPulse() from the JFX Parent to redraw its child Nodes
            if (this.progress.get() == 1.) {
                this.setNeedsLayout(true);
            }
        });
        // When map zooming or screen scrolling occurs, each map tile position need to be recalculated. When the app. is starting, it do some action to trigger this events and distribute base tiles.
        baseMap.zoom().addListener(new WeakInvalidationListener(zl));
        baseMap.translateXProperty().addListener(new WeakInvalidationListener(zl));
        baseMap.translateYProperty().addListener(new WeakInvalidationListener(zl));
        calculatePosition(); // calculates tile position on the screen
        this.setMouseTransparent(true);
    }

    boolean loading() {
        return !(progress.greaterThanOrEqualTo(1.)).get();
    }

    int getZoomLevel() {
        return myZoom;
    }

    private void calculatePosition() {
        this.setVisible(baseMap.zoom().get() == myZoom);
        logger.fine("visible tile " + this + "? " + this.isVisible() + (this.isVisible() ? " covering? " : ""));
        setTranslateX(256 * i); // each tile is 256 pixels, setTranslate will move the tile from upper left corner of the parent Node to 256*X
        setTranslateY(256 * j);
    }

    @Override
    public String toString() {
        return "tile with z = " + myZoom + " [" + i + "," + j + "]";
    }

}