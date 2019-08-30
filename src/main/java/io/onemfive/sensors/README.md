# Sensors
Provides an intelligent router as an overlay network using I2P for a base level of anonymity P2P and Tor when accessing clearnet web services. 
The module participates with the DID Service for self-sovereign identity and reputation based access. 
When internet access is not available, it switches to 1DN, a wireless direct ad-hoc network comprised of the Radio and LiFi Sensors. 
As long as enough people still have their device, the network survives.

## Sensor Service
Primary entry-point into managing and using Sensors.
Contains a Sensor Manager and Peer Manager.

## Configuration

## Lifecycle

### Start

### Shutdown

### Graceful Shutdown

## Sensor Manager
Manages Sensors by offering registration then maintaining their status.
Sensors can be requested including with escalation.

### Simple Sensor Manager
Selects Sensor based on Sensitivity, Operation, and URL.

### Uncensored Sensor Manager
Extends Simple Sensor Manager by first using it for select the Sensor
required to satisfy the request while checking to determine if that
Sensor is unable to make the request on the local node. If so, it
determines what is the best Sensor to currently use to get the request
to another peer to make the uncensored request.

## Current Sensors Supported

### [Clearnet Client Sensor](https://github.com/1m5/1m5-clearnet-client)
Uses an HTTP client for making requests to clearnet servers.

### [Tor Client Sensor](https://github.com/1m5/1m5-tor-client)
Uses the Tor installation on the local machine to access the Tor network
for making HTTP requests over the clearnet.

### [I2P Sensor](https://github.com/1m5/1m5-i2p)
Launches an embedded I2P Router. 
I2P provides garlic routing to provide anonymity on the internet.
Future work will support running as an I2P Client with an external I2P router.

### [Radio Sensor](https://github.com/1m5/1m5-radio)
Provides access to the full radio spectrum. 
Both the Radio and LiFi Sensors make up the 1DN 'outernet'.

### [LiFI Sensor](https://github.com/1m5/1m5-lifi)
Provides access to the full light spectrum.
Both the Radio and LiFi Sensors make up the 1DN 'outernet'.

### [WiFi Direct Sensor](https://github.com/1m5/1m5-wifi-direct)
Old sensor work that will be merged into the Radio Sensor.