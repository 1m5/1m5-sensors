package io.onemfive.sensors;

import io.onemfive.data.JSONSerializable;

import java.util.HashMap;
import java.util.Map;

/**
 * Relationships among Network Peers.
 *
 * @author objectorange
 */
public class P2PRelationship implements JSONSerializable {

    private Long totalAcks = 0L;
    private Long lastAckTime = 0L;
    private Long avgAckLatencyMS = 0L;
    private String ackTimesTracked;

    public Long advanceTotalAcks() {
        totalAcks++;
        return totalAcks;
    }

    public void setTotalAcks(long totalAcks) {
        this.totalAcks = totalAcks;
    }

    public Long getTotalAcks() {
        return totalAcks;
    }

    public void addAckTimeTracked(long t) {
        if(t <= 0) return; // not an ack
        if(ackTimesTracked ==null || ackTimesTracked.isEmpty()) {
            ackTimesTracked = String.valueOf(t);
        } else {
            ackTimesTracked += "," + String.valueOf(t);
        }
        int currNumberAcksTracked = ackTimesTracked.split(",").length;
        while(currNumberAcksTracked > Config.MaxAT) {
            ackTimesTracked = ackTimesTracked.substring(ackTimesTracked.indexOf(",")+1);
            currNumberAcksTracked--;
        }
    }

    public String getAckTimesTracked() {
        return ackTimesTracked;
    }

    public void setAckTimesTracked(String ackTimes) {
        this.ackTimesTracked = ackTimes;
    }

    public Long getAvgAckLatencyMS() {
        if(ackTimesTracked !=null) {
            String[] times = ackTimesTracked.split(",");
            long sum = 0L;
            long t;
            for (String ts : times) {
                t = Long.parseLong(ts);
                sum += t;
            }
            avgAckLatencyMS = sum / times.length;
        } else {
            avgAckLatencyMS = 0L;
        }
        return avgAckLatencyMS;
    }

    public void setAvgAckLatencyMS(Long avgAckLatencyMS) {
        this.avgAckLatencyMS = avgAckLatencyMS;
    }

    public Long getLastAckTime() {
        return lastAckTime;
    }

    public void setLastAckTime(Long lastAckTime) {
        this.lastAckTime = lastAckTime;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        if(totalAcks !=null) m.put("totalAcks", totalAcks);
        if(avgAckLatencyMS !=null) m.put("avgAckLatencyMS", avgAckLatencyMS);
        if(lastAckTime!=null) m.put("lastAckTime", lastAckTime);
        if(ackTimesTracked !=null) m.put("ackTimesTracked", ackTimesTracked);
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        if(m!=null) {
            if(m.get("totalAcks")!=null)
                totalAcks = (Long)m.get("totalAcks");
            if(m.get("avgAckLatencyMS")!=null)
                avgAckLatencyMS = (Long)m.get("avgAckLatencyMS");
            if(m.get("lastAckTime")!=null)
                lastAckTime = (Long)m.get("lastAckTime");
            if(m.get("ackTimesTracked")!=null)
                ackTimesTracked = (String)m.get("ackTimesTracked");
        }
    }
}
