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
 * EXTREME: 1DN Wireless Direct Ad-Hoc Network
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
 * * NONE - MANCON 6*
 * Use HTTPS if specified in the URL otherwise HTTP. If HTTPS fails, fall back to HTTP.
 *
 * * LOW - MANCON 5 *
 * If HTTP supplied in URL, try HTTPS anyways. If HTTPS doesn't work, provide and/or log a warning.
 * This may be ok for accessing little known web services where privacy is not a concern.
 *
 * * MEDIUM - MANCON 4 *
 * Use Tor to reach specified HTTP/HTTPS URL.
 *
 * * HIGH - MANCON 3 *
 * If HTTP/HTTPS URL specified, use peers through I2P to reach a peer that can successfully use Tor.
 *
 * * VERYHIGH - MANCON 2 *
 * If HTTP/HTTPS URL specified, use peers through I2P with high random delays to reach a peer that can successfully use Tor.
 *
 * * EXTREME - MANCON 1 *
 * If HTTP/HTTPS URL specified and Direct Mesh not available, use peers through I2P with the max high random delays
 * to reach a peer that can successfully use Tor. If Direct Mesh is available, use that instead.
 *
 * * NEO - MANCON 0 *
 * Use a combination of anonymity networks to maximize anonymity.
 *
 * * GENERAL PEER PROPAGATION *
 * 1. If any of the above fails, send request to another peer to have it attempt it using an escalated network.
 * 2. If the protocol specified fails at the peer, it will forward onto randomly chosen
 * (likely to get smarter in future) next peer and retry.
 * 3. This will occur for specified number of attempts up to a maximum 10 until tokenization is implemented
 * at which it will continue until supplied tokens for transaction are exhausted.
 *
 * @author objectorange
 */
public class SensorManagerUncensored extends SensorManagerSimple {

    private static Logger LOG = Logger.getLogger(SensorManagerUncensored.class.getName());

    @Override
    public Sensor selectSensor(Envelope e) {
        // Lookup sensor by simple normal means
        Sensor simpleSelected = super.selectSensor(e);
        Sensor selected = simpleSelected;
        String err = null;
        if(simpleSelected == null) {
            // Sensor not determined by request - we have a problem
            err = "Unable to select sensor from request. Please ensure Envelope sensitivity, operation, or url is set to a supported Sensor.";
        } else {
            // Sensor determined by request
            if(simpleSelected.getStatus() == SensorStatus.NETWORK_BLOCKED) {
                if(TOR_SENSOR_NAME.equals(simpleSelected.getClass().getName())) {
                    LOG.info("Tor Sensor blocked.");
                    // Tor is being blocked, switch to I2P/Radio/LiFi
                    if(getActiveSensors().get(I2P_SENSOR_NAME) == null) {
                        if(getActiveSensors().get(RADIO_SENSOR_NAME) == null) {
                            if(getActiveSensors().get(LIFI_SENSOR_NAME) == null) {
                                err = "TOR blocked and I2P, Radio, and LiFi Sensors not active. Please register I2P, Radio, and/or LiFi Sensor to ensure TOR can be re-routed through I2P, Radio, or LiFi when blocked.";
                            } else {
                                LOG.info("LiFi Sensor is active; switching to LiFi...");
                                selected = getActiveSensors().get(LIFI_SENSOR_NAME);
                            }
                        } else {
                            LOG.info("Radio Sensor is active; switching to Radio...");
                            selected = getActiveSensors().get(RADIO_SENSOR_NAME);
                        }
                    } else {
                        LOG.info("I2P Sensor is active; switching to I2P...");
                        selected = getActiveSensors().get(I2P_SENSOR_NAME);
                    }
                } else if(I2P_SENSOR_NAME.equals(simpleSelected.getClass().getName())) {
                    LOG.info("I2P Sensor blocked.");
                    // I2P is being blocked, switch to Radio/LiFi
                    if(getActiveSensors().get(RADIO_SENSOR_NAME) == null) {
                        if(getActiveSensors().get(LIFI_SENSOR_NAME) == null) {
                            err = "I2P blocked and Radio nor LiFi Sensors are active. Please register Radio and/or LiFi Sensor to ensure I2P can be re-routed through Radio or LiFI when blocked.";
                        } else {
                            LOG.info("LiFi Sensor is active; switching to LiFi...");
                            selected = getActiveSensors().get(LIFI_SENSOR_NAME);
                        }
                    } else {
                        LOG.info("Radio Sensor is active; switching to Radio...");
                        selected = getActiveSensors().get(RADIO_SENSOR_NAME);
                    }
                } else if(RADIO_SENSOR_NAME.equals(simpleSelected.getClass().getName())) {
                    LOG.info("Radio Sensor blocked.");
                    // Radio is being blocked, switch to LiFi
                    if(getActiveSensors().get(LIFI_SENSOR_NAME) == null) {
                        err = "Radio blocked and LiFi Sensor not active. Please register LiFi Sensor to ensure Radio can be re-routed through LiFi when blocked.";
                    } else {
                        LOG.info("LiFi Sensor is active; switching to LiFi...");
                        selected = getActiveSensors().get(LIFI_SENSOR_NAME);
                    }
                }
            }
        }
        if(err != null) {
            if (e.getMessage() != null) {
                e.getMessage().addErrorMessage(err);
            }
            LOG.warning(err);
            return null;
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
        return selected;
    }
}
