package io.onemfive.sensors.peers;

import io.onemfive.data.NetworkPeer;

import java.util.List;

public interface PeerReport {
    void report(NetworkPeer networkPeer);
    void report(List<NetworkPeer> networkPeers);
}
