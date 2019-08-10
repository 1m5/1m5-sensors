package io.onemfive.sensors.peers;

import io.onemfive.core.util.tasks.TaskRunner;
import io.onemfive.data.NetworkPeer;
import io.onemfive.sensors.SensorsConfig;
import io.onemfive.sensors.SensorTask;
import io.onemfive.sensors.SensorsService;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Cycle through all known peers randomly to build and maintain known peer database.
 *
 * @author objectorange
 */
public  class PeerDiscovery extends SensorTask {

    private Logger LOG = Logger.getLogger(PeerDiscovery.class.getName());
    private boolean firstRun = true;
    private SensorsService service;

    public PeerDiscovery(String taskName, SensorsService service, TaskRunner taskRunner, Properties properties) {
        super(taskName, taskRunner, properties);
        this.service = service;
    }

    @Override
    public Long getPeriodicity() {
        if(firstRun) {
            // five minutes to give time for 1M5 Sensor Manager to warm up establishing network sessions
            // TODO: replace with event-driven notification of 1M5 Sensor Manager status
            return 5 * 60 * 1000L;
        }
        else
            return SensorsConfig.UI * 1000L; // wait for UI seconds
    }

    @Override
    public Boolean execute() {
        LOG.info("Running Peer Discovery...");
        NetworkPeer localPeer = service.getPeerManager().getLocalPeer();
        if(localPeer == null) {
            LOG.warning("Sensors Service doesn't have local Peer yet. Can't run Peer Updater.");
            return false;
        }
        long totalKnown = service.getPeerManager().totalPeers(localPeer);
        if(totalKnown < 1) {
            LOG.info("No peers known.");
            if(SensorsConfig.seeds!=null && SensorsConfig.seeds.size() > 0) {
                // Launch Seeds
                for (NetworkPeer seed : SensorsConfig.seeds) {
                    LOG.info("Sending Peer Status Request to Seed Peer:\n\tNetwork: " + seed.getNetwork() + "\n\tFingerprint: "+seed.getFingerprint()+"\n\tAddress: "+seed.getAddress());
                service.pingOut(seed);
                    LOG.info("Sent Peer Status Request to Seed Peer.");
                }
            } else {
                LOG.warning("No seeds available! Please provide at least one seed!");
                return false;
            }
        } else if(totalKnown < SensorsConfig.MaxPT) {
            LOG.info(totalKnown+" known peers less than Maximum Peers Tracked of "+ SensorsConfig.MaxPT+"; continuing peer discovery...");
            NetworkPeer p = service.getPeerManager().getRandomPeer(localPeer);
            if(p != null) {
                LOG.info("Sending Peer Status Request to Known Peer...");
                service.pingOut(p);
                LOG.info("Sent Peer Status Request to Known Peer.");
            }
        } else {
            LOG.info("Maximum Peers Tracked of "+ SensorsConfig.MaxPT+" reached. No need to look for more.");
        }
        firstRun = false;
        return true;
    }

}
