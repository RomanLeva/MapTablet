package maps;
import javafx.scene.Parent;

/**
 * A maps.MapLayer can be added on top a maps.BaseMap (which provides the map tiles).
 * MapLayers contain specific functionality that is rendered by overriding the
 * {@link #layoutLayer()} method.
 * <p>
 * There are 2 reasons why the {@link #layoutLayer() } will be called:
 * <ul>
 * <li>The maps.MapLayer {@link #layoutLayer() } method will be called by the controller.MapViewController
 * in case the coordinates (center/zoom) are changed.
 * <li>When the content of the maps.MapLayer implementation changes (e.g. a POI is
 * added or moved), it should call the {@link #markDirty() } method.
 * This will mark this layer dirty and request it to be recalculated in the next
 * Pulse.
 * </ul>
 * <p>
 * The maps.MapLayer has access to the {@link #baseMap} instance that renders
 * the map tiles and it can use the methods provided by the {@link BaseMap}
 */
public class MapLayer extends Parent {
    private boolean dirty = false;
    protected BaseMap baseMap;
    /**
     * Only the controller.MapViewController should call this method. We want implementations to
     * access the maps.BaseMap (since they need to be able to act on changes in
     * center/zoom values) but they can not modify it.
     *
     * @param baseMap
     */
    public final void setBaseMap(BaseMap baseMap) {
        this.baseMap = baseMap;
        initialize();
    }
    /**
     * This method is called by the framework when the maps.MapLayer is created and
     * added to the Map. At this point, it is safe to use the
     * <code>baseMap</code> and its fields.
     * The default implementation doesn't do anything. It is up to specific
     * layers to add layer-specific initialization.
     */
    protected void initialize() {
    }
    /**
     * Implementations should call this function when the content of the data
     * has changed. It will set the <code>dirty</code> flag, and it will
     * request the layer to be reconsidered during the next pulse.
     */
    public void markDirty() {
        this.dirty = true;
        this.requestLayout();
    }
    @Override
    protected void layoutChildren() {
        if (dirty) {
            layoutLayer();
        }
    }
    /**
     * This method is called when a Pulse is running and it is detected that
     * the layer should be redrawn, as a consequence of an earlier call to
     * {@link #markDirty() } (which should happen in case the info in the
     * specific layer has changed) or when the { com.gluonhq.maps.controller.MapViewController}
     * has its dirty flag set to true (which happens when the map is moved/zoomed).
     * The default implementation doesn't do anything. It is up to specific
     * layers to add layer-specific rendering.
     */
    public void layoutLayer() {
    }
}