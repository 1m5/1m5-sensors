package io.onemfive.sensors.peers;

import io.onemfive.core.util.FileUtil;
import io.onemfive.data.NetworkPeer;
import io.onemfive.neo4j.GraphUtil;
import io.onemfive.neo4j.Neo4jDB;
import io.onemfive.sensors.SensorsConfig;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.IndexDefinition;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

public class GraphPeerManager extends BasePeerManager {

    private static final Logger LOG = Logger.getLogger(GraphPeerManager.class.getName());

    public static final Label PEER_LABEL = Label.label(NetworkPeer.class.getSimpleName());
    public static final String PEER_LOCAL = "localPeer";

    // Peer-to-Peer Relationship
    public static final String PEER_TO_PEER_AVG_LATENCY = "peerToPeerAvgLatency";

    public static final String DBNAME = "imspg";

    private Neo4jDB db;

    public GraphPeerManager() { }

    public GraphPeerManager(ThreadPoolExecutor fixedExecutor, ScheduledThreadPoolExecutor scheduledExecutor) {
        super(fixedExecutor, scheduledExecutor);
    }

    public Boolean init(Properties properties) {
        if(!super.init(properties)) {
            LOG.warning("Problem starting SensorManagerBase...exiting...");
            return false;
        }
        db = new Neo4jDB();
        String baseDir = null;
        try {
            baseDir = service.getServiceDirectory().getCanonicalPath();
        } catch (IOException e) {
            LOG.warning("IOException caught retrieving SensorsService's service directory.");
            return false;
        }
        db.setLocation(baseDir + "/" + DBNAME);
        String cleanDB = properties.getProperty("onemfive.sensors.db.cleanOnRestart");
        if (Boolean.parseBoolean(cleanDB)) {
            FileUtil.rmdir(db.getLocation(), false);
            LOG.info("Cleaned " + DBNAME);
        }
        db.init(properties);

        // Initialize indexes
        LOG.info("Verifying Content Indexes are present...");
        try (Transaction tx = db.getGraphDb().beginTx()) {
            Iterable<IndexDefinition> definitions = db.getGraphDb().schema().getIndexes(PEER_LABEL);
            if(definitions==null || ((List)definitions).size() == 0) {
                LOG.info("1M5 Indexes not found; creating...");
                // No Address Indexes...set them up
                db.getGraphDb().schema().indexFor(PEER_LABEL).withName("NetworkPeer.address").on("address").create();
                db.getGraphDb().schema().indexFor(PEER_LABEL).withName("NetworkPeer.1m5Address").on("1m5Address").create();
                db.getGraphDb().schema().indexFor(PEER_LABEL).withName("NetworkPeer.torAddress").on("torAddress").create();
                db.getGraphDb().schema().indexFor(PEER_LABEL).withName("NetworkPeer.i2pAddress").on("i2pAddress").create();
                db.getGraphDb().schema().indexFor(PEER_LABEL).withName("NetworkPeer.sdrAddress").on("sdrAddress").create();
                db.getGraphDb().schema().indexFor(PEER_LABEL).withName("NetworkPeer.lifiAddress").on("lifiAddress").create();
                LOG.info("1M5 Indexes created.");
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }

        LOG.info("Relationship configurations:" +
                "\n\tonemfive.sensors.MinPT="+SensorsConfig.MinPT+": Min Peers Tracked - the point at which Discovery process goes into 'hyper' mode." +
                "\n\tonemfive.sensors.MaxPT="+SensorsConfig.MaxPT+": Max Peers Tracked - the total number of Peers to attempt to maintain knowledge of." +
                "\n\tonemfive.sensors.MaxPS="+SensorsConfig.MaxPS+": Max Peers Sent - Maximum number of peers to send in a peer list (the bigger a datagram, the less chance of it getting through)." +
                "\n\tonemfive.sensors.MaxAT="+SensorsConfig.MaxAT+": Max Acknowledgments Tracked" +
                "\n\tonemfive.sensors.UI="+SensorsConfig.UI+": Update Interval - the minutes between Discovery process" +
                "\n\tonemfive.sensors.MinAckRP="+SensorsConfig.MinAckRP+": Reliable Peer Min Acks" +
                "\n\tonemfive.sensors.MinAckSRP="+SensorsConfig.MinAckSRP+": Super Reliable Peer Min Acks");

        return true;
    }

//    public Node[] findShortestPath(NetworkPeer fromPeer, NetworkPeer toPeer) {
//        Node[] shortestPath = null;
//        PathFinder<Path> finder = GraphAlgoFactory.shortestPath(PathExpanders.forTypeAndDirection(P2PRelationship.RelType.Known, Direction.OUTGOING), 15);
//
//        Node startNode = findPeerNode(fromPeer, true);
//        Node endNode = findPeerNode(toPeer, true);
//        Iterator<Path> i = finder.findAllPaths( startNode, endNode ).iterator();
//        Path p;
//        while(i.hasNext()) {
//            p = i.next();
//
//        }
//        return shortestPath;
//    }
//
//    public Node[] findLowestLatencyPath(NetworkPeer fromPeer, NetworkPeer toPeer) {
//        Node[] lowestLatencyPath = null;
//        PathFinder<WeightedPath> finder = GraphAlgoFactory.dijkstra(PathExpanders.forTypeAndDirection(P2PRelationship.RelType.Known, Direction.OUTGOING), PEER_TO_PEER_AVG_LATENCY);
//        Node startNode = findPeerNode(fromPeer, true);
//        Node endNode = findPeerNode(toPeer, true);
//        WeightedPath path = finder.findSinglePath( startNode, endNode );
//        double weight = path.weight();
//
//        return lowestLatencyPath;
//    }

    @Override
    public NetworkPeer getLocalPeer() {
        return localPeer;
    }

    @Override
    public Boolean savePeer(NetworkPeer p, Boolean autocreate) {
        LOG.info("Saving NetworkPeer...");
        if(p.getAddress()==null || p.getAddress().isEmpty() || p.getAddress().equals("null")) {
            LOG.info("NetworkPeer to save has no Address. Skipping.");
            return false;
        }
        boolean updated = false;
        try {
            updated = updatePeer(p);
        } catch (Exception e) {
            return false;
        }
        if(updated)
            return true;
        else if(autocreate) {
            LOG.info("Creating NetworkPeer in graph...");
            long numberPeers = totalPeers(getLocalPeer(), P2PRelationship.RelType.Known);
            if(numberPeers <= SensorsConfig.MaxPT) {
                try (Transaction tx = db.getGraphDb().beginTx()) {
                    Node n = db.getGraphDb().createNode(PEER_LABEL);
                    toNode(p,n);
                    tx.success();
                    LOG.info("CDNPeer saved to graph.");
                } catch (Exception e) {
                    LOG.warning(e.getLocalizedMessage());
                }
            } else {
                LOG.info("Not adding peer; max number of peers reached: "+ SensorsConfig.MaxPT);
            }
        } else {
            LOG.info("New Peer but autocreate is false, unable to save peer.");
        }
        if(isLocalReady()
                && isRemoteReady(p)
                && !isRemoteLocal(p)
                && !isRelated(p, P2PRelationship.RelType.Known)) {
            LOG.info("Peer not known: relating as known.");
            relatePeers(getLocalPeer(), p, P2PRelationship.RelType.Known);
            LOG.info("Peers related as known.");
        }
        return true;
    }

    private boolean updatePeer(NetworkPeer p) throws Exception {
        LOG.info("Find and Update Peer Node...");
        boolean updated = false;
        LOG.info("Looking up Node by Address: "+p.getAddress());
        try (Transaction tx = db.getGraphDb().beginTx()) {
            Node n = db.getGraphDb().findNode(PEER_LABEL, "address", p.getAddress());
            if(n!=null) {
                LOG.info("Found Node: updating...");
                toNode(p, n);
                updated = true;
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
            throw e;
        }
        return updated;
    }

    @Override
    public Boolean verifyPeer(NetworkPeer peer) {
        if(findPeerByNetworkedAddress(peer.getNetwork(), peer.getAddress())==null) {
            return savePeer(peer, true);
        }
        return true;
    }

    @Override
    public List<NetworkPeer> getAllPeers(NetworkPeer fromPeer, int pageSize, int beginIndex) {
        LOG.info("Get All Peers...");
        List<NetworkPeer> peers = new ArrayList<>();
        int numPeers = 0;
        try(Transaction tx = db.getGraphDb().beginTx()){
            ResourceIterator<Node> i = db.getGraphDb().findNodes(PEER_LABEL);
            while (i.hasNext() && numPeers++ < pageSize) {
                peers.add(toPeer(toMap(i.next())));
            }
            tx.success();
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return peers;
    }

    @Override
    public NetworkPeer getRandomPeer(NetworkPeer p) {
        LOG.info("Get Random Peer...");
        NetworkPeer peer = null;
        long numberPeers = totalPeers(p, P2PRelationship.RelType.Known);
        if(numberPeers > 0) {
            long randomIndex = (long)(Math.random() * numberPeers);
            List<NetworkPeer> peers = getPeersByIndexRange(p, randomIndex, 1);
            peer = peers.get(0);
            LOG.info("Random peer selected: "+peer+" of peer: "+p);
        }
        return peer;
    }

    // TODO: Move this to file system to speed up
    @Override
    public Long totalPeers(NetworkPeer p, P2PRelationship.RelType relType) {
        long count = -1;
        try (Transaction tx = db.getGraphDb().beginTx()) {
            String cql = "MATCH (n {address: '"+p.getAddress()+"'})-[:" + relType.name() + "]->()" +
                    " RETURN count(*) as total";
            Result r = db.getGraphDb().execute(cql);
            if (r.hasNext()) {
                Map<String,Object> row = r.next();
                for (Map.Entry<String,Object> column : row.entrySet()) {
                    count = (long)column.getValue();
                    break;
                }
            }
            tx.success();
            LOG.info(count + " peers for peer: "+p.getAddress());
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return count;
    }

    public boolean isLocalReady() {
        boolean ready = getLocalPeer()!=null && getLocalPeer().getAddress()!=null;
        if(ready) {
            LOG.info("Local is ready: "+getLocalPeer().getAddress());
        } else {
            LOG.info("Local is not ready.");
        }
        return ready;
    }

    public boolean isRemoteReady(NetworkPeer r) {
        boolean ready = r != null && r.getAddress()!=null;
        if(ready) {
            LOG.info("Remote is ready: "+r.getAddress());
        } else {
            LOG.info("Remote is not ready.");
        }
        return ready;
    }

    public boolean isRemoteLocal(NetworkPeer r) {
        boolean remoteIsLocal = r != null
                && r.getAddress()!=null
                && getLocalPeer()!=null
                && getLocalPeer().getAddress()!=null
                && r.getAddress().equals(getLocalPeer().getAddress());
        if(remoteIsLocal) {
            LOG.info("Remote Peer is actually the Local Peer.");
        } else {
            LOG.info("Remote Peer is not the Local Peer.");
        }
        return remoteIsLocal;
    }

    public NetworkPeer findPeerByAddress(String address) {
        NetworkPeer p = null;
        if(address!=null) {
            try (Transaction tx = db.getGraphDb().beginTx()) {
                Node n = db.getGraphDb().findNode(PEER_LABEL, "address", address);
                p = toPeer(n);
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }
        }
        return p;
    }

    public NetworkPeer findPeerByNetworkedAddress(String network, String address) {
        NetworkPeer p = null;
        if(address!=null && network!=null) {
            try (Transaction tx = db.getGraphDb().beginTx()) {
                Node n = db.getGraphDb().findNode(PEER_LABEL, network.toLowerCase()+"Address", address);
                if(n!=null) {
                    p = toPeer(n);
                }
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }
        }
        return p;
    }

    public NetworkPeer findPeerByAddressAllNetworks(String address) {
        NetworkPeer p = null;
        if(address!=null) {
            p = findPeerByAddress(address);
            if(p!=null) {
                return p;
            }
            for (NetworkPeer.Network network : NetworkPeer.Network.values()) {
                try (Transaction tx = db.getGraphDb().beginTx()) {
                    Node n = db.getGraphDb().findNode(PEER_LABEL, network.name() + "Address", address);
                    if(n!=null) {
                        p = toPeer(n);
                    }
                    tx.success();
                } catch (Exception e) {
                    LOG.warning(e.getLocalizedMessage());
                }
                if(p!=null) {
                    return p;
                }
            }
        }
        return null;
    }

    public boolean isKnown(String address) {
        boolean isKnown = findPeerByAddressAllNetworks(address) != null;
        LOG.info("Peer\n\taddress: "+address+"\n\tis known: "+isKnown);
        return isKnown;
    }

    public boolean isRelated(NetworkPeer peer, P2PRelationship.RelType relType) {
        boolean isRelated = hasRelationship(getLocalPeer(), peer, relType);
        LOG.info("Are Peers Related? :\n\tLocal Peer Address: "+getLocalPeer().getAddress()+"\n\tRemote Peer Address :"+peer.getAddress()+"\n\t is related: "+isRelated);
        return isRelated;
    }

    public P2PRelationship relatePeers(NetworkPeer leftPeer, NetworkPeer rightPeer, P2PRelationship.RelType relType) {
        if(leftPeer==null || leftPeer.getAddress()==null) {
            LOG.info("Relating peer not provided or doesn't have address, skipping.");
            return null;
        }
        if(rightPeer==null || rightPeer.getAddress()==null) {
            LOG.info("Peer to relate with not provided or doesn't have address, skipping.");
            return null;
        }
        if(leftPeer.getAddress()!=null
                && rightPeer.getAddress()!=null
                && leftPeer.getAddress().equals(rightPeer.getAddress())) {
            LOG.info("Both peers are the same, skipping.");
            return null;
        }
        P2PRelationship rt = null;
        try (Transaction tx = db.getGraphDb().beginTx()) {
            Node lpn = db.getGraphDb().findNode(PEER_LABEL, "address", leftPeer.getAddress());
            Node rpn = db.getGraphDb().findNode(PEER_LABEL, "address", rightPeer.getAddress());
            Iterator<Relationship> i = lpn.getRelationships(relType, Direction.OUTGOING).iterator();
            while(i.hasNext()) {
                Relationship r = i.next();
                if(r.getNodes()[1].equals(rpn)) {
                    // load
                    rt = initP2PRel(r);
                    LOG.info("Found P2P Relationship; no need to create.");
                    break;
                }
            }
            if(rt==null) {
                // create
                Relationship r = lpn.createRelationshipTo(rpn, relType);
                rt = initP2PRel(r);
                LOG.info(rightPeer+" is now a "+relType.name()+" peer of "+leftPeer);
            }
            tx.success();
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return rt;
    }

    public long countByRelType(NetworkPeer p, P2PRelationship.RelType relType) {
        long count = -1;
        try (Transaction tx = db.getGraphDb().beginTx()) {
            String cql = "MATCH (n {address: '"+p.getAddress()+"'})-[:" + relType.name() + "]->()" +
                    " RETURN count(*) as total";
            Result r = db.getGraphDb().execute(cql);
            if (r.hasNext()) {
                Map<String,Object> row = r.next();
                for (Map.Entry<String,Object> column : row.entrySet()) {
                    count = (long)column.getValue();
                    break;
                }
            }
            tx.success();
            LOG.info(count+" "+relType.name());
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return count;
    }

    /**
     * Remove relationship
     * @param startPeer
     */
    public boolean removeRelationship(NetworkPeer startPeer, NetworkPeer endPeer, P2PRelationship.RelType relType) {
        try (Transaction tx = db.getGraphDb().beginTx()) {
            String cql = "MATCH (n {address: '"+startPeer.getAddress()+"'})-[r:" + relType.name() + "]->( e {address: '"+endPeer.getAddress()+"'})" +
                    " DELETE r;";
            db.getGraphDb().execute(cql);
            tx.success();
            LOG.info(relType.name() + " relationship of "+endPeer+" removed from "+startPeer);
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    public List<NetworkPeer> getPeersByIndexRange(NetworkPeer lp, long startIndex, long limit) {
        LOG.info("Looking up peers starting at index["+startIndex+"] and limited to "+limit+" peers....");
        List<NetworkPeer> peers = new ArrayList<>();
        try (Transaction tx = db.getGraphDb().beginTx()) {
            String cql = "START rp=node(*) MATCH (lp:"+PEER_LABEL+" {address: '"+lp.getAddress()+"'})->(rp:"+PEER_LABEL+")" +
                    " RETURN rp" +
                    " SKIP " + startIndex +
                    " LIMIT " + limit +";";
            Result r = db.getGraphDb().execute(cql);
            while (r.hasNext()) {
                peers.add(toPeer((Node)r.next().get("rp")));
            }
            tx.success();
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return peers;
    }

    @Override
    public void reliablesFromRemotePeer(NetworkPeer remotePeer, List<NetworkPeer> remoteKnown) {
        LOG.info("Number of known by remote peer sent: "+remoteKnown.size());
        int saved = 0;
        LOG.info("Saving Remote Peer...");
        if(savePeer(remotePeer, true)) {
            LOG.info("Remote Peer saved.");
            relatePeers(getLocalPeer(), remotePeer, P2PRelationship.RelType.Known);
            LOG.info("Remote Peer related as known to local peer.");
            long numberKnown = totalPeers(getLocalPeer(), P2PRelationship.RelType.Known);
            NetworkPeer remoteRelP;
            for (NetworkPeer known : remoteKnown) {
                if (numberKnown + saved > SensorsConfig.MaxPT)
                    break;
                LOG.info("Saving Remote Known...");
                savePeer(known, true);
                relatePeers(getLocalPeer(), known, P2PRelationship.RelType.Known);
                LOG.info("Remote Peer saved and related as Known to local peer.");
                relatePeers(remotePeer, known, P2PRelationship.RelType.Known);
                LOG.info("Remote Known Peer related as Known to Remote Peer.");
                if (++saved >= SensorsConfig.MaxPS) {
                    LOG.info("No longer taking reliables from this peer. Max reliables to receive reached: " + SensorsConfig.MaxPS);
                    break; // Ensure we do not update beyond the max sent to help fight a form of DDOS
                }
            }
        }
    }

    @Override
    public List<NetworkPeer> getReliablesToShare(NetworkPeer p) {
        List<NetworkPeer> peers = new ArrayList<>();
        try (Transaction tx = db.getGraphDb().beginTx()) {
            String cql = "MATCH (n {address: '"+p.getAddress()+"'})-[:" + P2PRelationship.RelType.Known.name() + "]->(x)" +
                    " RETURN x" +
                    " LIMIT "+ SensorsConfig.MaxPS+";";
            Result result = db.getGraphDb().execute(cql);
            while (result.hasNext()) {
                peers.add(toPeer((Node)result.next().get("x")));
            }
            tx.success();
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return peers;
    }

    /**
     * Saves Peer Request status results.
     * Determine if results change Reliable Peers list.
     * Reliable Peers are defined as peers known by given peer who have displayed
     * a minimum number of acks (CDNConfig.mr) and minimum avg response time (<= CDNConfig.lmc)
     *
     * @param startPeer CDNPeer of request
     * @param endPeer CDNPeer target
     * @param timeSent
     * @param timeDelivered
     * @param timeAcknowledged
     */
    public Boolean savePeerStatusTimes(NetworkPeer startPeer, NetworkPeer endPeer, Long timeSent, Long timeDelivered, Long timeAcknowledged) {
        boolean addedAsReliable = false;
        startPeer.setLocal(true); // Start is always local

        boolean isKnown = false;
        boolean isReliable = false;
        boolean isSuperReliable = false;

        long totalAcks = 0;
        long avgAckLatency = 0;

        isKnown = hasRelationship(startPeer, endPeer, P2PRelationship.RelType.Known);
        if(isKnown) {
            P2PRelationship knownRel = new P2PRelationship();
            String cql = "MATCH (n {address: '" + startPeer.getAddress() + "'})-[r:" + P2PRelationship.RelType.Known.name() + "]->(e {address: '" + endPeer.getAddress() + "'})" +
                    "return r;";
            try (Transaction tx = db.getGraphDb().beginTx()) {
                Result result = db.getGraphDb().execute(cql);
                if (result.hasNext()) {
                    Relationship r = (Relationship)result.next().get("r");
                    knownRel.fromMap(r.getAllProperties());
                }
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }
            // Update stats
            knownRel.advanceTotalAcks();
            knownRel.setLastAckTime(timeAcknowledged);
            knownRel.addAckTimeTracked(timeAcknowledged - timeSent);
            avgAckLatency = knownRel.getAvgAckLatencyMS();
            cql = "MATCH (n {address: '" + startPeer.getAddress() + "'})-[r:" + P2PRelationship.RelType.Known.name() + "]->(e {address: '" + endPeer.getAddress() + "'})" +
                    " SET r.totalAcks = " + knownRel.getTotalAcks() + "," +
                    " r.lastAckTime = " + knownRel.getLastAckTime() + "," +
                    " r.avgAckLatencyMS = " + avgAckLatency + "," +
                    " r.ackTimesTracked = '" + knownRel.getAckTimesTracked() + "';";
            try (Transaction tx = db.getGraphDb().beginTx()) {
                db.getGraphDb().execute(cql);
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }
            // Ensure total acks updated and persisted
            cql = "MATCH (n {address: '" + startPeer.getAddress() + "'})-[r:" + P2PRelationship.RelType.Known.name() + "]->(e {address: '" + endPeer.getAddress() + "'})" +
                    "return r;";
            P2PRelationship k = new P2PRelationship();
            try (Transaction tx = db.getGraphDb().beginTx()) {
                Result result = db.getGraphDb().execute(cql);
                if (result.hasNext()) {
                    Relationship r = (Relationship)result.next().get("r");
                    k.fromMap(r.getAllProperties());
                    totalAcks = k.getTotalAcks();
                }
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }

            // Update relationship
            isReliable = hasRelationship(startPeer, endPeer, P2PRelationship.RelType.Reliable);
            if(isReliable) {
                isSuperReliable = hasRelationship(startPeer, endPeer, P2PRelationship.RelType.SuperReliable);
                if(!isSuperReliable && knownRel.getTotalAcks() >= SensorsConfig.MinAckSRP) {
                    relatePeers(startPeer, endPeer, P2PRelationship.RelType.SuperReliable);
                    LOG.info("Now super reliable peer: "+endPeer);
                }
            } else if(knownRel.getTotalAcks() >= SensorsConfig.MinAckRP) {
                // Reliable relationship
                relatePeers(startPeer, endPeer, P2PRelationship.RelType.Reliable);
                addedAsReliable = true;
                LOG.info("Now reliable peer: "+endPeer);
            }

            LOG.info("Peer status times: {\n" +
                    "\tremote peer received message in: "+(timeDelivered-timeSent)+"ms\n"+
                    "\tack received by local peer in: "+(timeAcknowledged-timeDelivered)+"ms\n"+
                    "\tround trip in: "+(timeAcknowledged-timeSent)+"ms\n"+
                    "\t-------------------\n"+
                    "\ttotal acks: "+totalAcks+"\n"+
                    "\tavg round trip latency: "+avgAckLatency+"ms\n} of remote peer "+endPeer+" with start peer "+startPeer);

        } else if(totalPeers(startPeer, P2PRelationship.RelType.Known) <= SensorsConfig.MaxPT) {
            relatePeers(startPeer, endPeer, P2PRelationship.RelType.Known);
            LOG.info("New known peer: "+endPeer);
        } else {
            LOG.info("Max peers tracked: "+ SensorsConfig.MaxPT);
        }
        return addedAsReliable;
    }

    public boolean hasRelationship(NetworkPeer startPeer, NetworkPeer endPeer, RelationshipType relType) {
        boolean hasRel = false;
        String cql = "MATCH (n {address: '" + startPeer.getAddress() + "'})-[r:" + relType.name() + "]->(e {address: '" + endPeer.getAddress() + "'})" +
                " RETURN r;";
        try (Transaction tx = db.getGraphDb().beginTx()) {
            Result result = db.getGraphDb().execute(cql);
            if (result.hasNext()) {
                hasRel = true;
                LOG.info(endPeer + " is "+relType.name()+" peer to " + startPeer);
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return hasRel;
    }

    @Override
    public void report(NetworkPeer networkPeer) {
        LOG.info("NetworkPeer reported: "+networkPeer);
    }

    @Override
    public void report(List<NetworkPeer> networkPeers) {
        for(NetworkPeer networkPeer : networkPeers){
            report(networkPeer);
        }
    }

    private Map<String,Object> toMap(PropertyContainer n) {
        return GraphUtil.getAttributes(n);
    }

    private NetworkPeer toPeer(PropertyContainer n) {
        NetworkPeer p = new NetworkPeer();
        p.fromMap(toMap(n));
        return p;
    }

    private NetworkPeer toPeer(Map<String,Object> m) {
        NetworkPeer p = new NetworkPeer();
        p.fromMap(m);
        return p;
    }

    private void toNode(NetworkPeer p, PropertyContainer n) {
        GraphUtil.updateProperties(n, p.toMap());
    }

    private P2PRelationship initP2PRel(Relationship r) {
        P2PRelationship p2PR = new P2PRelationship();
        p2PR.fromMap(toMap(r));
        return p2PR;
    }
}
