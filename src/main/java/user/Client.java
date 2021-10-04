package user;

import room.Room;
import util.MyLog;
import util.OperationEnum;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;


public class Client
{
    private Logger logr;
    ByteBuffer readBuffer = ByteBuffer.allocate(10000);
    ByteBuffer writeBuffer = ByteBuffer.allocate(10000);
    private Object for_sendTextProcess = new Object();
    private Object for_enterRoomProcess = new Object();
    private Object for_inviteRoomProcess = new Object();
    private Object for_quitRoomProcess = new Object();

    private AsynchronousSocketChannel socketChannel;
    private String userId = "not set yet";
    private List<Room> myRoomList = new Vector<>();
    private Room myCurRoom;

    public Client(AsynchronousSocketChannel socketChannel)
    {
        this.socketChannel = socketChannel;
        this.logr = MyLog.getLogr();
        receive();
    }

    public AsynchronousSocketChannel getSocketChannel()
    {
        return socketChannel;
    }

    public String getUserId()
    {
        return userId;
    }

    public List<Room> getMyRoomList()
    {
        return myRoomList;
    }

    public Room getMyCurRoom()
    {
        return myCurRoom;
    }

    public void setSocketChannel(AsynchronousSocketChannel socketChannel)
    {
        this.socketChannel = socketChannel;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public void setMyCurRoom(Room myCurRoom)
    {
        this.myCurRoom = myCurRoom;
    }

    public void receive()
    {
        socketChannel.read(readBuffer, null, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {
                try
                {
                    logr.info("[요청 처리: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                    processOp(readBuffer);
                    readBuffer = ByteBuffer.allocate(10000);
                    if (socketChannel != null) socketChannel.read(readBuffer, readBuffer, this);
                } catch (IOException e)
                {
                } catch (BufferUnderflowException e)
                {
                    logr.info("receive 하는중에 BufferUnderflow 발생함");
                    readBuffer = ByteBuffer.allocate(10000);
                    readBuffer.clear();
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment)
            {
                try
                {
                    logr.severe("[receive fail" + socketChannel.getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                    socketChannel.close();
                } catch (IOException e)
                {
                }
            }
        });

    }

    public void send(int reqId, int operation, int broadcastNum, int result, ByteBuffer leftover)
    {
        if (reqId != -1)
        {
            writeBuffer.putInt(reqId);
            writeBuffer.putInt(operation);
            writeBuffer.putInt(result);
            writeBuffer.put(leftover);
        } else if (reqId == -1)
        {
            writeBuffer.putInt(reqId);
            writeBuffer.putInt(broadcastNum);
            writeBuffer.put(leftover);
        }
        writeBuffer.flip();
        socketChannel.write(writeBuffer, null, new CompletionHandler<Integer, Object>()
        {
            @Override
            public void completed(Integer result, Object attachment)
            {
                writeBuffer = ByteBuffer.allocate(10000);
                synchronized (for_inviteRoomProcess)
                {
                    for_inviteRoomProcess.notify();
                }
                synchronized (for_enterRoomProcess)
                {
                    for_enterRoomProcess.notify();
                }
                synchronized (for_sendTextProcess)
                {
                    for_sendTextProcess.notify();
                }
                synchronized (for_quitRoomProcess)
                {
                    for_quitRoomProcess.notify();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment)
            {
                synchronized (for_inviteRoomProcess)
                {
                    for_inviteRoomProcess.notify();
                }
                synchronized (for_enterRoomProcess)
                {
                    for_enterRoomProcess.notify();
                }
                synchronized (for_sendTextProcess)
                {
                    for_sendTextProcess.notify();
                }
                synchronized (for_quitRoomProcess)
                {
                    for_quitRoomProcess.notify();
                }
                try
                {
                    logr.severe("[send fail" + socketChannel.getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                    socketChannel.close();
                } catch (IOException e)
                {
                }
            }
        });
    }


    void processOp(ByteBuffer attachment)
    {
        attachment.flip();
        ClientProcess clientProcess = new ClientProcess(attachment,for_sendTextProcess,for_enterRoomProcess,for_inviteRoomProcess,for_quitRoomProcess);
        int reqId = clientProcess.getReqId();
        int operation = clientProcess.getOperation();
        String userId = clientProcess.getUserId();
        int roomNum = clientProcess.getRoomNum();
        OperationEnum op = OperationEnum.fromInteger(operation);
        switch (op)
        {
            case login:
                clientProcess.loginProcess(reqId, operation, userId, attachment);
                logr.info("login process completed");
                return;
            case logout:
                clientProcess.logoutProcess(reqId, operation, userId, attachment);
                logr.info("logout process completed");
                return;
            case sendText:
                clientProcess.sendTextProcess(reqId, operation, userId, attachment);
                return;
            case fileUpload:
            case fileList:
            case fileDownload:
            case fileDelete:
            case createRoom:
                clientProcess.createRoomProcess(reqId, operation, userId, attachment);
                return;
            case quitRoom:
                clientProcess.quitRoomProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case inviteRoom:
                clientProcess.inviteRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case roomUserList:
            case roomList:
                clientProcess.roomListProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enterRoom:
                clientProcess.enterRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enrollFile:

        }
    }
}
