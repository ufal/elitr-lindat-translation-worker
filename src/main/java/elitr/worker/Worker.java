package elitr.worker;

import java.util.Optional;
import java.util.logging.Logger;
import it.pervoice.eubridge.mcloud.MCloudDataType;
import it.pervoice.eubridge.mcloud.MCloudException;
import it.pervoice.eubridge.mcloud.jni.MCloudPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudQueue;
import it.pervoice.eubridge.mcloud.jni.MCloudWorker;

public class Worker {
    private static final Logger log  = Logger.getLogger(Worker.class.getName());

    private static final String DEFAULT_HOST = "mediator.pervoice.com";
    private static final String DEFAULT_PORT = "60021";

    private static final String serviceType = "mt";
    private static final String inOutType = "text";

    private MCloudWorker mWorker;
    private MCloudQueue procQueue;
    private MCloudQueue sendQueue;

    public Worker (String name) throws MCloudException {
        mWorker = new MCloudWorker(name);
        //TODO get these from transformer
        String inputFingerprint = "en";
        String outputFingerprint = "cs";
        log.info("Adding service: name=" + name + " service=" + serviceType + " inputFP=" + inputFingerprint
                + " inputType=" + inOutType + " outputFP=" + outputFingerprint + " outputType=" + inOutType);
        mWorker.addService(name, serviceType, inputFingerprint, MCloudDataType.valueOf(inOutType),
                outputFingerprint, MCloudDataType.valueOf(inOutType), null);
        procQueue = mWorker.getProcessingQueue();
        sendQueue = mWorker.getSendingQueue();
        log.info("Setting listeners to processing queue and sending queue");
        ProcessingEventListener manager = new ProcessingEventListener(mWorker, inputFingerprint, outputFingerprint);
        procQueue.addGlobalListener(manager);
        sendQueue.addGlobalListener(new SendingEventListener(manager));
    }


    public static void main(String[] args) throws MCloudException {
        Worker worker = new Worker("LindatTranslationWorker");
        String host = Optional.ofNullable(System.getenv("PV_HOST")).orElse(DEFAULT_HOST);
        int port = Integer.valueOf(Optional.ofNullable(System.getenv("PV_PORT")).orElse(DEFAULT_PORT));
        try{
            worker.start(host, port);
        }catch (MCloudException e){
            log.severe("Error during processing. " + e.getMessage());
        }finally {
            worker.stop();
        }
    }

    //TODO this in an own thread?
    private void start(String host, int port) throws MCloudException {

        connect(host, port);

        while (true) {
            log.info("Waiting for clients");
            try {
                mWorker.waitForClient();
                log.info("Client request accepted");
            } catch (MCloudException e) {
                log.info("WaitForClient error... timed out?");
                connect(host, port);
                continue;
            }

            MCloudPacket pkt = null;
            boolean proceed = true;
            // please notice that if it's a DATA packet getNextPacketOrProcessAsync will
            // process it
            while (proceed && ((pkt = mWorker.getNextPacketOrProcessAsync()) != null)) {
                switch (pkt.getType()) {
                    case STATUS_FLUSH:
                        log.info("FLUSH received");
                        log.info("Wait until all packages in processing queue has been processed");
                        procQueue.waitFinish(false);
                        log.info("Tell the next Worker to flush too");
                        mWorker.sendFlush();
                        break;
                    case STATUS_DONE:
                        log.info("DONE received");
                        log.info("Wait until all packages in processing queue has been processed");
                        procQueue.waitFinish(true);
                        log.info("Wait until all packages in sending queue has been sent");
                        sendQueue.waitFinish(true);
                        log.info("Tell the next Worker there are no more data to be received");
                        mWorker.sendDone();
                        log.info("DONE sent");
                        proceed = false;
                        break;
                    case STATUS_ERROR:
                        log.info("ERROR received");
                        log.info("Stop processing packages immediately, and reset queue.");
                        procQueue.breakQueue();
                        proceed = false;
                        break;
                    case STATUS_RESET:
                        log.info("RESET received");
                        log.info("Stop processing packages immediately, and reset queue.");
                        procQueue.breakQueue();
                        proceed = false;
                        break;
                    default:
                        log.warning("Received unknown message: " + pkt);
                        break;
                }
            }
        }
    }

    private void connect(String host, int port) {
        while (true) {
            try {
                log.info("Connecting to MCloud: " + host + ":" + port);
                mWorker.connect(host, port);
                break;
            } catch (MCloudException e) {
                log.warning("Error connecting to MCloud. Retrying in a moment... [" + e.getMessage() + "]");
                try {
                    Thread.currentThread().sleep(2000);
                } catch (InterruptedException e1) {

                }
                continue;
            }
        }
    }

    private void stop() throws MCloudException {
        log.info("Stopping worker...");
        /*proceede = false;
        stopped = true;*/
        mWorker.disconnect();
    }
}
