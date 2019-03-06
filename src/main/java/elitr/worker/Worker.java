package elitr.worker;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import cz.cuni.mff.ufal.LindatTranslationClient;
import cz.cuni.mff.ufal.Translator;
import it.pervoice.eubridge.mcloud.MCloudDataType;
import it.pervoice.eubridge.mcloud.MCloudException;
import it.pervoice.eubridge.mcloud.jni.MCloudPacket;
import it.pervoice.eubridge.mcloud.jni.MCloudQueue;
import it.pervoice.eubridge.mcloud.jni.MCloudWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker {
    private static final Logger log  = LoggerFactory.getLogger(Worker.class.getName());

    private static final String DEFAULT_HOST = "mediator.pervoice.com";
    private static final String DEFAULT_PORT = "60021";

    private static final String serviceType = "mt";
    private static final String inOutType = "text";

    private MCloudWorker mWorker;
    private MCloudQueue procQueue;
    private MCloudQueue sendQueue;

    public Worker (String name, String inputFingerprint, String outputFingerprint) throws MCloudException {
        /*
        Without synchronized this odd thing appears:
        This machine has 4available processors.
        [main] INFO elitr.worker.Worker - Using 2 threads
        JMCloud native library loaded!
        JMCloud native library loaded!
        Codec not found!
        ERROR (src/MCloud.c,2107): Setting audio encoder codec=RPCM samplerate=16000 bitrate=0 channels=1
         */
        synchronized (MCloudWorker.class) {
            mWorker = new MCloudWorker(name);
        }
        log.info("Adding service: name=" + name + " service=" + serviceType + " inputFP=" + inputFingerprint
                + " inputType=" + inOutType + " outputFP=" + outputFingerprint + " outputType=" + inOutType);
        mWorker.addService(name, serviceType, inputFingerprint, MCloudDataType.valueOf(inOutType),
                outputFingerprint, MCloudDataType.valueOf(inOutType), null);
        procQueue = mWorker.getProcessingQueue();
        sendQueue = mWorker.getSendingQueue();
        log.info("Setting listeners to processing queue and sending queue");
        ProcessingEventListener manager = new ProcessingEventListener(mWorker, outputFingerprint);
        procQueue.addGlobalListener(manager);
        sendQueue.addGlobalListener(new SendingEventListener(manager));
    }


    public static void main(String[] args) throws InterruptedException {
        System.err.println("This machine has " + Runtime.getRuntime().availableProcessors() + "available processors.");

        String host = Optional.ofNullable(System.getenv("PV_HOST")).orElse(DEFAULT_HOST);
        int port = Integer.valueOf(Optional.ofNullable(System.getenv("PV_PORT")).orElse(DEFAULT_PORT));
        //String nThreadArg = args.length == 1 ? args[0] : "1";
        //int nThreads = Integer.valueOf(nThreadArg);

        //log.info("Using " + nThreads + " threads");

        Path selfPath = Paths.get(Worker.class.getProtectionDomain().getCodeSource().getLocation().getPath());

        Properties props = new Properties();
        Path propPath = Paths.get(selfPath.getParent().toAbsolutePath().toString(),"input_fingerprints_mapping.properties");
        if(Files.exists(propPath)){
            try {
                props.load(Files.newBufferedReader(propPath));
            }catch (IOException e){
                log.error("Error loading " + propPath.toAbsolutePath(), e);
            }
        }else{
            log.info("Input fingreprints mapping file not found " + propPath.toAbsolutePath());
        }


        List<Callable<Void>> callables = new LinkedList<>();

        Translator translator = new LindatTranslationClient();
        for (Map.Entry<String, String> entry : translator.getAvailableLanguagePairs()) {
            String src = entry.getKey();
            String mapped_src = (String)props.getOrDefault(src, src);
            String tgt = entry.getValue();

            Callable<Void> callable = () -> {
                String workerName = String.format("LindatTranslationWorker-%s-%s-%s", InetAddress.getLocalHost().getHostName(),
                                ProcessHandle.current().pid(), Thread.currentThread().getId());
                Worker worker = new Worker(workerName, mapped_src, tgt);
                try{
                    worker.start(host, port);
                }catch (MCloudException e){
                    log.error("Error during processing. " + e.getMessage());
                }finally {
                    worker.stop();
                }
                return null;
            };
            callables.add(callable);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(callables.size());
        executorService.invokeAll(callables);
    }

    private void start(String host, int port) throws MCloudException {

        connect(host, port);

        while (!Thread.interrupted()) {
            log.info("Waiting for clients");
            try {
                mWorker.waitForClient();
                log.info("Client request accepted ");
            } catch (MCloudException e) {
                log.info("WaitForClient error... timed out?");
                connect(host, port);
                continue;
            }

            MCloudPacket pkt = null;
            boolean proceed = true;
            // please notice that if it's a DATA packet getNextPacketOrProcessAsync will process it
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
                        log.warn("Received unknown message: " + pkt);
                        break;
                }
            }
        }
    }

    private void connect(String host, int port) {
        while (!Thread.interrupted()) {
            try {
                log.info("Connecting to MCloud: " + host + ":" + port);
                mWorker.connect(host, port);
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

    private void stop() throws MCloudException {
        log.error("wtf", new Throwable("wtf"));
        log.info("Stopping worker...");
        mWorker.disconnect();
        mWorker = null;
    }
}
