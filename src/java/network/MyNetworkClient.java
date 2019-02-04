package network;
import data.MapPoint;

public interface MyNetworkClient {
    void connectToHeadQuarters(String host, String port);
    void createHeadQuarters(String port);
    void pushCommandPointByChannel(MapPoint point, Object channelContext);
    void spreadPointAmongOthers(MapPoint point);
}
