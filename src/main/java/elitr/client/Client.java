package elitr.client;

import it.pervoice.eubridge.mcloud.MCloudDataType;
import it.pervoice.eubridge.mcloud.MCloudException;
import it.pervoice.eubridge.mcloud.jni.MCloudClient;
import it.pervoice.eubridge.mcloud.jni.MCloudPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudQueue;
import it.pervoice.eubridge.mcloud.jni.MCloudTextPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class Client {

    private static Logger log = LoggerFactory.getLogger(Client.class.getName());

    private String name;
    private MCloudClient mClient;
    private MCloudQueue procQueue;
    private MCloudQueue sendQueue;
    private String streamID="00001";

    private static String serviceName = "MockupClient";
    private static String inputFingerprint = "cs";
    private static String outputFingerprint = "en";
    private static String inputType = "text";
    private static String outputType = "text";

    private static String mediatorHost = "mediator.pervoice.com";
    private static int mediatorClientPort = 4445;

    public Client(String _name) throws MCloudException {
        name=_name;
        mClient=new MCloudClient(name);
        mClient.addFlowDescription(null, true, inputFingerprint, name, serviceName);
        procQueue=mClient.getProcessingQueue();
        sendQueue=mClient.getSendingQueue();
        procQueue.addGlobalListener(new ProcessingEventListener());
    }

    void connect(String host, int port) {
        while (true) {
            try {
                log.info("Connecting to MCloud: " + host + ":" + port);
                mClient.connect(host, port);
                break;
            } catch (MCloudException e) {
                log.warn("Error connecting to MCloud. Retrying in a moment... [" + e.getMessage() + "]");
                try {
                    Thread.currentThread().sleep(2000);
                } catch (InterruptedException e1) {

                }
                continue;
            }
        }
    }

    //Naive implementation, good idea is to manage it in separated thread
    void start(String host, int port) throws MCloudException {
        connect(mediatorHost, mediatorClientPort);
        log.info("Announcing output stream: outputFP=" + outputFingerprint+ " outputType=" + outputType);
        mClient.announceOutputStream(outputFingerprint, MCloudDataType.valueOf(outputType), streamID, null);
        log.info("Requesting input stream: inputFP=" + inputFingerprint+ " inputType=" + inputType);

        mClient.requestInputStream(inputFingerprint, MCloudDataType.valueOf(inputType), streamID);

        log.info("Sending 1 word packet");
        sendOnePacket();
        log.info("Wait finish on sending queue");
        sendQueue.waitFinish(true);

        MCloudPacket p=mClient.getNextPacket();
        if(p.getType() == MCloudPacket.PacketType.DATA_TEXT) {
            mClient.processDataAsync(p);
        }else {
            switch (p.getType()) {
                case STATUS_FLUSH:
                    break;
                case STATUS_DONE:
                    log.info("DONE received");
                    procQueue.waitFinish(true);
                    break;
                case STATUS_ERROR:
                    log.info("ERROR received");
                    procQueue.breakQueue();
                    break;
                case STATUS_RESET:
                    System.err.println("RESET received");
                    procQueue.breakQueue();
                default:
                    System.err.println("Received unknown packet " + p);
            }
        }
    }

    void stop() throws MCloudException {
        log.info("Stopping client...");
        mClient.disconnect();
    }

    private void sendOnePacket() throws MCloudException {
        MCloudPacket pkt = new MCloudTextPacket(new Date(), new Date(), 0,
                inputFingerprint, "WORD " + ProcessHandle.current().pid());
        mClient.sendPacketAsync(pkt);
    }

    public static void main(String[] args) {
        Client client;
        try {
            client = new Client("MockupClient");
        } catch (MCloudException e) {
            log.error("Something wrong happened initializing Client" + e.getMessage());
            return;
        }
        try {
            client.start(mediatorHost, mediatorClientPort);
        } catch (MCloudException e) {
            log.error("Something wrong happened during processing" + e.getMessage());
        } finally {
            try {
                client.stop();
            } catch (MCloudException e) {
                log.error("Something wrong happened while stopping client" + e.getMessage());
            }
        }
    }

}
