package elitr.client;

import it.pervoice.eubridge.mcloud.MCloudEventListener;
import it.pervoice.eubridge.mcloud.jni.MCloudBinaryPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudTextPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProcessingEventListener extends MCloudEventListener {
    private static Logger log = LoggerFactory.getLogger(ProcessingEventListener.class.getName());

    @Override
    /**
     * Raised for each incoming data packet in a serial way.
     *
     * @return true if no error occurred
     */
    public boolean handleData(MCloudPacket pkt) {
        System.err.println("handleData");
        log.info("handleData is called for packet " + pkt);
        if(pkt.getType() == MCloudPacket.PacketType.DATA_TEXT) {
            MCloudTextPacket textPacket = (MCloudTextPacket) pkt;
            log.info("Received " + textPacket.getText());
        }
        return true;
    }
}
