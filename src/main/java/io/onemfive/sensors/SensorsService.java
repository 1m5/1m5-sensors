package io.onemfive.sensors;

import io.onemfive.core.*;
import io.onemfive.core.keyring.AuthNRequest;
import io.onemfive.core.keyring.KeyRingService;
import io.onemfive.core.notification.NotificationService;
import io.onemfive.core.notification.SubscriptionRequest;
import io.onemfive.data.*;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.FileUtil;
import io.onemfive.data.util.HashUtil;
import io.onemfive.data.util.JSONParser;
import io.onemfive.did.AuthenticateDIDRequest;
import io.onemfive.did.DIDService;
import io.onemfive.sensors.packet.CommunicationPacket;
import io.onemfive.sensors.packet.PeerStatus;
import io.onemfive.sensors.packet.ResponsePacket;
import io.onemfive.sensors.packet.StatusCode;
import io.onemfive.sensors.peers.PeerManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static io.onemfive.sensors.SensorsConfig.seeds;

/**
 * This is the main entry point into the application by supported networks.
 * It registers all supported/configured Sensors and manages their lifecycle.
 * All Sensors' status has an effect on the SensorsService status which is
 * monitored by the ServiceBus.
 *
 *  @author objectorange
 */
public class SensorsService extends BaseService {

    private static final Logger LOG = Logger.getLogger(SensorsService.class.getName());

    public static final String OPERATION_SEND = "SEND";
    public static final String OPERATION_REPLY = "REPLY";
    public static final String OPERATION_UPDATE_LOCAL_DID = "updateLocalDID";
    public static final String OPERATION_RECEIVE_LOCAL_PEER = "receiveLocalPeer";

    private SensorManager sensorManager;
    private PeerManager peerManager;
    private File sensorsDirectory;
    private Properties properties;

    public SensorsService() {
        super();
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    @Override
    public void handleDocument(Envelope envelope) {
        handleAll(envelope);
    }

    @Override
    public void handleEvent(Envelope envelope) {
        handleAll(envelope);
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        handleAll(envelope);
    }

    private void handleAll(Envelope e) {
        Route r = e.getRoute();
        Sensor sensor = sensorManager.selectSensor(e);
        if(sensor != null) {
            switch (r.getOperation()) {
                case OPERATION_SEND : {
                    LOG.info("Sending Envelope to selected Sensor...");
                    sensor.send(e);
                    break;
                }
                case OPERATION_REPLY : {
                    LOG.info("Replying with Envelope to requester...");
                    sensor.reply(e);
                    break;
                }
                case OPERATION_UPDATE_LOCAL_DID: {
                    LOG.info("Update local DID...");
                    peerManager.updateLocalPeer((DID)DLC.getData(DID.class,e));break;
                }
                case OPERATION_RECEIVE_LOCAL_PEER: {
                    LOG.info("Receive Local CDNPeer...");
                    peerManager.updateLocalPeer(((AuthNRequest) DLC.getData(AuthNRequest.class,e)));break;
                }
                default: {
                    LOG.warning("Operation ("+r.getOperation()+") not supported. Sending to Dead Letter queue.");
                    deadLetter(e);
                }
            }
        } else {
            LOG.warning("Unable to determine sensor. Sending to Dead Letter queue.");
            deadLetter(e);
        }
    }

    public void sendToBus(Envelope envelope) {
        LOG.info("Sending request to service bus from Sensors Service...");
        producer.send(envelope);
    }

    void suspend(Envelope envelope) {
        deadLetter(envelope);
    }

    private void routeIn(Envelope envelope) {
        LOG.info("Route In from Notification Service...");
        DID fromDid = envelope.getDID();
        LOG.info("From DID pulled from Envelope.");
        // -- Ensure saved ---
        NetworkPeer fromPeer = new NetworkPeer();
        fromPeer.setDid(fromDid);
        peerManager.savePeer(fromPeer,true);
        // ----------
        EventMessage m = (EventMessage)envelope.getMessage();
        Object msg = m.getMessage();
        Object obj;
        if(msg instanceof String) {
            // Raw
            Map<String, Object> mp = (Map<String, Object>) JSONParser.parse(msg);
            String type = (String) mp.get("type");
            if (type == null) {
                LOG.warning("Attribute 'type' not found in EventMessage message. Unable to instantiate object.");
                deadLetter(envelope);
                return;
            }
            try {
                obj = Class.forName(type).newInstance();
            } catch (InstantiationException e) {
                LOG.warning("Unable to instantiate class: " + type);
                deadLetter(envelope);
                return;
            } catch (IllegalAccessException e) {
                LOG.severe(e.getLocalizedMessage());
                deadLetter(envelope);
                return;
            } catch (ClassNotFoundException e) {
                LOG.warning("Class not on classpath: " + type);
                deadLetter(envelope);
                return;
            }
            if (obj instanceof CommunicationPacket) {
                LOG.info("Object a CommunicationPacket...");
                CommunicationPacket packet = (CommunicationPacket) obj;
                packet.fromMap(mp);
                packet.setTimeDelivered(System.currentTimeMillis());
                switch (type) {
                    case "io.onemfive.sensors.packet.PeerStatus": {
                        pingIn((PeerStatus) packet);
                        break;
                    }
                    case "io.onemfive.sensors.packet.ResponsePacket": {
                        response((ResponsePacket) packet);
                        break;
                    }
                    default:
                        deadLetter(envelope);
                }
            } else {
                LOG.warning("Object " + obj.getClass().getName() + " not handled.");
                deadLetter(envelope);
            }
        } else if(msg instanceof DID) {
            LOG.info("Route in DID with I2P Address...");
            DID d = (DID)msg;
            NetworkPeer updatedPeer = new NetworkPeer();
            NetworkPeer localPeer = peerManager.getLocalPeer();
            updatedPeer.setDid(d);
            if(updatedPeer.getI2PFingerprint()!=null)
                localPeer.setI2PFingerprint(updatedPeer.getI2PFingerprint());
            if(updatedPeer.getI2PAddress()!=null)
                localPeer.setI2PAddress(updatedPeer.getI2PAddress());
            LOG.info("Saving local peer's DID updated with I2P addresses: "+localPeer);
            peerManager.savePeer(localPeer, false);
            LOG.info("DID with I2P Addresses saved; Sensors Service ready for requests.");
        } else {
            LOG.warning("EnvelopeMessage message "+msg.getClass().getName()+" not handled.");
            deadLetter(envelope);
        }
    }


    /**
     * Request from an external NetworkPeer to see if this NetworkPeer is online.
     * Reply with known reliable peer addresses.
     */
    public void pingIn(PeerStatus request) {
        LOG.info("Received PeerStatus request...");
        peerManager.reliablesFromRemotePeer(request.getFromPeer(), request.getReliablePeers());
        request.setResponding(true);
        request.setReliablePeers(peerManager.getReliablesToShare(peerManager.getLocalPeer()));
        LOG.info("Sending response to PeerStatus request...");
        routeOut(new ResponsePacket(request, peerManager.getLocalPeer(), request.getFromPeer(), StatusCode.OK, request.getId()));
    }

    /**
     * Response handling of ResponsePacket from earlier request.
     * @param res
     */
    public void response(ResponsePacket res) {
        res.setTimeReceived(System.currentTimeMillis());
        CommunicationPacket req = res.getRequest();
        switch (res.getStatusCode()) {
            case OK: {
                req.setTimeAcknowledged(System.currentTimeMillis());
                LOG.info("Ok response received from request.");
                if (req instanceof PeerStatus) {
                    LOG.info("PeerStatus response received from PeerStatus request.");
                    LOG.info("Saving peer status times in graph...");
                    if(peerManager.savePeerStatusTimes(req.getFromPeer(), req.getToPeer(), req.getTimeSent(), req.getTimeDelivered(), req.getTimeAcknowledged())) {
                        LOG.info("Updating reliables in graph...");
                        peerManager.reliablesFromRemotePeer(req.getToPeer(), ((PeerStatus)req).getReliablePeers());
                    }
                } else {
                    LOG.warning("Unsupported request type received in ResponsePacket: "+req.getClass().getName());
                }
                break;
            }
            case GENERAL_ERROR: {
                LOG.warning("General error.");
                break;
            }
            case INSUFFICIENT_HASHCASH: {
                LOG.warning("Insufficient hashcash.");
                break;
            }
            case INVALID_HASHCASH: {
                LOG.warning("Invalid hashcash.");
                break;
            }
            case INVALID_PACKET: {
                LOG.warning("Invalid packed received by peer.");
                break;
            }
            case NO_AVAILABLE_STORAGE: {
                LOG.warning("No available storage on peer.");
                break;
            }
            case NO_DATA_FOUND: {
                LOG.warning("No data found by peer.");
                break;
            }
            default:
                LOG.warning("Unhandled ResponsePacket due to unhandled Status Code: " + res.getStatusCode().name());
        }
    }

    /**
     * Probe an external NetworkPeer to see if it is online sending it current reliable peers expecting to receive OK with their reliable peers (response).
     */
    public void pingOut(NetworkPeer peerToProbe) {
        LOG.info("Sending PeerStatus request out to peer...");
        PeerStatus ps = new PeerStatus(peerManager.getLocalPeer(), peerToProbe);
        ps.setReliablePeers(peerManager.getReliablesToShare(peerManager.getLocalPeer()));
        routeOut(ps);
    }

    /**
     * Send request out to peer
     * @param packet
     */
    public void routeOut(CommunicationPacket packet) {
        LOG.info("Routing out comm packet to Sensors Service...");
        if(packet.getTimeSent() <= 0) {
            // initial route out
            packet.setTimeSent(System.currentTimeMillis());
        }
        String json = JSONParser.toString(packet.toMap());
        LOG.info("Content to send: "+json);
        Envelope e = Envelope.documentFactory();
        // Setting Sensitivity to HIGH requests it to be routed through I2P
        e.setSensitivity(Envelope.Sensitivity.HIGH);
        SensorRequest r = new SensorRequest();
        r.from = packet.getFromPeer().getDid();
        r.to = packet.getToPeer().getDid();
        r.content = json;
        DLC.addData(SensorRequest.class, r, e);
        DLC.addRoute(SensorsService.class, SensorsService.OPERATION_SEND, e);
        producer.send(e);
        LOG.info("Comm packet sent.");
    }

    /**
     * Based on supplied SensorStatus, set the SensorsService status.
     * @param sensorStatus
     */
    void determineStatus(SensorStatus sensorStatus) {
        ServiceStatus currentServiceStatus = getServiceStatus();
        LOG.info("Status updated to: "+sensorStatus.name());
        switch (sensorStatus) {
            case INITIALIZING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case STARTING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case WAITING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                else if(currentServiceStatus == ServiceStatus.STARTING)
                    updateStatus(ServiceStatus.WAITING);
                break;
            }
            case NETWORK_WARMUP: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_PORT_CONFLICT: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_CONNECTING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_CONNECTED: {
                if(allSensorsWithStatus(SensorStatus.NETWORK_CONNECTED)) {
                    LOG.info("All Sensors Connected to their networks, updating SensorService status to RUNNING.");
                    updateStatus(ServiceStatus.RUNNING);
                }
                break;
            }
            case NETWORK_STOPPING: {
                if(currentServiceStatus == ServiceStatus.RUNNING)
                    updateStatus(ServiceStatus.PARTIALLY_RUNNING);
                break;
            }
            case NETWORK_STOPPED: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case NETWORK_BLOCKED: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case NETWORK_ERROR: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case PAUSING: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case PAUSED: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case UNPAUSING: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case SHUTTING_DOWN: {
                break; // Not handling
            }
            case GRACEFULLY_SHUTTING_DOWN: {
                break; // Not handling
            }
            case SHUTDOWN: {
                if(allSensorsWithStatus(SensorStatus.SHUTDOWN)) {
                    if(getServiceStatus() != ServiceStatus.RESTARTING) {
                        updateStatus(ServiceStatus.SHUTDOWN);
                    }
                }
                break;
            }
            case GRACEFULLY_SHUTDOWN: {
                if(allSensorsWithStatus(SensorStatus.GRACEFULLY_SHUTDOWN)) {
                    if(getServiceStatus() == ServiceStatus.RESTARTING) {
                        start(properties);
                    } else {
                        updateStatus(ServiceStatus.GRACEFULLY_SHUTDOWN);
                    }
                }
                break;
            }
            case RESTARTING: {
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            case ERROR: {
                if(allSensorsWithStatus(SensorStatus.ERROR)) {
                    // Major issues - all sensors error - flag for restart of Service
                    updateStatus(ServiceStatus.UNSTABLE);
                    break;
                }
                if(currentServiceStatus == ServiceStatus.RUNNING
                        || currentServiceStatus == ServiceStatus.PARTIALLY_RUNNING)
                    updateStatus(ServiceStatus.DEGRADED_RUNNING);
                break;
            }
            default: LOG.warning("Sensor Status not being handled: "+sensorStatus.name());
        }
    }

    private Boolean allSensorsWithStatus(SensorStatus sensorStatus) {
        Collection<Sensor> sensors = ((SensorManagerBase)sensorManager).getActiveSensors().values();
        for(Sensor s : sensors) {
            if(s.getStatus() != sensorStatus){
                return false;
            }
        }
        return true;
    }

    public SensorManager getSensorManager() {
        return sensorManager;
    }

    @Override
    public boolean start(Properties properties) {
        super.start(properties);
        LOG.info("Starting...");
        updateStatus(ServiceStatus.STARTING);
        this.properties = properties;

        // Parameters
        String sensorManagerClass = properties.getProperty(SensorManager.class.getName());
        if(sensorManagerClass == null) {
            LOG.warning(SensorManager.class.getName()+" property required to start SensorsService.");
            return false;
        }
        String peerManagerClass = properties.getProperty(PeerManager.class.getName());
        if(peerManagerClass == null) {
            LOG.warning(PeerManager.class.getName()+" property required to start SensorsService.");
            return false;
        }
        String sensorsConfig = properties.getProperty(Sensor.class.getName());
        if(sensorsConfig == null) {
            LOG.warning(Sensor.class.getName()+" property required to start SensorsService.");
            return false;
        }

        // Directories
        try {
            sensorsDirectory = new File(getServiceDirectory(), "sensors");
            if(!sensorsDirectory.exists() && !sensorsDirectory.mkdir()) {
                LOG.warning("Unable to create sensors directory at: "+getServiceDirectory().getAbsolutePath()+"/sensors");
            } else {
                properties.setProperty("1m5.dir.sensors",sensorsDirectory.getCanonicalPath());
            }
        } catch (IOException e) {
            LOG.warning("IOException caught while building sensors directory: \n"+e.getLocalizedMessage());
        }

        SensorsConfig.update(properties);

        // Sensor Manager
        try {
            sensorManager = (SensorManager)Class.forName(sensorManagerClass).newInstance();
            ((SensorManagerBase)sensorManager).setSensorsService(this);
        } catch (Exception e) {
            LOG.warning("Exception caught while creating instance of Sensor Manager "+sensorManagerClass);
            e.printStackTrace();
            return false;
        }

        // Peer Manager
        try {
            peerManager = (PeerManager) Class.forName(peerManagerClass).newInstance();
        } catch (Exception e) {
            LOG.warning("Exception caught while creating instance of Peer Manager "+sensorManagerClass);
            e.printStackTrace();
            return false;
        }

        // Sensors
        String[] sensorConfigStrings = sensorsConfig.split(":");
        String[] sp;
        Sensor sensor = null;
        LOG.info("Building sensors configuration...");
        for(String sc : sensorConfigStrings) {
            sp = sc.split(",");
            String sensorClass = sp[0];
            String sensitivity = sp[1];
            String priorityStr = sp[2];
            try {
                sensor = (Sensor)Class.forName(sensorClass).newInstance();
            } catch (Exception e) {
                LOG.warning("Exception caught while creating instance of Sensor "+sensorClass+" with sensitivity "+sensitivity+" and priority "+priorityStr);
                e.printStackTrace();
            }
            if(sensor != null) {
                BaseSensor baseSensor = (BaseSensor)sensor;
                baseSensor.setSensitivity(Envelope.Sensitivity.valueOf(sensitivity));
                baseSensor.setPriority(Integer.parseInt(priorityStr));
                baseSensor.setSensorManager(sensorManager);
                sensorManager.registerSensor(sensor);
                LOG.info("Registered sensor "+sensor.getClass().getName());
            }
        }
        if(sensorManager.init(properties) && peerManager.init(properties, seeds)) {
            Subscription subscription = new Subscription() {
                @Override
                public void notifyOfEvent(Envelope envelope) {
                    routeIn(envelope);
                }
            };

            // Subscribe to Text notifications
            SubscriptionRequest r = new SubscriptionRequest(EventMessage.Type.TEXT, subscription);
            Envelope e = Envelope.documentFactory();
            DLC.addData(SubscriptionRequest.class, r, e);
            DLC.addRoute(NotificationService.class, NotificationService.OPERATION_SUBSCRIBE, e);
//            producer.send(e);

            // Subscribe to DID status notifications
            SubscriptionRequest r2 = new SubscriptionRequest(EventMessage.Type.STATUS_DID, subscription);
            Envelope e2 = Envelope.documentFactory();
            DLC.addData(SubscriptionRequest.class, r2, e2);
            DLC.addRoute(NotificationService.class, NotificationService.OPERATION_SUBSCRIBE, e2);
//            producer.send(e2);

            // Credentials
            String username = "Alice";
            String passphrase = null;
            try {
                String credFileStr = getServiceDirectory().getAbsolutePath() + "/cred";
                File credFile = new File(credFileStr);
                if(!credFile.exists())
                    if(!credFile.createNewFile())
                        throw new Exception("Unable to create node credentials file at: "+credFileStr);

                properties.setProperty("onemfive.node.local.username",username);
                passphrase = FileUtil.readTextFile(credFileStr,1, true);
                if("".equals(passphrase) ||
                        (properties.getProperty("onemfive.node.local.rebuild")!=null && "true".equals(properties.getProperty("onemfive.node.local.rebuild")))) {
                    passphrase = HashUtil.generateHash(String.valueOf(System.currentTimeMillis()), Hash.Algorithm.SHA1).getHash();
                    if(!FileUtil.writeFile(passphrase.getBytes(), credFileStr)) {
                        LOG.warning("Unable to write local node Alice passphrase to file.");
                        return false;
                    }
                }
                properties.setProperty("onemfive.node.local.passphrase",passphrase);
            } catch (Exception ex) {
                LOG.warning(ex.getLocalizedMessage());
                return false;
            }

            // 3. Request local Peer
            Envelope e3 = Envelope.documentFactory();
            DLC.addRoute(SensorsService.class, SensorsService.OPERATION_RECEIVE_LOCAL_PEER, e3);
            // 2. Authenticate DID
            DID did = new DID();
            did.setUsername(username);
            did.setPassphrase(passphrase);
            AuthenticateDIDRequest adr = new AuthenticateDIDRequest();
            adr.did = did;
            adr.autogenerate = true;
            DLC.addData(AuthenticateDIDRequest.class,adr,e3);
            DLC.addRoute(DIDService.class, DIDService.OPERATION_AUTHENTICATE,e3);
            // 1. Load Public Key addresses for short and full addresses
            AuthNRequest ar = new AuthNRequest();
            ar.location = getServiceDirectory().getAbsolutePath();
            ar.keyRingUsername = username;
            ar.keyRingPassphrase = passphrase;
            ar.alias = username; // use username as default alias
            ar.aliasPassphrase = passphrase; // just use same passphrase
            ar.autoGenerate = true;

            DLC.addData(AuthNRequest.class, ar, e3);
            DLC.addRoute(KeyRingService.class, KeyRingService.OPERATION_AUTHN, e3);
//            producer.send(e3);

//            new AppThread(peerManager).start(); // Comment out for now

            updateStatus(ServiceStatus.RUNNING);
            LOG.info("Inkrypt CDNService Started.");
        }
        return true;
    }

    @Override
    public boolean restart() {
        updateStatus(ServiceStatus.RESTARTING);
        gracefulShutdown();
        return true;
    }

    @Override
    public boolean shutdown() {
        super.shutdown();
        if(getServiceStatus() != ServiceStatus.RESTARTING)
            updateStatus(ServiceStatus.SHUTTING_DOWN);
        sensorManager.shutdown();
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        // TODO: add wait/checks to ensure each sensor shutdowns
        return shutdown();
    }

}
