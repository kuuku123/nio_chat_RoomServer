package roomserver;

import util.MyLog;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class SeverProcess
{
    ByteBuffer byteBuffer;
    private Logger logr;

    public SeverProcess(ByteBuffer byteBuffer)
    {
        this.byteBuffer = byteBuffer;
        logr = MyLog.getLogr();
    }
}
