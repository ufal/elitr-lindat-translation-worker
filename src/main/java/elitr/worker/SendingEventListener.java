package elitr.worker;

import it.pervoice.eubridge.mcloud.MCloudEventListener;
import it.pervoice.eubridge.mcloud.jni.MCloudQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SendingEventListener extends MCloudEventListener {
    private static final Logger log = LoggerFactory.getLogger(SendingEventListener.class.getName());
    private ProcessingEventListener manager;

    public SendingEventListener(ProcessingEventListener manager) {
        this.manager = manager;
    }

    @Override
    public boolean handleError(MCloudQueue.QueueType queueType){
        manager.teardown();
        return true;
    }

    @Override
    public boolean handleFinalize(){
        return true;
    }

    @Override
    public boolean handleBreak(MCloudQueue.QueueType queueType){
        log.info("Processing break");
        return true;
    }
}
