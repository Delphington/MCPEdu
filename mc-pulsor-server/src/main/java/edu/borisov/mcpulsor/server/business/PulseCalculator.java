package edu.borisov.mcpulsor.server.business;

import java.util.Random;

public class PulseCalculator {

    public static final Random RANDOM = new Random();

    public static int getPulse(String name){
        return RANDOM.nextInt(100)+1;
    }
}
