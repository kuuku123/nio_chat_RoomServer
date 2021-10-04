package roomserver;

import room.Room;
import user.Client;
import util.MyLog;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static util.ElseProcess.removeZero;

public class ServerProcess
{
    ByteBuffer byteBuffer;
    private Logger logr;

    private Object for_sendTextProcess;
    private Object for_enterRoomProcess;
    private Object for_inviteRoomProcess;
    private Object for_quitRoomProcess;

    ServerProcess(ByteBuffer byteBuffer,Object for_sendTextProcess, Object for_enterRoomProcess, Object for_inviteRoomProcess,Object for_quitRoomProcess)
    {
        this.byteBuffer = byteBuffer;
        logr = MyLog.getLogr();
        this.for_sendTextProcess  = for_sendTextProcess;
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
        byteBuffer.get(reqUserId,0,16);
        byteBuffer.position(24);
        return new String(removeZero(reqUserId), StandardCharsets.UTF_8);
    }

    int getRoomNum()
    {
        return byteBuffer.getInt();
    }


    public void createRoomProcess(int reqId, int operation, String userId, ByteBuffer attachment)
    {
        RoomSeverService service = new RoomSeverService();
        int roomNum = attachment.getInt();
        byte[] roomNameReceive = new byte[20];
        attachment.get(roomNameReceive, 0, 20);
        String roomName = new String(removeZero(roomNameReceive), StandardCharsets.UTF_8);

        Room room = new Room(roomNum);
        room.setRoomName(roomName);
        RoomSeverService.masterRoomList.add(room);

        logr.info("["+roomNum+ "번 방이 생성]");
    }
}
