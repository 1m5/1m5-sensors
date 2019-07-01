package io.onemfive.sensors;

import io.onemfive.data.Envelope;
import io.onemfive.data.NetworkPeer;

import java.util.Map;
import java.util.Properties;

public interface SensorManager {

    String TOR_SENSOR_NAME = "io.onemfive.tor.client.TorClientSensor";
    String I2P_SENSOR_NAME = "io.onemfive.i2p.I2PSensor";
    String IDN_SENSOR_NAME = "io.onemfive.idn.IDNSensor";
    String RADIO_SENSOR_NAME = "io.onemfive.radio.RadioSensor";

    boolean init(Properties properties);
    Sensor selectSensor(Envelope envelope);
    void registerSensor(Sensor sensor);
    void updateSensorStatus(final String sensorID, SensorStatus sensorStatus);
    void savePeer(NetworkPeer peer);
    Map<String,NetworkPeer> getAllPeers();
    void sensorError(String sensorClass);
    void sendToBus(Envelope envelope);
    void suspend(Envelope envelope);
    boolean shutdown();
}
