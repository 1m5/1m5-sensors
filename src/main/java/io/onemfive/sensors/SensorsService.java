package io.onemfive.sensors;

import io.onemfive.core.*;
import io.onemfive.data.Envelope;
import io.onemfive.data.Route;
import io.onemfive.data.util.DLC;

import java.util.*;
import java.util.logging.Logger;

/**
 * This is the main entry point into the application by supported networks.
 * It registers all supported/configured Sensors and manages their lifecycle.
 * All Sensors' status has an effect on the SensorsService status which is
 * monitored by the ServiceBus.
 *
 *  @author objectorange
 */
public class SensorsService extends BaseService {

    private static final Logger LOG = Logger.getLogger(SensorsService.class.getName());

    public static final String OPERATION_SEND = "SEND";
    public static final String OPERATION_REPLY = "REPLY";

    private Properties config;

    private SensorManager sensorManager;

    public SensorsService() {
        super();
    }

    @Override
    public void handleDocument(Envelope envelope) {
        handleAll(envelope);
    }

    @Override
    public void handleEvent(Envelope envelope) {
        handleAll(envelope);
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        handleAll(envelope);
    }

    private void handleAll(Envelope e) {
        SensorRequest request = (SensorRequest)DLC.getData(SensorRequest.class,e);
        if(request == null) {
            LOG.warning("No SensorRequest found in Envelope data while sending to SensorsService.");
            return;
        }
        if(request.from == null) {
            LOG.warning("No fromDID found in SensorRequest while sending to SensorsService.");
            return;
        }
        if(request.to == null) {
            LOG.warning("No toDID found in SensorRequest while sending to SensorsService.");
            return;
        }
        Route r = e.getRoute();
        Sensor sensor = sensorManager.selectSensor(e);
        if(sensor != null) {
            switch (r.getOperation()) {
                case OPERATION_SEND : {
                    LOG.info("Sending Envelope to selected Sensor...");
                    sensor.send(e);
                }
                case OPERATION_REPLY : {
                    LOG.info("Replying with Envelope to requester...");
                    sensor.reply(e);
                }
                default: {
                    LOG.warning("Operation not supported. Sending to Dead Letter queue.");
                    deadLetter(e);
                }
            }
        } else {
            LOG.warning("Unable to determine sensor. Sending to Dead Letter queue.");
            deadLetter(e);
        }
    }

    public void sendToBus(Envelope envelope) {
        LOG.info("Sending request to service bus from Sensors Service...");
        int maxAttempts = 30;
        int attempts = 0;
        while(!producer.send(envelope) && ++attempts <= maxAttempts) {
            synchronized (this) {
                try {
                    this.wait(100);
                } catch (InterruptedException e) {}
            }
        }
        if(attempts == maxAttempts) {
            // failed
            DLC.addErrorMessage("500",envelope);
        }
    }

    /**
     * Based on supplied SensorStatus, set the SensorsService status.
     * @param sensorStatus
     */
    void determineStatus(SensorStatus sensorStatus) {
        ServiceStatus currentServiceStatus = getServiceStatus();
        LOG.info("Status updated to: "+sensorStatus.name());
        switch (sensorStatus) {
            case INITIALIZING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case STARTING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case WAITING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                else if(currentServiceStatus == ServiceStatus.STARTING)
                    updateStatus(ServiceStatus.WAITING);
                break;
            }
            case NETWORK_WARMUP: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_PORT_CONFLICT: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_CONNECTING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_CONNECTED: {
                if(allSensorsWithStatus(SensorStatus.NETWORK_CONNECTED)) {
                    LOG.info("All Sensors Connected to their networks, updating SensorService status to RUNNING.");
                    updateStatus(ServiceStatus.RUNNING);
                }
                break;
            }
            case NETWORK_STOPPING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_STOPPED: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case NETWORK_BLOCKED: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case NETWORK_ERROR: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case PAUSING: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case PAUSED: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case UNPAUSING: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case SHUTTING_DOWN: {
                break; // Not handling
            }
            case GRACEFULLY_SHUTTING_DOWN: {
                break; // Not handling
            }
            case SHUTDOWN: {
                if(allSensorsWithStatus(SensorStatus.SHUTDOWN)) {
                    if(getServiceStatus() != ServiceStatus.RESTARTING) {
                        updateStatus(ServiceStatus.SHUTDOWN);
                    }
                }
                break;
            }
            case GRACEFULLY_SHUTDOWN: {
                if(allSensorsWithStatus(SensorStatus.GRACEFULLY_SHUTDOWN)) {
                    if(getServiceStatus() == ServiceStatus.RESTARTING) {
                        start(this.config);
                    } else {
                        updateStatus(ServiceStatus.GRACEFULLY_SHUTDOWN);
                    }
                }
                break;
            }
            case RESTARTING: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case ERROR: {
                if(allSensorsWithStatus(SensorStatus.ERROR)) {
                    // Major issues - all sensors error - flag for restart of Service
                    updateStatus(ServiceStatus.UNSTABLE);
                    break;
                }
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            default: LOG.warning("Sensor Status not being handled: "+sensorStatus.name());
        }
    }

    private Boolean allSensorsWithStatus(SensorStatus sensorStatus) {
        Collection<Sensor> sensors = ((SensorManagerBase)sensorManager).getActiveSensors().values();
        for(Sensor s : sensors) {
            if(s.getStatus() != sensorStatus){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean start(Properties properties) {
        super.start(properties);
        LOG.info("Starting...");
        updateStatus(ServiceStatus.STARTING);

        // Parameters
        String sensorManagerClass = properties.getProperty(SensorManager.class.getName());
        if(sensorManagerClass == null) {
            LOG.warning(SensorManager.class.getName()+" property required to start SensorsService.");
            return false;
        }
        String sensorsConfig = properties.getProperty(Sensor.class.getName());
        if(sensorsConfig == null) {
            LOG.warning(Sensor.class.getName()+" property required to start SensorsService.");
            return false;
        }

        // Sensor Manager
        try {
            sensorManager = (SensorManager)Class.forName(sensorManagerClass).newInstance();
        } catch (Exception e) {
            LOG.warning("Exception caught while creating instance of Sensor Manager "+sensorManagerClass);
            e.printStackTrace();
            return false;
        }

        // Sensors
        String[] sensorConfigStrings = sensorsConfig.split(":");
        String[] sp;
        Sensor sensor = null;
        LOG.info("Building sensors configuration...");
        for(String sc : sensorConfigStrings) {
            sp = sc.split(",");
            String sensorClass = sp[0];
            String sensitivity = sp[1];
            String priorityStr = sp[2];
            try {
                sensor = (Sensor)Class.forName(sensorClass).newInstance();
            } catch (Exception e) {
                LOG.warning("Exception caught while creating instance of Sensor "+sensorClass+" with sensitivity "+sensitivity+" and priority "+priorityStr);
                e.printStackTrace();
            }
            if(sensor != null) {
                BaseSensor baseSensor = (BaseSensor)sensor;
                baseSensor.setSensitivity(Envelope.Sensitivity.valueOf(sensitivity));
                baseSensor.setPriority(Integer.parseInt(priorityStr));
                baseSensor.setSensorManager(sensorManager);
                sensorManager.registerSensor(sensor);
                LOG.info("Registered sensor "+sensor.getClass().getName());
            }
        }
        return sensorManager.init(properties);
    }

    @Override
    public boolean restart() {
        updateStatus(ServiceStatus.RESTARTING);
        gracefulShutdown();
        return true;
    }

    @Override
    public boolean shutdown() {
        super.shutdown();
        if(getServiceStatus() != ServiceStatus.RESTARTING)
            updateStatus(ServiceStatus.SHUTTING_DOWN);
        sensorManager.shutdown();
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        // TODO: add wait/checks to ensure each sensor shutdowns
        return shutdown();
    }

    public void setManager(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

}
