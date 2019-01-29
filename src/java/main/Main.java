package main;
import controller.AppLogicController;
import controller.JfxGuiController;
import controller.MapViewController;
import data.PoiLayersData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.NetworkClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.logging.Logger;

public class Main extends Application {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    @Override
    public void start(Stage primaryStage) {
        try {
            ApplicationContext apc = new ClassPathXmlApplicationContext("/Beans.xml");
            MapViewController mapViewController = (MapViewController) apc.getBean("mapview");
            mapViewController.setZoom(4);
            AppLogicController logic = (AppLogicController) apc.getBean("logic");
            PoiLayersData poiLayersData = (PoiLayersData) apc.getBean("poilayersdata");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui.fxml"));
            Parent root = loader.load();
            JfxGuiController myJfxGuiController = loader.getController();
            myJfxGuiController.setMapViewController(mapViewController);
            myJfxGuiController.setAppLogicController(logic);
            myJfxGuiController.setPoiLayersData(poiLayersData);
            myJfxGuiController.borderPane.setCenter(mapViewController);
            mapViewController.setGuiController(myJfxGuiController);
            logic.setGuiController(myJfxGuiController);
            primaryStage.setTitle("Map Tablet");
            primaryStage.setScene(new Scene(root));
            primaryStage.setOnCloseRequest(event -> {
                Platform.exit();
                System.exit(0);
            });
            primaryStage.show();
        } catch (Exception e) {
            logger.warning(e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
