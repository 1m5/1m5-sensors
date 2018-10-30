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
 *
 * Sensitivity order from least to greatest is defined in Envelope.Sensitivity with default protocols:
 *
 * NONE: HTTP
 * LOW: HTTPS
 * MEDIUM: Tor
 * HIGH: I2P
 * VERYHIGH: I2P Bote
 * EXTREME: Mesh
 * NEO: A combination of all anonymous networks from MEDIUM to EXTREME
 *
 * We are working towards providing the following sensitivity routing logic:
 *
 * ** 1M5 Inter-Node Communications **
 * All communications between 1M5 peers defaults to I2P unless the Envelope's sensitivity
 * is set to VERYHIGH which indicates that higher sensitivity is required with
 * acceptable higher latency or when set to EXTREME and MESH is available.
 * 1M5's communication foundation starts with I2P as it provides the lowest latency / greatest
 * privacy trade-off. MESH is not yet available so a Sensitivity of EXTREME will use
 * I2P Bote with random delays set high.
 *
 * ** EXTERNAL COMMUNICATIONS **
 * All communications specifying an external HTTP URL will:
 *
 * * NONE *
 * Use HTTPS if specified in the URL otherwise HTTP. If HTTPS fails, fall back to HTTP.
 *
 * * LOW *
 * If HTTP supplied in URL, try HTTPS anyways.
 *
 * * MEDIUM *
 * Use Tor to reach specified HTTP/HTTPS URL.
 *
 * * HIGH *
 * If HTTP/HTTPS URL specified, use peers through I2P to reach a peer that can successfully use Tor.
 *
 * * VERYHIGH *
 * If HTTP/HTTPS URL specified, use peers through I2P Bote to reach a peer that can successfully use Tor.
 *
 * * EXTREME *
 * If HTTP/HTTPS URL specified and MESH not available, use peers through I2P Bote with high random delays
 * to reach a peer that can successfully use Tor. If MESH is available, use that instead.
 *
 * * GENERAL PEER PROPAGATION *
 * 1. If any of the above fails, send request to another peer via I2P to have it attempt it.
 * 2. If the protocol specified fails at the peer, it will forward onto randomly chosen
 * (likely to get smarter in future) next peer and retry.
 * 3. This will occur for specified number of attempts up to a maximum 10 until tokenization is implemented
 * at which it will continue until supplied tokens for transaction are exhausted.
 * 4. If I2P fails during any of these attempts and MESH is available, MESH will take over.
 *
 * This logic is/will be implemented in a Sensor Manager.
 *
 * The SensorManagerSimple class is a very basic implementation.
 *
 * The SensorManagerNeo4j is more complex using the Neo4j Graph database embedded.
 * The 1M5 Neo4j library must be included to use this.
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

    private Sensor lookupBySensitivity(Envelope.Sensitivity sensitivity) {
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

    private Sensor lookupByOperation(String operation) {
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

    private Sensor lookupByURL(String url) {
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
