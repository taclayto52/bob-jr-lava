package com.bob.jr.utils;

import org.slf4j.Logger;

import java.util.function.Consumer;

public class FluxUtils {

    public static Consumer<Throwable> logFluxError(Logger logger, String sourceFunction) {
        return throwable -> {
            logger.error("Error noticed in {} - {}", String.format(sourceFunction), throwable.getMessage());
            throwable.printStackTrace();
        };
    }

}
