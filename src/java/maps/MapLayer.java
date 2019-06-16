package maps;
import javafx.scene.Group;

public class MapLayer extends Group {
    protected BaseMap baseMap;
    public final void setBaseMap(BaseMap baseMap) {
        this.baseMap = baseMap;
    }

    /**
     * Mark dirty happens when your app is changing points or lines position (for example if you zoom or add new point),
     * this method will request JFX thread to redraw them.
     * Than JFX will watch its children nodes list and recalculate each node position.
      */
    public void markDirty() {
        this.requestLayout(); // Request JFX animation pulse!
    }

    /**
     * Invoked by JFX animation thread
     */
    @Override
    public void layoutChildren() {
        layoutLayer();
    }

    /**
     * Method will be overridden in extended classes. Each layer will do recalculations, set translate property of its nodes and so on...
     */
    public void layoutLayer() {
    }
}