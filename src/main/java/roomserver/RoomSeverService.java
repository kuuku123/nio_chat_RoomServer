package roomserver;

import room.Room;
import user.Client;
import util.MyLog;
import util.OperationEnum;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class RoomSeverService
{
    private final Logger logr;
    ByteBuffer adminReadBuf = ByteBuffer.allocate(10000);
    ByteBuffer adminWriteBuf = ByteBuffer.allocate(10000);
    AsynchronousChannelGroup channelGroup;
    AsynchronousServerSocketChannel masterRoomSocketChannel;
    AsynchronousSocketChannel adminServerSocketChannel;
    private Object for_sendTextProcess = new Object();
    private Object for_enterRoomProcess = new Object();
    private Object for_inviteRoomProcess = new Object();
    private Object for_quitRoomProcess = new Object();

    public static List<Client> masterClientList = new Vector<>();
    public static List<Room> masterRoomList = new Vector<>();

    public RoomSeverService()
    {
        logr = MyLog.getLogr();
    }



    void startServer(int openPort, int adminPort)
    {
        try
        {
            channelGroup = AsynchronousChannelGroup.withFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    Executors.defaultThreadFactory()
            );
            masterRoomSocketChannel = AsynchronousServerSocketChannel.open(channelGroup);
            masterRoomSocketChannel.bind(new InetSocketAddress(openPort));
            logr.info("[서버 연결됨]");
        }
        catch (Exception e)
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
            adminServerSocketChannel.connect(new InetSocketAddress("127.0.0.1", 5001), null, new CompletionHandler<Void, Object>()
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


    public void adminReceive()
    {
        adminServerSocketChannel.read(adminReadBuf, adminReadBuf, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {

                processOp(adminReadBuf);
                if(adminServerSocketChannel.isOpen()) adminServerSocketChannel.read(adminReadBuf,adminReadBuf,this);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment)
            {
                logr.info("[admin server 연결 끊김]");
            }
        });
    }

    public void adminSend()
    {
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

    void processOp(ByteBuffer attachment)
    {
        attachment.flip();
        ServerProcess serverProcess = new ServerProcess(attachment, for_sendTextProcess, for_enterRoomProcess, for_inviteRoomProcess, for_quitRoomProcess);
        int reqId = serverProcess.getReqId();
        int operation = serverProcess.getOperation();
        String userId = serverProcess.getUserId();
        int roomNum = serverProcess.getRoomNum();
        OperationEnum op = OperationEnum.fromInteger(operation);
        switch (op)
        {
            case login:
//                serverProcess.loginProcess(reqId, operation, userId, attachment);
//                logr.info("login process completed");
                return;
            case logout:
//                serverProcess.logoutProcess(reqId, operation, userId, attachment);
//                logr.info("logout process completed");
                return;
            case sendText:
//                serverProcess.sendTextProcess(reqId, operation, userId, attachment);
                return;
            case fileUpload:
            case fileList:
            case fileDownload:
            case fileDelete:
            case createRoom:
                serverProcess.createRoomProcess(reqId, operation, userId, attachment);
                return;
            case quitRoom:
//                serverProcess.quitRoomProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case inviteRoom:
//                serverProcess.inviteRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case roomUserList:
            case roomList:
//                serverProcess.roomListProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enterRoom:
//                serverProcess.enterRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enrollFile:

        }
    }



    public static void main(String[] args)
    {
        RoomSeverService roomSeverService = new RoomSeverService();
        int port = Integer.parseInt(args[0]);
        int adminPort = Integer.parseInt(args[1]);
        roomSeverService.startServer(port, adminPort);
    }

}
