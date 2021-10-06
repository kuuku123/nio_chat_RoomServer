package user;

import room.Room;
import roomserver.RoomSeverService;
import util.MyLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static util.ElseProcess.removeZero;

public class ClientProcess
{
    ByteBuffer byteBuffer;
    private Logger logr;
    private Object for_sendTextProcess;
    private Object for_enterRoomProcess;
    private Object for_inviteRoomProcess;
    private Object for_quitRoomProcess;

    ClientProcess(ByteBuffer byteBuffer, Object for_sendTextProcess, Object for_enterRoomProcess, Object for_inviteRoomProcess, Object for_quitRoomProcess)
    {
        this.byteBuffer = byteBuffer;
        logr = MyLog.getLogr();
        this.for_sendTextProcess = for_sendTextProcess;
        this.for_enterRoomProcess = for_enterRoomProcess;
        this.for_inviteRoomProcess = for_inviteRoomProcess;
        this.for_quitRoomProcess = for_quitRoomProcess;
    }

    int getReqId()
    {
        return byteBuffer.getInt();
    }

    int getOperation()
    {
        return byteBuffer.getInt();
    }

    String getUserId()
    {
        byte[] reqUserId = new byte[16];
        byteBuffer.get(reqUserId, 0, 16);
        byteBuffer.position(24);
        return new String(removeZero(reqUserId), StandardCharsets.UTF_8);
    }

    int getRoomNum()
    {
        return byteBuffer.getInt();
    }


    public void loginProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        List<Client> clientList = RoomSeverService.masterClientList;

        for (Client client : clientList)
        {
            if (client.getUserId().equals(userId))
            {
                Client newClient = clientList.get(clientList.size() - 1);
                client.setSocketChannel(newClient.getSocketChannel());
                clientList.remove(newClient);
//                client.receive(client.getSocketChannel());

                boolean roomExist = false;
                for (Room room : client.getMyRoomList())
                {
                    if(room.getRoomNum() == roomNum) roomExist = true; break;
                }

                if(!roomExist)
                {
                    for (Room room : RoomSeverService.masterRoomList)
                    {
                        if(room.getRoomNum() == roomNum)
                        {
                            client.getMyRoomList().add(room);
                        }
                    }
                }

                logr.info("[연결 개수: " + clientList.size() + "]");
                logr.info(userId + " 로그인 성공");
                break;
            }
        }
    }

    public void logoutProcess(int reqId, int operation, String userId, ByteBuffer attachment)
    {
        List<Client> clientList = RoomSeverService.masterClientList;
        for (Client client : clientList)
        {
            if (client.getUserId().equals(userId))
            {
                try
                {
                    logr.info(userId + " logged out , connection closed");
                    client.setMyCurRoom(null);
                    client.getSocketChannel().close();
                    return;
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendTextProcess(int reqId, int operation, String userId, ByteBuffer attachment)
    {

    }

    public void createRoomProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = RoomSeverService.getSender(userId);
        if (sender == null)
        {
            sender = RoomSeverService.masterClientList.get(RoomSeverService.masterClientList.size() - 1);
            sender.setUserId(userId);
        }

        Room room = new Room(roomNum);
//        room.setRoomName(roomName);
        RoomSeverService.masterRoomList.add(room);

        sender.getMyRoomList().add(room);
        sender.setMyCurRoom(room);
        sender.getMyCurRoom().getUserList().add(sender);

        logr.info("["+roomNum+ "번 방이 생성]");

    }

    public void quitRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer attachment)
    {
    }

    public void inviteRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {
        List<Client> clientList = RoomSeverService.masterClientList;
        Room invited = null;
        for (Room room : RoomSeverService.masterRoomList)
        {
            if (room.getRoomNum() == roomNum)
            {
                invited = room;
                break;
            }
        }
        int userCount = data.getInt();
        String[] users = new String[userCount];
        int curPos = data.position();
        for (int i = 0; i < userCount; i++)
        {
            byte[] userReceive = new byte[16];
            data.position(i * 16 + curPos);

            data.get(userReceive, 0, 16);
            String user = new String(removeZero(userReceive), StandardCharsets.UTF_8);
            users[i] = user;
        }
        for (String user : users)
        {
            Client client = new Client();
            client.setUserId(user);
            clientList.add(client);
            client.setMyCurRoom(invited);
            client.getMyCurRoom().getUserList().add(client);
            client.getMyRoomList().add(invited);
        }

        Client invitee = null;
        for (Client c : clientList)
        {
            if (c.getUserId().equals(userId))
            {
                invitee = c; break;
            }
        }

        for (Client roomUser : invitee.getMyCurRoom().getUserList())
        {
            if(roomUser.socketChannel != null  && roomUser.getSocketChannel().isOpen())
            {
                ByteBuffer infoBuf = ByteBuffer.allocate(1000);
                infoBuf.putInt(roomNum);
                infoBuf.put(invitee.getUserId().getBytes(StandardCharsets.UTF_8));
                infoBuf.position(20);
                infoBuf.putInt(userCount);
                int curPos0 = infoBuf.position();
                int i = 0;
                for (i = 0; i < userCount; i++)
                {
                    infoBuf.position(curPos0 + 16 * i);
                    infoBuf.put(users[i].getBytes(StandardCharsets.UTF_8));
                }
                infoBuf.position(curPos0 + 16 * i);
                infoBuf.flip();
                if(roomUser.getSocketChannel().isOpen())
                {
                    synchronized (for_inviteRoomProcess)
                    {
                        try
                        {
                            System.out.println(roomUser.getUserId());
                            roomUser.send(-1, operation, 0, 0, infoBuf);
                            for_inviteRoomProcess.wait(100);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        invitee.send(reqId, operation, 0, 0, ByteBuffer.allocate(0));

    }

    public void roomListProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = RoomSeverService.getSender(userId);
        int size = sender.getMyRoomList().size();
        ByteBuffer byteBuffer = ByteBuffer.allocate(10000);
        byteBuffer.putInt(size);
        for (int i = 0; i < size; i++)
        {
            Room room = sender.getMyRoomList().get(i);
            int roomNum1 = room.getRoomNum();
            String roomName = room.getRoomName();
            int userSize = room.getUserList().size();
            int notRead = room.getUserNotRoomRead(userId);
            byteBuffer.putInt(roomNum1);
            int prevPos = byteBuffer.position();
            byteBuffer.put(roomName.getBytes(StandardCharsets.UTF_8));
            int curPos = byteBuffer.position();
            int plusPos = 16 - (curPos - prevPos);
            byteBuffer.position(curPos + plusPos);
            byteBuffer.putInt(userSize);
            byteBuffer.putInt(notRead);
        }
        byteBuffer.flip();
        sender.send(reqId, operation, 0, 0, byteBuffer);

    }

    public void enterRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer attachment)
    {
    }
}
