package maps;
import javafx.scene.Parent;

public class MapLayer extends Parent {
    private boolean dirty = false;
    protected BaseMap baseMap;
    public final void setBaseMap(BaseMap baseMap) {
        this.baseMap = baseMap;
        initialize();
    }
    protected void initialize() {
    }
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
    public void layoutLayer() {
    }
}