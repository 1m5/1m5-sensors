package io.onemfive.sensors.packet;

import io.onemfive.core.util.HashCash;
import io.onemfive.data.NetworkPeer;
import io.onemfive.sensors.Util;

import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public abstract class CommunicationPacket extends NetworkPacket {

    private static Logger LOG = Logger.getLogger(CommunicationPacket.class.getName());

    private Long id;
    protected NetworkPeer fromPeer;
    protected NetworkPeer toPeer;
    protected DataPacket dataPacket;
    private long timeSent = 0;
    private long timeDelivered = 0;
    private long timeAcknowledged = 0;
    protected String commPacketType;

    private HashCash hashCash;

    public CommunicationPacket(){
        id = Util.nextRandomLong();
        try {
            hashCash = HashCash.mintCash(new Random().nextLong()+"",1);
        } catch (NoSuchAlgorithmException e) {
            LOG.warning(e.getLocalizedMessage());
        }
    }

    public CommunicationPacket(DataPacket dataPacket) {
        this();
        this.dataPacket = dataPacket;
    }

    public CommunicationPacket(NetworkPeer fromPeer, NetworkPeer toPeer) {
        this();
        this.fromPeer = fromPeer;
        this.toPeer = toPeer;
    }

    protected CommunicationPacket(NetworkPeer fromPeer, NetworkPeer toPeer, long id) {
        // Don't call this() constructor as this constructor should only be called by the Response Packet
        // and we don't want to spend the processing generating a HashCash unnecessarily.
        this.fromPeer = fromPeer;
        this.toPeer = toPeer;
        this.id = id;
    }

    public CommunicationPacket(NetworkPeer fromPeer, NetworkPeer toPeer, DataPacket dataPacket) {
        this(fromPeer, toPeer);
        this.dataPacket = dataPacket;
    }

    public CommunicationPacket(NetworkPeer fromPeer, NetworkPeer toPeer, DataPacket dataPacket, long timeSent) {
        this(fromPeer, toPeer, dataPacket);
        this.timeSent = timeSent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NetworkPeer getFromPeer() {
        return fromPeer;
    }

    public void setFromPeer(NetworkPeer fromPeer) {
        this.fromPeer = fromPeer;
    }

    public NetworkPeer getToPeer() {
        return toPeer;
    }

    public void setToPeer(NetworkPeer toPeer) {
        this.toPeer = toPeer;
    }

    public DataPacket getDataPacket() {
        return dataPacket;
    }

    public void setDataPacket(DataPacket dataPacket) {
        this.dataPacket = dataPacket;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(long timeSent) {
        this.timeSent = timeSent;
    }

    public long getTimeDelivered() {
        return timeDelivered;
    }

    public void setTimeDelivered(long timeDelivered) {
        this.timeDelivered = timeDelivered;
    }

    public long getTimeAcknowledged() {
        return timeAcknowledged;
    }

    public void setTimeAcknowledged(long timeAcknowledged) {
        this.timeAcknowledged = timeAcknowledged;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String,Object> m = super.toMap();
        if(id != null) m.put("id",String.valueOf(id));
        if(fromPeer != null) m.put("fromPeer",fromPeer.toMap());
        if(toPeer != null) m.put("toPeer", toPeer.toMap());
        if(dataPacket != null) m.put("dataPacket", dataPacket.toMap());
        if(timeSent > 0) m.put("timeSent", String.valueOf(timeSent));
        if(timeDelivered > 0) m.put("timeDelivered", String.valueOf(timeDelivered));
        if(timeAcknowledged > 0) m.put("timeAcknowledged", String.valueOf(timeAcknowledged));
        m.put("commPacketType",getClass().getName());
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        super.fromMap(m);
        if(m.get("id") != null) id = Long.parseLong((String)m.get("id"));
        if(m.get("fromPeer") != null) {
            fromPeer = new NetworkPeer();
            fromPeer.fromMap((Map<String,Object>)m.get("fromPeer"));
        }
        if(m.get("toPeer") != null) {
            toPeer = new NetworkPeer();
            toPeer.fromMap(((Map<String,Object>)m.get("toPeer")));
        }
        if(m.get("dataPacket") != null) {
            Map<String,Object> dm = (Map<String,Object>)m.get("dataPacket");
            String dataPacketType = (String)dm.get("dataPacketType");
            LOG.info("dataPacketType:"+dataPacketType);
            if(dataPacketType != null) {
                dataPacket = null;
                try {
                    dataPacket = (DataPacket)Class.forName(dataPacketType).newInstance();
                } catch (Exception e) {
                    e.printStackTrace();
                    LOG.warning("Exception caught while creating DataPacket instance of type: "+dataPacketType);
                }
                if(dataPacket!=null){
                    dataPacket.fromMap(dm);
                }
            }
        }
        if(m.get("timeSent") != null) timeSent = Long.parseLong((String)m.get("timeSent"));
        if(m.get("timeDelivered") != null) timeDelivered = Long.parseLong((String)m.get("timeDelivered"));
        if(m.get("timeAcknowledged") != null) timeAcknowledged = Long.parseLong((String)m.get("timeAcknowledged"));
        commPacketType = (String)m.get("dataPacketType");
    }
}
