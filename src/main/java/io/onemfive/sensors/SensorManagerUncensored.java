package io.onemfive.sensors;

import io.onemfive.data.Envelope;

import java.util.logging.Logger;

/**
 * Sensitivity order from least to greatest is defined in Envelope.Sensitivity with default protocols:
 *
 * NONE: HTTP
 * LOW: HTTPS
 * MEDIUM: Tor
 * HIGH: I2P
 * VERYHIGH: I2P with High Delays
 * EXTREME: Direct Mesh
 * NEO: A combination of all anonymous networks from MEDIUM to EXTREME
 *
 * We are working towards providing the following sensitivity routing logic:
 *
 * ** 1M5 Inter-Node Communications **
 * All communications between 1M5 peers defaults to I2P unless the Envelope's sensitivity
 * is set to VERYHIGH which indicates that higher sensitivity is required with
 * acceptable higher latency or when set to EXTREME and Direct Mesh is available.
 * 1M5's communication foundation starts with I2P as it provides the lowest latency / greatest
 * privacy value. Direct Mesh is not yet available so a Sensitivity of EXTREME will use
 * I2P with random delays set to the maximum.
 *
 * ** EXTERNAL COMMUNICATIONS **
 * All communications specifying an external HTTP URL will:
 *
 * * NONE *
 * Use HTTPS if specified in the URL otherwise HTTP. If HTTPS fails, fall back to HTTP.
 * This should only be used for localhost.
 *
 * * LOW *
 * If HTTP supplied in URL, try HTTPS anyways. If HTTPS doesn't work, provide and/or log a warning.
 * This may be ok for accessing little known web services where privacy is not a concern.
 *
 * * MEDIUM *
 * Use Tor to reach specified HTTP/HTTPS URL.
 *
 * * HIGH *
 * If HTTP/HTTPS URL specified, use peers through I2P to reach a peer that can successfully use Tor.
 *
 * * VERYHIGH *
 * If HTTP/HTTPS URL specified, use peers through I2P with high random delays to reach a peer that can successfully use Tor.
 *
 * * EXTREME *
 * If HTTP/HTTPS URL specified and Direct Mesh not available, use peers through I2P with the max high random delays
 * to reach a peer that can successfully use Tor. If Direct Mesh is available, use that instead.
 *
 * * GENERAL PEER PROPAGATION *
 * 1. If any of the above fails, send request to another peer via I2P to have it attempt it.
 * 2. If the protocol specified fails at the peer, it will forward onto randomly chosen
 * (likely to get smarter in future) next peer and retry.
 * 3. This will occur for specified number of attempts up to a maximum 10 until tokenization is implemented
 * at which it will continue until supplied tokens for transaction are exhausted.
 * 4. If I2P fails during any of these attempts and Direct Mesh is available, Direct Mesh will take over.
 *
 * The SensorManagerNeo4j is more complex using the Neo4j Graph database embedded.
 * The 1M5 Neo4j library must be included to use this.
 *
 * @author objectorange
 */
public class SensorManagerUncensored extends SensorManagerSimple {

    private static Logger LOG = Logger.getLogger(SensorManagerUncensored.class.getName());

    @Override
    public Sensor selectSensor(Envelope e) {
        // Lookup sensor by simple means
        Sensor s = super.selectSensor(e);
        String err = null;
        if(s == null) {
            // Sensor not determined by request - we have a problem
            err = "Unable to select sensor from request. Please ensure Envelope sensitivity, operation, or url is set to a supported Sensor.";
        } else {
            // Sensor determined by request
            if(blockedSensors.get(s.getClass().getName())!=null
                    && SensorStatus.NETWORK_CONNECTED.name().equals(s.getStatus().name())) {
                blockedSensors.remove(s.getClass().getName());
            }
            switch(s.getStatus()) {
                case NETWORK_BLOCKED: {
                    if(TOR_SENSOR_NAME.equals(s.getClass().getName())) {
                        // Tor is being blocked, switch to I2P
                        if(getActiveSensors().get(I2P_SENSOR_NAME) == null) {
                            if(getActiveSensors().get(IDN_SENSOR_NAME) == null) {
                                err = "TOR blocked and I2P and 1DN Sensors not active. Please register I2P Sensor to ensure TOR can be re-routed through I2P when blocked.";
                            } else {
                                s = getActiveSensors().get(IDN_SENSOR_NAME);
                            }
                        } else {
                            s = getActiveSensors().get(I2P_SENSOR_NAME);
                        }
                    }
                    break;
                }

            }
        }
        if(err != null) {
            if (e.getMessage() != null) {
                e.getMessage().addErrorMessage(err);
            }
            LOG.warning(err);
        }

//        if(e.getRoute() != null && e.getURL() != null) {
//            String p = e.getURL().getPath();
//            if(p.startsWith("http")) {
//                if(p.startsWith("https")) {
//
//                } else {
//                    // Only HTTPS specified
//                    switch(e.getSensitivity()) {
//                        case LOW: {
//                            // try https anyways
//                            break;
//                        }
//                        case MEDIUM: {
//                            // use Tor if available
//
//                            break;
//                        }
//                        case NONE:
//                        default: {
//                            // try http
//
//                        }
//                    }
//                }
//            }
//        }
//
        return s;
    }
}
