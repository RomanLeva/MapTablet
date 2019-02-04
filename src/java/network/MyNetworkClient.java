package network;
import data.MapPoint;

public interface MyNetworkClient {
    void connectToHeadQuarters(String host, String port);
    void createHeadQuarters(String port);
    void pushCommandPointByChannel(MapPoint point, Object channel);
    void spreadPointAmongOthers(MapPoint point);
}
