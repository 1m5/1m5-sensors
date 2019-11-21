package io.onemfive.sensors.packet;

import io.onemfive.core.util.data.Base64;

import java.util.Map;

public abstract class DataPacket extends NetworkPacket {

    protected byte[] data;
    protected String dataPacketType;

    public DataPacket(){}

    public DataPacket(byte[] data) {
        this.data = data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String,Object> m = super.toMap();
        if(data != null) m.put("data", Base64.encode(data));
        m.put("dataPacketType",getClass().getName());
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        super.fromMap(m);
        if(m.get("data") != null) data = Base64.decode((String)m.get("data"));
        dataPacketType = (String)m.get("dataPacketType");
    }
}
