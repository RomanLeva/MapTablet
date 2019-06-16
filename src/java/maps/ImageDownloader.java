package maps;
import data.MapPoint;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageDownloader {
    private static final Logger logger = Logger.getLogger(ImageDownloader.class.getName());
    private static final int TIMEOUT = 5000;
    private static final String host = "http://tile.openstreetmap.org/";
    private static File cacheRoot;
    private static boolean hasFileCache;
    static {
        try {
            System.setProperty("http.agent", "Chrome");
            String home = System.getProperty("user.home");
            File storageRoot = new File(home, ".maptablet");
            cacheRoot = new File(storageRoot, ".maps");
            hasFileCache = true;
            logger.info("Available for downloading map tiles");
        } catch (Exception ex) {
            hasFileCache = false;
            logger.log(Level.SEVERE, null, ex);
        }
    }
    static ReadOnlyDoubleProperty fillImage(ImageView imageView, int zoom, long i, long j) {
        Image image = fromFileCache(zoom, i, j);
        if (image == null) {
            String urlString = host + zoom + "/" + i + "/" + j + ".png";
            image = new Image(urlString, true); // load image from an OSM server and than return its loading progress property
        }
        imageView.setImage(image);
        return image.progressProperty();
    }

    // Return an image from file cache, or null if the cache doesn't contain the image
    static private Image fromFileCache(int zoom, long i, long j) {
        if (zoom == 4) { // Load the base tiles from the resources
            URL tile = ImageDownloader.class.getClassLoader().getResource("tiles/" + zoom + "/" + i + "/" + j + ".png");
            if (tile != null) {
                return new Image(tile.toString(), true);
            }
        }
        if (hasFileCache) {
            String tag = zoom + File.separator + i + File.separator + j + ".png";
            File f = new File(cacheRoot, tag);
            if (f.exists()) {
                return new Image(f.toURI().toString(), true);
            }
        }
        return null;
    }

    public static void downloadSelectedSquareToCacheFolder(MapPoint tnFirst, MapPoint tnSecond) throws IllegalAccessException {
        if (!hasFileCache) throw new IllegalAccessException();
        new Thread(() -> Downloader.downloadSelectedSquareToCacheFolder(tnFirst, tnSecond)).start();
    }

    private static class Downloader {
        private static void downloadSelectedSquareToCacheFolder(MapPoint tnFirst, MapPoint tnSecond) {
            for (int z = 4; z <= 14; z++) { // Start load tile layers from the 4 zoom level to 14 (for testing).  4 level is because you already have the base 3 levels
                long[] tnF = getTileNumberFromMapPoint(tnFirst, z);
                long[] tnS = getTileNumberFromMapPoint(tnSecond, z);
                for (long xn = tnF[0]; xn <= tnS[0]; xn++) {
                    for (long yn = tnF[1]; yn <= tnS[1]; yn++) {
                        download(z, xn, yn);
                    }
                }
            }
        }

        private static long[] getTileNumberFromMapPoint(MapPoint point, double zoom) {
            long[] tn = new long[2];
            long n = (long) Math.pow(2, zoom); // n - is number of tiles in current zoom level
            double lat_rad = Math.PI * point.getLatitude() / 180; //latitude in radians
            tn[0] = (long) (n * ((180 + point.getLongitude()) / 360)); //X-tile номер
            tn[1] = (long) (n * (1 - (Math.log(Math.tan(lat_rad) + 1 / Math.cos(lat_rad)) / Math.PI)) / 2);//Y-tile номер
            return tn;
        }

        private static void download(int zoom, long xn, long yn) {
            URLConnection openConnection;
            InputStream inputStream = null;
            FileOutputStream fos = null;
            try {
                URL url = new URL(host + zoom + "/" + xn + "/" + yn + ".png");
                openConnection = url.openConnection();
                openConnection.setConnectTimeout(TIMEOUT);
                openConnection.setReadTimeout(TIMEOUT);
                openConnection.connect();
            } catch (IOException e) {
                logger.fine("Maybe no connection");
                return;
            }
            try {
                inputStream = openConnection.getInputStream();
                String enc = File.separator + zoom + File.separator + xn + File.separator + yn + ".png";
                File candidate = new File(cacheRoot, enc);
                candidate.getParentFile().mkdirs();
                fos = new FileOutputStream(candidate);
                byte[] buff = new byte[4096];
                int len = inputStream.read(buff);
                while (len > 0) {
                    fos.write(buff, 0, len);
                    len = inputStream.read(buff);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException ex) {
                    logger.log(Level.WARNING, null, ex);
                }
            }
        }
    }
}