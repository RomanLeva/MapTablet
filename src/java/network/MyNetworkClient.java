package network;
import data.MapPoint;

public interface MyNetworkClient {
    void establishConnection(String host, String port);
    void createServer(String port);
    void pushCommandPointTo(MapPoint point, Object channelContext);
    void spreadPointAmongSpotters(MapPoint point);
}
