package io.onemfive.sensors;

import io.onemfive.data.NetworkPeer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SensorsConfig {

    public static void update(Properties properties) {
        if(properties.getProperty("onemfive.sensors.seeds") != null) {
            String i2pSeedsStr = properties.getProperty("onemfive.sensors.seeds");
            if(i2pSeedsStr!=null && !"".equals(i2pSeedsStr)) {
                String[] sl = i2pSeedsStr.split(",");
                NetworkPeer np;
                String[] na;
                for(String s : sl) {
//                    na = s.split("|");
//                    np = new NetworkPeer(na[0]);
//                    np.setAddress(na[1]);
                    // For now just assume I2P
                    np = new NetworkPeer(NetworkPeer.Network.I2P.name());
                    np.setAddress(s);
                    seeds.add(np);
                }
            }
        }
        if(properties.getProperty("onemfive.sensors.banned") != null) {
            String i2pBannedStr = properties.getProperty("onemfive.sensors.banned");
            if(i2pBannedStr!=null && !"".equals(i2pBannedStr)) {
                String[] bl = i2pBannedStr.split(",");
                NetworkPeer np;
                String[] na;
                for(String b : bl) {
                    na = b.split("|");
                    np = new NetworkPeer(na[0]);
                    np.setAddress(na[1]);
                    banned.add(np);
                }
            }
        }
        if(properties.getProperty("onemfive.sensors.MinPT") != null) {
            MinPT = Integer.parseInt(properties.getProperty("onemfive.sensors.MinPT"));
        }
        if(properties.getProperty("onemfive.sensors.MaxPT") != null) {
            MaxPT = Integer.parseInt(properties.getProperty("onemfive.sensors.MaxPT"));
        }
        if(properties.getProperty("onemfive.sensors.MaxPS") != null) {
            MaxPS = Integer.parseInt(properties.getProperty("onemfive.sensors.MaxPS"));
        }
        if(properties.getProperty("onemfive.sensors.MaxAT") != null) {
            MaxAT = Integer.parseInt(properties.getProperty("onemfive.sensors.MaxAT"));
        }
        if(properties.getProperty("onemfive.sensors.UI") != null) {
            UI = Integer.parseInt(properties.getProperty("onemfive.sensors.UI"));
        }
        if(properties.getProperty("onemfive.sensors.MinAckRP") != null) {
            MinAckRP = Integer.parseInt(properties.getProperty("onemfive.sensors.MinAckRP"));
        }
        if(properties.getProperty("onemfive.sensors.MinAckSRP") != null) {
            MinAckSRP = Integer.parseInt(properties.getProperty("onemfive.sensors.MinAckSRP"));
        }
    }

    // ------------ Discovery ---------------
    // Seeds
    public static List<NetworkPeer> seeds = new ArrayList<>();
    // Banned
    public static List<NetworkPeer> banned = new ArrayList<>();
    // Min Peers Tracked - the point at which Discovery process goes into 'hyper' mode.
    public static int MinPT = 10;
    // Max Peers Tracked - the total number of Peers to attempt to maintain knowledge of
    public static int MaxPT = 100;
    // Max Peers Sent - Maximum number of peers to send in a peer list (the bigger a datagram, the less chance of it getting through).
    public static int MaxPS = 5;
    // Max Acknowledgments Tracked
    public static int MaxAT = 20;
    // Update Interval - seconds between Discovery process
    public static int UI = 60;
    // Reliable Peer Min Acks
    public static int MinAckRP = 20;
    // Super Reliable Peer Min Acks
    public static int MinAckSRP = 10000;

}
