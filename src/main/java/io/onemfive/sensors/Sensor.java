package io.onemfive.sensors;

import io.onemfive.core.LifeCycle;
import io.onemfive.data.Envelope;
import io.onemfive.data.NetworkPeer;

import java.util.Map;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public interface Sensor extends LifeCycle {
    boolean send(Envelope envelope);
    boolean reply(Envelope envelope);
    SensorStatus getStatus();
    Integer getRestartAttempts();
    Map<String,NetworkPeer> getPeers();
    Envelope.Sensitivity getSensitivity();
    String[] getOperationEndsWith();
    String[] getURLBeginsWith();
    String[] getURLEndsWith();
    // When Sensitivities are equal, priority determines order of evaluation
    Integer getPriority();
}
