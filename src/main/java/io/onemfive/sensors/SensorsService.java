package io.onemfive.sensors;

import io.onemfive.core.*;
import io.onemfive.core.keyring.AuthNRequest;
import io.onemfive.core.keyring.KeyRingService;
import io.onemfive.core.notification.NotificationService;
import io.onemfive.core.notification.SubscriptionRequest;
import io.onemfive.core.util.tasks.TaskRunner;
import io.onemfive.data.*;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.FileUtil;
import io.onemfive.data.util.HashUtil;
import io.onemfive.data.util.JSONParser;
import io.onemfive.did.AuthenticateDIDRequest;
import io.onemfive.did.DIDService;
import io.onemfive.sensors.peers.BasePeerManager;
import io.onemfive.sensors.peers.PeerManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

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
    private BasePeerManager peerManager;
    private File sensorsDirectory;
    private Properties properties;
    private TaskRunner taskRunner;

    public SensorsService() {
        super();
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    Properties getProperties() {
        return properties;
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
        switch (r.getOperation()) {
            case OPERATION_SEND : {
                SensorRequest request = (SensorRequest)DLC.getData(SensorRequest.class,e);
                if(request == null){
                    LOG.info("No SensorRequest in Envelope. Not a P2P request...making resource request...");
                } else {
                    DID to = request.to;
                    if (to != null) {
                        NetworkPeer peer = to.getPrioritizedPeer();
                        if (peer == null) {
                            LOG.warning("No Network Peers in TO address. Unable to send.");
                            return;
                        }
                        peerManager.verifyPeer(peer);
                    }
                }
                Sensor sensor = sensorManager.selectSensor(e);
                if(sensor != null) {
                    LOG.info("Sending Envelope to selected Sensor...");
                    if (!sensor.send(e)) {
                        Message m = e.getMessage();
                        boolean reroute = false;
                        if (m != null && m.getErrorMessages() != null && m.getErrorMessages().size() > 0) {
                            for (String err : m.getErrorMessages()) {
                                LOG.warning(err);
                                if ("BLOCKED".equals(err)) {
                                    if (e.getSensitivity() == Envelope.Sensitivity.NONE) {
                                        LOG.info("No security required. Assuming block means the site is down.");
                                    } else {
                                        LOG.info("Some level of security required. Re-routing through another peer.");
                                        reroute = true;
                                    }
                                }
                            }
                        }
                        if (reroute || sensor.getStatus() == SensorStatus.NETWORK_BLOCKED) {
                            LOG.info("Can we reroute?");
                            String fromNetwork = sensor.getClass().getName();
                            sensor = sensorManager.selectSensor(e);
                            if (sensor != null) {
                                String toNetwork = sensor.getClass().getName();
                                if (!fromNetwork.equals(toNetwork)) {
                                    LOG.info("Escalated sensor: " + toNetwork);
                                    NetworkPeer newToPeer = peerManager.getRandomPeer(peerManager.getLocalPeer());
                                    if (newToPeer == null) {
                                        LOG.warning("No other peers to route blocked request. Request is dead.");
                                    } else {
                                        // Clear error messages
                                        if (m != null) {
                                            m.clearErrorMessages();
                                        }
                                        // Send through escalated network
                                        sensor.send(e);
                                    }
                                }
                            } else {
                                LOG.warning("Rerouting desired but no Sensor available for rerouting.");
                            }
                        }
                    }
                } else {
                    LOG.warning("No sensor available to send message. Dead lettering...");
                    deadLetter(e);
                }
                break;
            }
            case OPERATION_REPLY : {
                LOG.info("Replying with Envelope to requester...");
                Sensor sensor = sensorManager.selectSensor(e);
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
                obj = Class.forName(type).getConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                LOG.warning("No constructor for class: " + type);
                deadLetter(envelope);
                return;
            } catch (InvocationTargetException e) {
                LOG.warning("Unable to invoke new class of type: "+type);
                deadLetter(envelope);
                return;
            } catch (InstantiationException e) {
                LOG.warning("Unable to instantiate class: " + type);
                deadLetter(envelope);
                return;
            } catch (IllegalAccessException e) {
                LOG.severe("Class of type "+type+" is not accessible.");
                deadLetter(envelope);
                return;
            } catch (ClassNotFoundException e) {
                LOG.warning("Class not on classpath: " + type);
                deadLetter(envelope);
                return;
            }
            if (obj instanceof Packet) {
                LOG.info("Object a Packet...");
                Packet packet = (Packet) obj;
                packet.fromMap(mp);
                switch (type) {
                    case "io.onemfive.sensors.packet.PeerStatus": {
                        pingIn((PeerStatusRequest) packet);
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
    public void pingIn(PeerStatusRequest request) {
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
                if (req instanceof PeerStatusRequest) {
                    LOG.info("PeerStatus response received from PeerStatus request.");
                    LOG.info("Saving peer status times in graph...");
                    if(peerManager.savePeerStatusTimes(req.getFromPeer(), req.getToPeer(), req.getTimeSent(), req.getTimeAcknowledged())) {
                        LOG.info("Updating reliables in graph...");
                        peerManager.reliablesFromRemotePeer(req.getToPeer(), ((PeerStatusRequest)req).getReliablePeers());
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
        PeerStatusRequest ps = new PeerStatusRequest(peerManager.getLocalPeer(), peerToProbe);
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
        LOG.info("Current Sensors Service Status: "+currentServiceStatus+"; Inbound sensor status: "+sensorStatus.name());
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
        LOG.info("Verifying all sensors with status: "+sensorStatus.name());
        Collection<Sensor> sensors = ((SensorManagerBase)sensorManager).getRegisteredSensors().values();
        if(sensors.size() == 0) {
            return false;
        }
        for(Sensor s : sensors) {
            LOG.info(s.getClass().getName()+" status: "+s.getStatus().name());
            if(s.getStatus() != sensorStatus){
                return false;
            }
        }
        return true;
    }

    public SensorManager getSensorManager() {
        return sensorManager;
    }

    public File getSensorsDirectory() {
        return sensorsDirectory;
    }

    @Override
    public boolean start(Properties p) {
        super.start(p);
        LOG.info("Starting...");
        updateStatus(ServiceStatus.STARTING);
        try {
            properties = Config.loadFromClasspath("sensors.config", p, false);
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }

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
            sensorManager = (SensorManager)Class.forName(sensorManagerClass).getConstructor().newInstance();
            ((SensorManagerBase)sensorManager).setSensorsService(this);
        } catch (Exception e) {
            LOG.warning("Exception caught while creating instance of Sensor Manager "+sensorManagerClass);
            e.printStackTrace();
            return false;
        }

        // TODO: use global TaskRunner instance
        taskRunner = new TaskRunner();

        // Peer Manager
        try {
            peerManager = (BasePeerManager) Class.forName(peerManagerClass).getConstructor().newInstance();
            peerManager.setSensorsService(this);
            peerManager.setTaskRunner(taskRunner);
            sensorManager.setPeerManager(peerManager);
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
                sensor = (Sensor)Class.forName(sensorClass).getConstructor().newInstance();
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
        if(sensorManager.init(properties) && peerManager.init(properties)) {
            Subscription subscription = this::routeIn;

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
            // Comment out for now
            producer.send(e3);
//            new AppThread(peerManager).start();


            updateStatus(ServiceStatus.WAITING);
            LOG.info("Sensors Service Started.");
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
