package io.onemfive.sensors.packet;

import io.onemfive.data.NetworkPeer;

import java.util.Map;

/**
 * Response
 *
 * @author objectorange
 */
public class ResponsePacket extends CommunicationPacket {

    private CommunicationPacket request;

    public ResponsePacket(){}

    public ResponsePacket(CommunicationPacket request, NetworkPeer fromPeer, NetworkPeer toPeer, StatusCode statusCode, long id) {
        super(fromPeer, toPeer, id);
        this.request = request;
        this.statusCode = statusCode;
    }

    private Long timeReceived;
    private StatusCode statusCode;
    private DataPacket dataPacket;

    public CommunicationPacket getRequest() {
        return request;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(StatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public DataPacket getDataPacket() {
        return dataPacket;
    }

    public void setDataPacket(DataPacket dataPacket) {
        this.dataPacket = dataPacket;
    }

    public long getTimeReceived() {
        return timeReceived;
    }

    public void setTimeReceived(long timeReceived) {
        this.timeReceived = timeReceived;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = super.toMap();
        if(request!=null) m.put("request",request.toMap());
        if(timeReceived!=null) m.put("timeReceived",String.valueOf(timeReceived));
        if(statusCode!=null) m.put("statusCode",statusCode.name());
        if(dataPacket!=null) m.put("dataPacket",dataPacket.toMap());
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        super.fromMap(m);
        if(m.get("request")!=null) {
            try {
                Map<String,Object> rM = (Map<String,Object>)m.get("request");
                request = (CommunicationPacket) Class.forName((String) rM.get("commPacketType")).newInstance();
                request.fromMap(rM);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if(m.get("timeReceived")!=null) timeReceived = Long.parseLong((String)m.get("timeReceived"));
        if(m.get("statusCode")!=null) statusCode = StatusCode.valueOf((String)m.get("statusCode"));
        if(m.get("dataPacket")!=null) {
            try {
                dataPacket = (DataPacket)Class.forName((String)m.get("dataPacketType")).newInstance();
                dataPacket.fromMap((Map<String,Object>)m.get("dataPacket"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
