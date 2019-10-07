package io.onemfive.sensors;

import io.onemfive.core.util.AppThread;
import io.onemfive.data.Envelope;
import io.onemfive.data.Route;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

/**
 * Simple in-memory sensor management.
 * Great for testing.
 */
public class SensorManagerSimple extends SensorManagerBase {

    private Logger LOG = Logger.getLogger(SensorManagerSimple.class.getName());
    private final long MAX_BLOCK_TIME_BETWEEN_RESTARTS = 40 * 60 * 1000; // 40 minutes
    private Map<String,Long> sensorBlocks = new HashMap<>();

    @Override
    public void updateSensorStatus(String sensorID, SensorStatus sensorStatus) {
        switch (sensorStatus) {
            case INITIALIZING: {
                LOG.info(sensorID + " reporting initializing....");
                break;
            }
            case STARTING: {
                LOG.info(sensorID + " reporting starting up....");
                break;
            }
            case WAITING: {
                LOG.info(sensorID + " reporting waiting....");
                break;
            }
            case NETWORK_WARMUP: {
                LOG.info(sensorID + " reporting network warming up....");
                break;
            }
            case NETWORK_PORT_CONFLICT: {
                LOG.info(sensorID + " reporting port conflict....");
                break;
            }
            case NETWORK_CONNECTING: {
                LOG.info(sensorID + " reporting connecting....");
                break;
            }
            case NETWORK_CONNECTED: {
                LOG.info(sensorID + " reporting connected.");
                if(sensorBlocks.get(sensorID)!=null) {
                    sensorBlocks.remove(sensorID);
                }
                break;
            }
            case NETWORK_STOPPING: {
                LOG.info(sensorID + " reporting stopping....");
                break;
            }
            case NETWORK_STOPPED: {
                LOG.info(sensorID + " reporting stopped.");
                if(activeSensors.containsKey(sensorID)) {
                    // Active Sensor Stopped, attempt to restart
                    Sensor sensor = activeSensors.get(sensorID);
                    if(sensor.restart()) {
                        LOG.info(sensorID+" restarted after disconnection.");
                    }
                }
                break;
            }
            case NETWORK_BLOCKED: {
                LOG.info(sensorID + " reporting blocked.");
                long now = System.currentTimeMillis();
                sensorBlocks.putIfAbsent(sensorID, now);
                if((now - sensorBlocks.get(sensorID)) > MAX_BLOCK_TIME_BETWEEN_RESTARTS) {
                    LOG.warning(sensorID + " reporting blocked longer than 9 minutes. Restarting...");
                    // Active Sensor Blocked, attempt to restart
                    activeSensors.get(sensorID).restart();
                    // Reset blocked start time
                    sensorBlocks.put(sensorID, now);
                }
                break;
            }
            case NETWORK_ERROR: {
                LOG.info(sensorID + " reporting network error.");
                break;
            }
            case PAUSING: {
                LOG.info(sensorID + " reporting pausing....");
                // TODO: Persist messages to this sensor until unpaused then replay in order.
                break;
            }
            case PAUSED: {
                LOG.info(sensorID + " reporting paused....");
                break;
            }
            case UNPAUSING: {
                LOG.info(sensorID + " reporting unpausing....");
                // TODO: Replay any paused messages in order while resuming normal operations
                break;
            }
            case SHUTTING_DOWN: {
                LOG.info(sensorID + " reporting shutting down....");
                activeSensors.remove(sensorID);
                break;
            }
            case GRACEFULLY_SHUTTING_DOWN: {
                LOG.info(sensorID + " reporting gracefully shutting down....");
                activeSensors.remove(sensorID);
                break;
            }
            case SHUTDOWN: {
                LOG.info(sensorID + " reporting shutdown.");
                break;
            }
            case GRACEFULLY_SHUTDOWN: {
                LOG.info(sensorID + " reporting gracefully shutdown.");
                break;
            }
            case RESTARTING: {
                LOG.info(sensorID + " reporting restarting....");
                break;
            }
            case ERROR: {
                LOG.info(sensorID + " reporting error. Initiating hard restart...");
                Sensor s = activeSensors.get(sensorID);
                // Give stopping sensors a chance to clean up anything possible
                activeSensors.remove(sensorID);
                s.gracefulShutdown();
                // Regardless if it succeeds or not, replace it with a new instance and start it up
                try {
                    s = (Sensor)Class.forName(sensorID).getConstructor().newInstance();
                    if(s.start(sensorsService.getProperties())) {
                        activeSensors.put(sensorID, s);
                    } else {
                        LOG.warning("Unable to hard restart sensor: "+sensorID);
                    }
                } catch (Exception e) {
                    LOG.warning("Unable to create new instance of sensor for hard restart: "+sensorID);
                }
                break;
            }
            default: LOG.warning("Sensor Status for sensor "+sensorID+" not being handled: "+sensorStatus.name());
        }
        // Now update the Service's status based on the this Sensor's status
        sensorsService.determineStatus(sensorStatus);
        // Now update listeners
        if(listeners.get(sensorID)!=null) {
            List<SensorStatusListener> sslList = listeners.get(sensorID);
            for(SensorStatusListener ssl : sslList) {
                ssl.statusUpdated(sensorStatus);
            }
        }
    }

    @Override
    public Sensor selectSensor(Envelope e) {
        Sensor sensor = null;
        Route r = e.getRoute();
        // Lookup by Sensitivity
        if(e.getSensitivity() != null)
            sensor = lookupBySensitivity(e.getSensitivity());
        // Lookup by Operation
        if(sensor == null) {
            sensor = lookupByOperation(r.getOperation());
        }
        // Lookup by URL
        if(sensor == null && e.getURL() != null){
            sensor = lookupByURL(e.getURL());
        }
        return sensor;
    }

    protected Sensor lookupBySensitivity(Envelope.Sensitivity sensitivity) {
        int highestPriority = 0;
        Sensor highest = null;
        Collection<Sensor> sensors = activeSensors.values();
        for(Sensor s : sensors) {
            if(s.getSensitivity() == sensitivity && s.getPriority() >= highestPriority) {
                highest = s;
            }
        }
        return highest;
    }

    protected Sensor lookupByOperation(String operation) {
        int highestPriority = 0;
        Sensor highest = null;
        Collection<Sensor> sensors = activeSensors.values();
        String[] ops;
        for(Sensor s : sensors) {
            ops = s.getOperationEndsWith();
            for(String op : ops) {
                if(op.equals(operation) && s.getPriority() >= highestPriority)
                    highest = s;
            }
        }
        return highest;
    }

    protected Sensor lookupByURL(URL url) {
        int highestPriority = 0;
        String protocol = url.getProtocol();
        String path = url.getPath();
        Sensor highest = null;
        Collection<Sensor> sensors = activeSensors.values();
        String[] urls;
        for(Sensor s : sensors) {
            urls = s.getURLBeginsWith();
            for(String u : urls) {
                if(u.equals(protocol) && s.getPriority() >= highestPriority)
                    highest = s;
            }
        }
        if(highest == null) {
            for(Sensor s : sensors) {
                urls = s.getURLEndsWith();
                for(String u : urls) {
                    if(u.equals(path) && s.getPriority() >= highestPriority)
                        highest = s;
                }
            }
        }
        return highest;
    }

    @Override
    public boolean init(final Properties properties) {
        // TODO: Add loop with checks
        Collection<Sensor> sensors = registeredSensors.values();
        for(final Sensor s : sensors) {
            LOG.info("Launching sensor "+s.getClass().getName());
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    s.start(properties);
                    activeSensors.put(s.getClass().getName(),s);
                }
            }).start();
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        // TODO: Add loop with checks
        Collection<Sensor> sensors = activeSensors.values();
        for(final Sensor s : sensors) {
            LOG.info("Beginning Shutdown of sensor "+s.getClass().getName());
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    s.shutdown();
                    activeSensors.remove(s.getClass().getName());
                }
            }).start();
        }
        return true;
    }


}
