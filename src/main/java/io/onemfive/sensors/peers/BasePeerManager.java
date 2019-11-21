package io.onemfive.sensors.peers;

import io.onemfive.core.keyring.AuthNRequest;
import io.onemfive.core.util.tasks.TaskRunner;
import io.onemfive.data.DID;
import io.onemfive.data.NetworkPeer;
import io.onemfive.sensors.SensorsService;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

import static io.onemfive.core.ServiceRequest.NO_ERROR;

public abstract class BasePeerManager extends TaskRunner implements PeerManager {

    private static final Logger LOG = Logger.getLogger(BasePeerManager.class.getName());

    private Properties properties;
    protected SensorsService service;
    protected NetworkPeer localPeer = new NetworkPeer();
    protected PeerDiscovery peerDiscovery;

    public BasePeerManager() {}

    public BasePeerManager(ThreadPoolExecutor fixedExecutor, ScheduledThreadPoolExecutor scheduledExecutor) {
        super(fixedExecutor, scheduledExecutor);
    }

    public void setSensorsService(SensorsService service) {
        this.service = service;
    }

    @Override
    public NetworkPeer getLocalPeer() {
        return localPeer;
    }

    @Override
    public void updateLocalPeer(AuthNRequest r) {
        if (r.errorCode == NO_ERROR) {
            LOG.info("Updating Local Peer: \n\taddress: "+r.identityPublicKey.getAddress()+"\n\tfingerprint: "+r.identityPublicKey.getFingerprint());
            if(r.identityPublicKey.getAddress()!=null)
                localPeer.setAddress(r.identityPublicKey.getAddress());
            if(r.identityPublicKey.getFingerprint()!=null)
                localPeer.setFingerprint(r.identityPublicKey.getFingerprint());
            DID d = localPeer.getDid();
            d.setAuthenticated(true);
            d.setVerified(true);
            savePeer(localPeer, true);
            LOG.info("Added returned public key to local Peer:"+localPeer);
        } else {
            LOG.warning("Error returned from AuthNRequest: " + r.errorCode);
        }
    }

    @Override
    public void updateLocalPeer(DID d) {
        NetworkPeer updatedNetworkPeer = new NetworkPeer();
        updatedNetworkPeer.setDid(d);
        if(updatedNetworkPeer.getI2PFingerprint()!=null)
            localPeer.setI2PFingerprint(updatedNetworkPeer.getI2PFingerprint());
        if(updatedNetworkPeer.getI2PAddress()!=null)
            localPeer.setI2PAddress(updatedNetworkPeer.getI2PAddress());
        LOG.info("Saving local peer's DID updated with I2P addresses: "+localPeer);
        savePeer(localPeer, false);
        LOG.info("DID with I2P Addresses saved; DCDN Service ready for requests.");
    }

    @Override
    public Boolean init(Properties properties) {
        this.properties = properties;
        peerDiscovery = new PeerDiscovery(PeerDiscovery.class.getSimpleName(), service, this, properties);
        addTask(peerDiscovery);
        return true;
    }

}
