package elitr.worker;

import cz.cuni.mff.ufal.LindatTranslationClient;
import cz.cuni.mff.ufal.Translator;
import it.pervoice.eubridge.mcloud.MCloudEventListener;
import it.pervoice.eubridge.mcloud.MCloudException;
import it.pervoice.eubridge.mcloud.jni.MCloudPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudQueue;
import it.pervoice.eubridge.mcloud.jni.MCloudTextPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudWorker;

import java.util.Date;
import java.util.logging.Logger;

public class ProcessingEventListener extends MCloudEventListener {
    
    private static final Logger log = Logger.getLogger(ProcessingEventListener.class.getName());
    private final MCloudWorker worker;
    private final String outputFingerPrint;
    private final String inputFingerPrint;
    private final Translator translator;

    public ProcessingEventListener(MCloudWorker worker, String inputFingerPrint, String outputFingerPrint){
        super();
        this.worker = worker;
        this.outputFingerPrint = outputFingerPrint;
        this.inputFingerPrint = inputFingerPrint;
        //TODO configurable
        translator = new LindatTranslationClient("https://lindat.mff.cuni.cz/services/translation/api/v1");
    }
    
    @Override
    /**
     * 
     * Called when a service request has been accepted by a worker. The packet
     * contains the service description. This callback must return quickly!
     * Otherwise MCloud will raise an ERROR to the client
     *
     * @param pkt Packet containing the service description
     ** @return true if no error occurred
     */
    public boolean handleInit(MCloudPacket pkt) {
        log.info("handleInit called " + pkt.toString());
        return true;
    }

    public boolean handleError(MCloudQueue.QueueType queueType) {
        log.info("Error processing");
        teardown();
        return true;
    }

    public boolean handleBreak(MCloudQueue.QueueType queueType) {
        log.info("Break processing");
        return true;
    }

    public boolean handleFinalize() {
        log.info("Finalize processing");
        return true;
    }

    public boolean handleData(MCloudPacket pkt) {
        log.info("handleData is called for packet " + pkt);
        if(pkt.getType() == MCloudPacket.PacketType.DATA_TEXT){
            MCloudTextPacket textPacket = (MCloudTextPacket) pkt;
            String translation = translator.translate(textPacket.getText(), inputFingerPrint, outputFingerPrint);
            log.info("Translation: " + translation);
            //TODO start\time, stopTime, offset?
            MCloudPacket translated = new MCloudTextPacket(textPacket.getStartTime(), textPacket.getStopTime(),
                    textPacket.getTimeOffset(), outputFingerPrint, translation);
            try {
                worker.sendPacketAsync(translated);
            } catch (MCloudException e) {
                log.severe(e.getMessage());
                return false;
            }
        }
        return true;
    }

    public void teardown() {
    }
}
