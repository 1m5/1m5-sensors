package io.onemfive.sensors;

import java.util.Random;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class Util {

    public static long nextRandomLong() {
        return new Random(System.currentTimeMillis()).nextLong();
    }

}
