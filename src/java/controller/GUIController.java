package controller;
public interface GUIController {
    void setInfo(String info);
    void setLatitude(double latitude);
    void setLongitude(double longitude);
    void setButtonText(String buttonText);
    void setDirection(double direction);
    void setDistance(double distance);
    void eraseFields();
    void setReadyFire(boolean readyFire);
}
