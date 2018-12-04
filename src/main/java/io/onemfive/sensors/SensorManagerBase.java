package io.onemfive.sensors;

import io.onemfive.data.Envelope;
import io.onemfive.data.NetworkPeer;

import java.util.HashMap;
import java.util.Map;

public abstract class SensorManagerBase implements SensorManager {

    protected final Map<String, Sensor> registeredSensors = new HashMap<>();
    protected final Map<String, Sensor> activeSensors = new HashMap<>();
    protected final Map<String, Sensor> blockedSensors = new HashMap<>();

    protected Map<String, NetworkPeer> peers = new HashMap<>();

    protected SensorsService sensorsService;

    void setSensorsService(SensorsService sensorsService) {
        this.sensorsService = sensorsService;
    }

    @Override
    public void suspend(Envelope envelope) {
        sensorsService.suspend(envelope);
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

    @Override
    public void updatePeer(NetworkPeer peer) {
        peers.put(peer.getFullAddress(), peer);
    }

    @Override
    public Map<String, NetworkPeer> getAllPeers() {
        return peers;
    }

    @Override
    public void sendToBus(Envelope envelope) {
        sensorsService.sendToBus(envelope);
    }
}
