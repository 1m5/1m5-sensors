package io.onemfive.sensors;

import io.onemfive.data.Envelope;
import io.onemfive.data.Peer;

import java.util.Map;
import java.util.Properties;

public interface SensorManager {
    boolean init(Properties properties);
    Sensor selectSensor(Envelope envelope);
    void registerSensor(Sensor sensor);
    void updateSensorStatus(final String sensorID, SensorStatus sensorStatus);
    void updatePeer(Peer peer);
    Map<String,Peer> getAllPeers();
    void sensorError(String sensorClass);
    void sendToBus(Envelope envelope);
    void suspend(Envelope envelope);
    boolean shutdown();
}
