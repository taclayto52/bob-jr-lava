package com.bob.jr;

import com.google.common.util.concurrent.AbstractScheduledService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class HeartBeats extends AbstractScheduledService {

    private static final Logger logger = LoggerFactory.getLogger(HeartBeats.class);
    

    @Override
    protected void runOneIteration() throws Exception {
        logger.info("Scheduled heartbeat");
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(60, 60, TimeUnit.SECONDS);
    }
}
