package io.onemfive.sensors;

import io.onemfive.core.util.AppThread;
import io.onemfive.data.Envelope;
import io.onemfive.data.Peer;
import io.onemfive.data.Route;

import java.util.*;
import java.util.logging.Logger;

/**
 * Simple in-memory sensor management.
 * Great for testing.
 */
public class SensorManagerSimple extends SensorManagerBase {

    private Logger LOG = Logger.getLogger(SensorManagerSimple.class.getName());

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
                break;
            }
            case NETWORK_STOPPING: {
                LOG.info(sensorID + " reporting stopping....");
                break;
            }
            case NETWORK_STOPPED: {
                LOG.info(sensorID + " reporting stopped.");
                if(sensorID != null && sensorID.equals("io.onemfive.i2p.I2PSensor") && activeSensors.containsKey("io.onemfive.i2p.I2PSensor")) {
                    // Active I2P Sensor Stopped, attempt to restart
                    Sensor sensor = activeSensors.get("io.onemfive.i2p.I2PSensor");
                    if(sensor.restart()) {
                        LOG.info("I2P Sensor restarted after disconnection.");
                    }
                }
                break;
            }
            case NETWORK_BLOCKED: {
                LOG.info(sensorID + " reporting blocked.");
                break;
            }
            case NETWORK_ERROR: {
                LOG.info(sensorID + " reporting network error.");
                break;
            }
            case PAUSING: {
                LOG.info(sensorID + " reporting pausing....");
                break;
            }
            case PAUSED: {
                LOG.info(sensorID + " reporting paused....");
                break;
            }
            case UNPAUSING: {
                LOG.info(sensorID + " reporting unpausing....");
                break;
            }
            case SHUTTING_DOWN: {
                LOG.info(sensorID + " reporting shutting down....");
                break;
            }
            case GRACEFULLY_SHUTTING_DOWN: {
                LOG.info(sensorID + " reporting gracefully shutting down....");
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
                LOG.info(sensorID + " reporting error....");
                break;
            }
            default: LOG.warning("Sensor Status for sensor "+sensorID+" not being handled: "+sensorStatus.name());
        }
        // Now update the Service's status based on the this Sensor's status
        sensorsService.determineStatus(sensorStatus);
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
        if(sensor == null && e.getURL() != null && e.getURL().getProtocol() != null){
            sensor = lookupByURL(e.getURL().getPath());
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

    protected Sensor lookupByURL(String url) {
        int highestPriority = 0;
        Sensor highest = null;
        Collection<Sensor> sensors = activeSensors.values();
        String[] urls;
        for(Sensor s : sensors) {
            urls = s.getURLBeginsWith();
            for(String u : urls) {
                if(u.equals(url) && s.getPriority() >= highestPriority)
                    highest = s;
            }
        }
        if(highest == null) {
            for(Sensor s : sensors) {
                urls = s.getURLEndsWith();
                for(String u : urls) {
                    if(u.equals(url) && s.getPriority() >= highestPriority)
                        highest = s;
                }
            }
        }
        return highest;
    }

    @Override
    public void sensorError(final String sensorID) {
        // Sensor has Error, restart it if number of restarts is not greater than 3
        if(activeSensors.get(sensorID) != null) {
            if(activeSensors.get(sensorID).getRestartAttempts() <= 3) {
                new AppThread(new Runnable() {
                    @Override
                    public void run() {
                        activeSensors.get(sensorID).restart();
                    }
                }).start();
            } else {
                // Sensor is apparently not working. De-activate it.
                activeSensors.remove(sensorID);
            }
        }
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
