package io.onemfive.sensors;

import io.onemfive.core.ServiceRequest;
import io.onemfive.data.DID;
import io.onemfive.data.NetworkPeer;
import io.onemfive.data.content.Content;

/**
 * Request to use a Sensor for external communications.
 *
 * // TODO: Extend with a P2PSensorRequest and ResourceRequest. The former will contain the DIDs and the latter a URI.
 */
public class SensorRequest extends ServiceRequest {

    public static int TO_PEER_REQUIRED = 1;
    public static int TO_PEER_WRONG_NETWORK = 2;
    public static int NO_CONTENT = 3;
    public static int TO_PEER_NOT_FOUND = 4;
    public static int SENDING_FAILED = 5;

    public DID from;
    public DID to;
    public String content;
    // Upgrades
    public NetworkPeer toPeer;
    public NetworkPeer fromPeer;
    public NetworkPeer destinationPeer;
    public Content request;
    public Content response;
}
