import util.LogFormatter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class RoomSeverService
{
    private final static Logger logr = Logger.getGlobal();
    List<Room> masterRoomList = new Vector<>();
    AsynchronousChannelGroup channelGroup;
    AsynchronousServerSocketChannel masterRoomSocketChannel;
    AsynchronousSocketChannel adminServerSocketChannel;

    List<Client> masterClientList = new Vector<>();

    private static void setupLogger()
    {
        LogFormatter formatter = new LogFormatter();
        LogManager.getLogManager().reset();
        logr.setLevel(Level.ALL);

        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.INFO);
        ch.setFormatter(formatter);
        logr.addHandler(ch);
    }

    void startServer(int port, int adminPort)
    {
        try
        {
            channelGroup = AsynchronousChannelGroup.withFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    Executors.defaultThreadFactory()
            );
            masterRoomSocketChannel = AsynchronousServerSocketChannel.open(channelGroup);
            masterRoomSocketChannel.bind(new InetSocketAddress(port));
            logr.info("[서버 연결됨]");
        } catch (Exception e)
        {
            if (masterRoomSocketChannel.isOpen()) stopServer();
            logr.severe("[서버 연결 실패 startServer]");
            return;
        }

        masterRoomSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>()
        {
            @Override
            public void completed(AsynchronousSocketChannel socketChannel, Void attachment)
            {
                try
                {
                    logr.info("[연결 수락: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                } catch (IOException e)
                {
                    logr.severe("[Client 연결 도중에 끊김 accept IOException fail]");
                }


                Client client = new Client(socketChannel);
                masterClientList.add(client);
                logr.info("[연결 개수: " + masterClientList.size() + "]");

                masterRoomSocketChannel.accept(null, this);
            }

            @Override
            public void failed(Throwable exc, Void attachment)
            {
                if (masterRoomSocketChannel.isOpen()) stopServer();
                logr.severe("[Client 연결 안됨 accept fail]");
            }
        });

        try
        {
            adminServerSocketChannel = AsynchronousSocketChannel.open(channelGroup);
            adminServerSocketChannel.bind(new InetSocketAddress(adminPort));
            adminServerSocketChannel.connect(new InetSocketAddress("localhost", 5001), null, new CompletionHandler<Void, Object>()
            {
                @Override
                public void completed(Void result, Object attachment)
                {
                    try
                    {
                        logr.info("[admin Server 연결완료: " + adminServerSocketChannel.getRemoteAddress() + "]");

                    }
                    catch (IOException e)
                    {
                        logr.severe("[admin Server 연결실패]");
                    }
                    adminReceive();
                }

                @Override
                public void failed(Throwable exc, Object attachment)
                {
                    logr.severe("[서버 통신 안됨 connect fail]");
                }
            });
        }
        catch (IOException e)
        {
        } catch (NotYetConnectedException e)
        {
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    void stopServer()
    {
        try
        {
            masterClientList.clear();
            if (channelGroup != null && !channelGroup.isShutdown()) channelGroup.shutdown();
            logr.info("[서버 전체 종료]");
        } catch (Exception e)
        {
            logr.severe("[서버 전체 종료 실패]");
        }
    }


    void adminReceive()
    {
        ByteBuffer adminReadBuf = ByteBuffer.allocate(10000);
        adminServerSocketChannel.read(adminReadBuf, adminReadBuf, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {

            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment)
            {

            }
        });
    }

    void adminSend()
    {
        ByteBuffer adminWriteBuf = ByteBuffer.allocate(10000);
        adminServerSocketChannel.write(adminWriteBuf, adminWriteBuf, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {

            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment)
            {

            }
        });
    }

    class Client
    {
        AsynchronousSocketChannel socketChannel = null;
        ByteBuffer readBuffer = ByteBuffer.allocate(10000);
        ByteBuffer writeBuffer = ByteBuffer.allocate(10000);
        String userId = "not set yet";
        List<Room> myRoomList = new Vector<>();
        Room myCurRoom;

        Client(AsynchronousSocketChannel socketChannel)
        {
            this.socketChannel = socketChannel;
            receive();
        }

        void receive()
        {
            socketChannel.read(readBuffer, readBuffer, new CompletionHandler<Integer, ByteBuffer>()
            {
                @Override
                public void completed(Integer result, ByteBuffer attachment)
                {

                    readBuffer = ByteBuffer.allocate(10000);
                    readBuffer.clear();

                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment)
                {

                }
            });
        }

        void send()
        {
            socketChannel.write(writeBuffer, null, new CompletionHandler<Integer, Object>()
            {
                @Override
                public void completed(Integer result, Object attachment)
                {
                    writeBuffer = ByteBuffer.allocate(10000);
                    writeBuffer.clear();

                }

                @Override
                public void failed(Throwable exc, Object attachment)
                {

                }
            });
        }
    }


    class Room
    {
        int roomNum;
        String roomName;
        List<Client> clientList = new Vector<>();
        List<Text> chatLog = new ArrayList<>();
        int ip0;
        int ip1;
        int ip2;
        int ip3;
        int port;

        Room(int roomNum)
        {
            this.roomNum = roomNum;
        }

    }

    class Text
    {
        int textId;
        String sender;
        String text;
        Map<String, Integer> readCheck = new HashMap<>();
        int notRoomRead = 0;
        Room curRoom;

        public Text(int textId, String sender, String text)
        {
            System.out.println("test");
            this.textId = textId;
            this.sender = sender;
            this.text = text;
            for (Client c : curRoom.clientList)
            {
                if (c.socketChannel != null) readCheck.put(c.userId, 1);
                else
                {
                    readCheck.put(c.userId, 0);
                    notRoomRead++;
                }
            }
        }
    }

    public static void main(String[] args)
    {
        setupLogger();
        RoomSeverService roomSeverService = new RoomSeverService();
        int port = Integer.parseInt(args[0]);
        int adminPort = Integer.parseInt(args[1]);
        roomSeverService.startServer(port, adminPort);
    }

}
