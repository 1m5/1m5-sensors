package io.onemfive.sensors;

import io.onemfive.data.Envelope;
import io.onemfive.data.NetworkPeer;

import java.util.HashMap;
import java.util.Map;

public abstract class SensorManagerBase implements SensorManager {

    protected final Map<String, Sensor> registeredSensors = new HashMap<>();
    protected final Map<String, Sensor> activeSensors = new HashMap<>();
    protected final Map<String, Sensor> blockedSensors = new HashMap<>();

    protected NetworkPeer localPeer;
    protected Map<String, NetworkPeer> peers = new HashMap<>();

    protected SensorsService sensorsService;

    void setSensorsService(SensorsService sensorsService) {
        this.sensorsService = sensorsService;
    }

    @Override
    public void registerSensor(Sensor sensor) {
        registeredSensors.put(sensor.getClass().getName(), sensor);
    }

    Map<String, Sensor> getRegisteredSensors() {
        return registeredSensors;
    }

    Map<String, Sensor> getActiveSensors() {
        return activeSensors;
    }

    Map<String, Sensor> getBlockedSensors(){
        return blockedSensors;
    }

    public SensorStatus getSensorStatus(String sensor) {
        Sensor s = activeSensors.get(sensor);
        if(s == null) {
            return SensorStatus.UNREGISTERED;
        } else {
            return s.getStatus();
        }
    }

    public Sensor getRegisteredSensor(String sensorName) {
        return registeredSensors.get(sensorName);
    }

    public boolean isActive(String sensorName) {
        return activeSensors.containsKey(sensorName);
    }

    public void setLocalPeer(NetworkPeer localPeer) {
        this.localPeer = localPeer;
    }

    public NetworkPeer getLocalPeer() {
        return localPeer;
    }

    @Override
    public void sendToBus(Envelope envelope) {
        sensorsService.sendToBus(envelope);
    }

    @Override
    public void suspend(Envelope envelope) {
        sensorsService.suspend(envelope);
    }
}
