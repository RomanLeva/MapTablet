package controller;
public interface GUIController {
    void setInfo(String info);
    void setLatitude(String latitude);
    void setLongitude(String longitude);
    void setButtonText(String buttonText);
    void setDirection(String direction);
    void setDistance(String distance);
    void eraseFields();
}
