package io.onemfive.sensors;

import io.onemfive.data.NetworkPeer;

import java.util.Map;

public interface PeerManager {
    NetworkPeer getLocalPeer();
    void savePeer(NetworkPeer peer);
    Map<String,NetworkPeer> getAllPeers(NetworkPeer fromPeer);
    Integer totalKnownPeers(NetworkPeer fromPeer);
    NetworkPeer getRandomPeer(NetworkPeer fromPeer);

}
