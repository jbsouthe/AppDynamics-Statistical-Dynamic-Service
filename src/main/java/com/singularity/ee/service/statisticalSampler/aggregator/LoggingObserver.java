package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.util.log4j.IADLogger;

import java.util.Observable;
import java.util.Observer;

public class LoggingObserver implements Observer {
    IADLogger logger;

    public LoggingObserver(IADLogger logger) {
        this.logger=logger;
    }

    /**
     * This method is called whenever the observed object is changed. An
     * application calls an <tt>Observable</tt> object's
     * <code>notifyObservers</code> method to have all the object's
     * observers notified of the change.
     *
     * @param o   the observable object.
     * @param arg an argument passed to the <code>notifyObservers</code>
     *            method.
     */
    @Override
    public void update(Observable o, Object arg) {
        logger.debug(String.format("Observed: %s %s",o,arg));
    }
}
