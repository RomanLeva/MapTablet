package network;
import com.google.gson.Gson;
import controller.AppLogicController;
import data.MapPoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.util.ResourceLeakDetector;
import javafx.application.Platform;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SerializationUtils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class NetworkDuplexClient implements NetworkClient {
    private static final Logger logger = Logger.getLogger(NetworkDuplexClient.class.getName());
    private final String secretWord = "uebanskie_uebani"; // the needed bit length keyword used in the both client and server
    private Cipher encodecipher, decodecipher;
    private String host;
    private int port;
    private ArrayList<Channel> channels = new ArrayList<>(); // List of channels connected if using app as server
    private Channel channel; // Channel to HeadQuarters server if using app as client
    private AppLogicController applicationLogic;
    private ExecutorService es = Executors.newSingleThreadExecutor();
    private MyDuplexHandler myDuplexHandler = new MyDuplexHandler();
    private EventLoopGroup bossGroup, workerGroup;
    private ObjectEncoder encoder = new ObjectEncoder();

    public NetworkDuplexClient(AppLogicController applicationLogic) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        try {
            this.applicationLogic = applicationLogic;
            byte[] encryptionKeyBytes = secretWord.getBytes();
            SecretKey secretKey = new SecretKeySpec(encryptionKeyBytes, "AES");
            encodecipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            decodecipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            encodecipher.init(Cipher.ENCRYPT_MODE, secretKey);
            decodecipher.init(Cipher.DECRYPT_MODE, secretKey);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | KeyException e) {
            logger.warning(e.getMessage());
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public ArrayList<Channel> getChannels() {
        return channels;
    }

    @Override
    public void connectToHeadQuarters(String host, String port) {
        this.host = System.getProperty("host", host);
        this.port = Integer.parseInt(System.getProperty("port", port));
        es.submit(this::bootStrapConnection); // Run in a new thread
    }

    @Override
    public void createHeadQuarters(String port) {
        this.port = Integer.parseInt(System.getProperty("port", port));
        es.submit(this::bootStrapServer);
    }

    @Override
    public void pushCommandPointByChannel(MapPoint point, Object channel) {
        if (channel instanceof ChannelHandlerContext){
            ((ChannelHandlerContext) channel).channel().writeAndFlush(point, ((ChannelHandlerContext) channel).voidPromise());
        } else if (channel instanceof Channel){
            ((Channel) channel).writeAndFlush(point, ((Channel) channel).voidPromise());
        }
        Platform.runLater(() -> applicationLogic.displayMessage("Point pushed."));
    }

    @Override
    public void spreadPointAmongOthers(MapPoint point) {
        channels.forEach(chan -> chan.writeAndFlush(point, chan.voidPromise()));
        Platform.runLater(() -> applicationLogic.displayMessage("Points pushed."));
    }

    // Creates server from bootstrap
    private void bootStrapServer() {
        logger.info("Initiating server...");
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            try {
                                channels.add(ch); // Every connected client (or lower HeadQuarter) is in channels list. Server will response by this channels.
                                ChannelPipeline p = ch.pipeline();
                                p.addLast(
                                        encoder,
                                        new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                        myDuplexHandler);
                            } catch (Exception e) {
                                logger.warning(e.getMessage());
                            }
                        }
                    });
            b.bind(port).sync().channel().closeFuture().sync();// Bind to port number and start to accept incoming connections.
        } catch (Exception e) {
            applicationLogic.displayMessage("No connection!");
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    // Creates connection from bootstrap, initializes the channel of this connection
    private synchronized void bootStrapConnection() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            logger.info("Initiating client...");
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(
                                    encoder,
                                    new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                    myDuplexHandler);
                        }
                    });
            channel = b.connect(host, port).sync().channel(); // Channel to upper HeadQuarters
            channel.closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
            applicationLogic.displayMessage("No connection!");
        } finally {
            channel = null;
            group.shutdownGracefully();
        }
    }

    // Class implementing channel actions
    @ChannelHandler.Sharable
    private class MyDuplexHandler extends ChannelDuplexHandler {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            applicationLogic.displayMessage("Channel active!");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                Byte[] byteObject = ((Byte[]) msg);
                byte[] bytes = ArrayUtils.toPrimitive(byteObject);
                byte[] decryptedMessageBytes = decodecipher.doFinal(bytes);
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(decryptedMessageBytes));
                Gson gson = new Gson();
                String jsonInString = (String) ois.readObject();
                MapPoint point = gson.fromJson(jsonInString, MapPoint.class);
                applicationLogic.processIncomingMessage(point, ctx);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException | BadPaddingException c) {
                // do nothing
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                String gs = new Gson().toJson((MapPoint) msg);
                // encoding object with cipher and send it as serializable Byte[]
                ctx.writeAndFlush(ArrayUtils.toObject(encodecipher.doFinal(SerializationUtils.serialize(gs))), ctx.voidPromise());
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            channels.remove(ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            applicationLogic.displayMessage("Connection broken!");
            channel = null;
            ctx.close();
        }
    }
}
