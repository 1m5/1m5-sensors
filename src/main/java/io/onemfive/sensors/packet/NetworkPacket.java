package io.onemfive.sensors.packet;

import io.onemfive.core.ServiceRequest;

import java.util.Map;

public abstract class NetworkPacket extends ServiceRequest {

    protected Boolean verify = false;

    public Boolean getVerify() {
        return verify;
    }

    public void setVerify(Boolean verify) {
        this.verify = verify;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String,Object> m = super.toMap();
        m.put("verify",verify);
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        super.fromMap(m);
        if(m.get("verify")!=null) verify = (Boolean)m.get("verify");
    }
}
