package data;
import javafx.beans.NamedArg;

import java.io.Serializable;

public class MapPoint implements Serializable {
    private double latitude, longitude;
    private long id;
    public enum Commands {
        TARGET, READY, BUSY, FIRE, ACKNOWLEDGE, ADJUSTMENT, STOP, OVER, DESTROYED, NOWEAPON
    }
    private Commands command;

    public MapPoint(@NamedArg("latitude") double lat, @NamedArg("longitude") double lon) {
        this.latitude = lat;
        this.longitude = lon;
    }

    public double getLatitude() {
        return this.latitude;
    }

    public double getLongitude() {
        return this.longitude;
    }

    public void update(double lat, double lon) {
        this.latitude = lat;
        this.longitude = lon;
    }

    public Commands getCommand() {
        return command;
    }

    public void setCommand(Commands command) {
        this.command = command;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}