package io.onemfive.sensors;

import io.onemfive.data.Envelope;

import java.util.Properties;

public interface SensorManager {

    String TOR_SENSOR_NAME = "io.onemfive.tor.client.TorClientSensor";
    String I2P_SENSOR_NAME = "io.onemfive.i2p.I2PSensor";
    String IDN_SENSOR_NAME = "io.onemfive.idn.IDNSensor";
    String RADIO_SENSOR_NAME = "io.onemfive.radio.RadioSensor";
    String LIFI_SENSOR_NAME ="io.onemfive.lifi.LiFiSensor";

    boolean init(Properties properties);
    Sensor selectSensor(Envelope envelope);
    void registerSensor(Sensor sensor);
    void updateSensorStatus(final String sensorID, SensorStatus sensorStatus);
    boolean shutdown();
}
