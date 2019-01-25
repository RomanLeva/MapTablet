package network;
import com.google.gson.Gson;
import controller.AppLogicController;
import data.MapPoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.util.ResourceLeakDetector;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SerializationUtils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class NetworkClient {
    private static final Logger logger = Logger.getLogger(NetworkClient.class.getName());
    private final String secretWord = "uebanskie_uebani"; // the needed bit length keyword used in the both client and server
    private Cipher encodecipher, decodecipher;
    private String host;
    private int port;
    private Channel channel; // Channel to HeadQuarters server
    private AppLogicController applicationLogic;
    private ExecutorService es = Executors.newSingleThreadExecutor();
    private MyClientHandler myClientHandler = new MyClientHandler();
    private ObjectEncoder encoder = new ObjectEncoder();

    public NetworkClient(AppLogicController applicationLogic) {
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

    public void establishConnection(String host, String port) {
        this.host = System.getProperty("host", host);
        this.port = Integer.parseInt(System.getProperty("port", port));
        es.submit(this::bootStrapConnection); // Run in a new thread
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
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(
                                    encoder,
                                    new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                    myClientHandler);
                        }
                    });
            channel = b.connect(host, port).sync().channel();
            channel.closeFuture().sync();
        } catch (Exception e) {
            applicationLogic.displayMessage("No connection!", true);
        } finally {
            channel = null;
            group.shutdownGracefully();
        }
    }

    public Channel getChannel() {
        return channel;
    }

    // Class implementing channel actions
    @ChannelHandler.Sharable
    private class MyClientHandler extends ChannelDuplexHandler {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            applicationLogic.displayMessage("Connection ready!", false);
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
                applicationLogic.processIncomingMessage(point);
            } catch (IOException | ClassNotFoundException e) {
                logger.warning(e.getMessage());
            } catch (IllegalBlockSizeException | BadPaddingException c) {
                // do nothing, abort maybe enemy messages
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
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            applicationLogic.displayMessage("Connection broken!" ,true);
            channel = null;
            ctx.close();
        }
    }
}
