package io.onemfive.sensors.packet;

import io.onemfive.data.NetworkPeer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shares a Peer's status including some of its reliable peers.
 *
 * @author objectorange
 */
public class PeerStatus extends CommunicationPacket {

    public PeerStatus() {}

    public PeerStatus(NetworkPeer fromPeer, NetworkPeer toPeer) {
        super(fromPeer, toPeer);
    }

    private Boolean responding = false;
    private List<NetworkPeer> reliablePeers = new ArrayList<>();

    public Boolean getResponding() {
        return responding;
    }

    public void setResponding(Boolean responding) {
        this.responding = responding;
    }

    public void setReliablePeers(List<NetworkPeer> reliablePeers) {
        this.reliablePeers = reliablePeers;
    }

    public List<NetworkPeer> getReliablePeers() {
        return reliablePeers;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String,Object> m = super.toMap();
        if(responding!=null) m.put("responding",responding.toString());
        if(reliablePeers!=null && reliablePeers.size() > 0) {
            List<Map<String,Object>> rp = new ArrayList<>();
            for(NetworkPeer p : reliablePeers) {
                rp.add(p.toMap());
            }
            m.put("reliablePeers",rp);
        }
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        super.fromMap(m);
        if(m.get("responding")!=null) responding = Boolean.parseBoolean((String)m.get("responding"));
        if(m.get("reliablePeers")!=null) {
            reliablePeers = new ArrayList<>();
            List<Map<String,Object>> rp = (List<Map<String,Object>>) m.get("reliablePeers");
            NetworkPeer np;
            for(Map<String,Object> rpm : rp) {
                np = new NetworkPeer();
                np.fromMap(rpm);
                reliablePeers.add(np);
            }
        }
    }
}
