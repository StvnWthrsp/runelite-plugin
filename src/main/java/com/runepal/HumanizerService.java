package com.runepal;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

/**
 * Service for generating human-like delays and timing patterns.
 * Uses Gaussian (normal) distribution to create more realistic delays
 * compared to simple random number generation.
 */
@Singleton
@Slf4j
public class HumanizerService {
    
    private final Random random = new Random();
    
    // Base delay configurations (in game ticks, where 1 tick = ~600ms)
    private static final int SHORT_DELAY_MEAN = 1;    // ~600ms average
    private static final int SHORT_DELAY_STD_DEV = 1; // Standard deviation
    
    private static final int MEDIUM_DELAY_MEAN = 3;   // ~1800ms average  
    private static final int MEDIUM_DELAY_STD_DEV = 1;
    
    private static final int LONG_DELAY_MEAN = 5;     // ~3000ms average
    private static final int LONG_DELAY_STD_DEV = 2;
    
    // Minimum delays to prevent zero or negative values
    private static final int MIN_SHORT_DELAY = 1;
    private static final int MIN_MEDIUM_DELAY = 2;
    private static final int MIN_LONG_DELAY = 3;
    
    /**
     * Gets a short human-like delay suitable for quick actions.
     * Average ~600ms with natural variation.
     * 
     * @return delay in game ticks
     */
    public int getShortDelay() {
        return getGaussianDelay(SHORT_DELAY_MEAN, SHORT_DELAY_STD_DEV, MIN_SHORT_DELAY);
    }
    
    /**
     * Gets a medium human-like delay suitable for moderate actions.
     * Average ~1800ms with natural variation.
     * 
     * @return delay in game ticks
     */
    public int getMediumDelay() {
        return getGaussianDelay(MEDIUM_DELAY_MEAN, MEDIUM_DELAY_STD_DEV, MIN_MEDIUM_DELAY);
    }
    
    /**
     * Gets a long human-like delay suitable for major actions.
     * Average ~3000ms with natural variation.
     * 
     * @return delay in game ticks
     */
    public int getLongDelay() {
        return getGaussianDelay(LONG_DELAY_MEAN, LONG_DELAY_STD_DEV, MIN_LONG_DELAY);
    }
    
    /**
     * Gets a custom human-like delay with specified parameters.
     * 
     * @param meanTicks the average delay in game ticks
     * @param stdDevTicks the standard deviation in game ticks
     * @param minTicks the minimum delay to ensure (prevents zero/negative)
     * @return delay in game ticks
     */
    public int getCustomDelay(int meanTicks, int stdDevTicks, int minTicks) {
        return getGaussianDelay(meanTicks, stdDevTicks, minTicks);
    }
    
    /**
     * Generates a delay using Gaussian distribution and ensures minimum value.
     * 
     * @param mean the mean value for the distribution
     * @param stdDev the standard deviation for the distribution  
     * @param minimum the minimum value to return
     * @return the calculated delay
     */
    private int getGaussianDelay(int mean, int stdDev, int minimum) {
        double gaussianValue = random.nextGaussian() * stdDev + mean;
        int delay = (int) Math.round(gaussianValue);

        // Ensure we never return a value below the minimum
        delay = Math.max(delay, minimum);

        log.debug("Generated humanized delay: {} ticks (~{}ms)", delay, delay * 600);
        return delay;
    }

    public double getGaussian(double mean, double stdDev, double minimum) {
        double gaussianValue = random.nextGaussian() * stdDev + mean;

        // Ensure we never return a value below the minimum
        gaussianValue = Math.max(gaussianValue, minimum);

        log.trace("Generated gaussian value: {}", gaussianValue);
        return gaussianValue;
    }
    
    /**
     * Gets a random delay within a specified range using Gaussian distribution
     * where the mean is the center of the range.
     * 
     * @param minTicks minimum delay in ticks
     * @param maxTicks maximum delay in ticks
     * @return delay in game ticks
     */
    public int getRangeDelay(int minTicks, int maxTicks) {
        if (minTicks >= maxTicks) {
            return minTicks;
        }
        
        int mean = (minTicks + maxTicks) / 2;
        int stdDev = (maxTicks - minTicks) / 6; // 99.7% of values within range
        
        return getGaussianDelay(mean, Math.max(1, stdDev), minTicks);
    }
    
    /**
     * Gets a random delay within a specified range (replacing old setRandomDelay pattern).
     * This method provides a uniform interface for all task classes to get random delays
     * instead of duplicating the delay logic across multiple classes.
     * 
     * @param minTicks minimum delay in ticks
     * @param maxTicks maximum delay in ticks
     * @return delay in game ticks
     */
    public int getRandomDelay(int minTicks, int maxTicks) {
        if (minTicks >= maxTicks) {
            return minTicks;
        }
        return getRangeDelay(minTicks, maxTicks);
    }
} 