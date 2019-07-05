package io.onemfive.sensors;

import io.onemfive.core.LifeCycle;
import io.onemfive.data.Envelope;
import io.onemfive.data.NetworkPeer;
import io.onemfive.sensors.peers.PeerManager;

import java.util.Map;

/**
 * Expected behavior from a Sensor.
 *
 * @author objectorange
 */
public interface Sensor extends LifeCycle {
    boolean send(Envelope envelope);
    boolean reply(Envelope envelope);
    void setNetwork(NetworkPeer.Network network);
    NetworkPeer.Network getNetwork();
    SensorStatus getStatus();
    Integer getRestartAttempts();
    Envelope.Sensitivity getSensitivity();
    String[] getOperationEndsWith();
    String[] getURLBeginsWith();
    String[] getURLEndsWith();
    // When Sensitivities are equal, priority can factor into order of evaluation
    Integer getPriority();
    void setSensorManager(SensorManager sensorManager);
    void setSensitivity(Envelope.Sensitivity sensitivity);
    void setPriority(Integer priority);
}
