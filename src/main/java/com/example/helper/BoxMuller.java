package com.example.helper;

public class BoxMuller {
    static public int generatePrice() {
        int mean = 32;
        int std_dev = 3;
        double u1 = Math.random();
        double u2 = Math.random();
        double z0 = (Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2));
        return (int) Math.ceil(z0 * std_dev + mean);
    }
}
