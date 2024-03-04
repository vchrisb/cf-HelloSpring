package com.example.hellospring;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CustomState extends ReadinessStateHealthIndicator {

    @Autowired
    private FailReadyUntil failReadyUntil;

    public CustomState(ApplicationAvailability availability) {
        super(availability);
      }

    @Override
    protected AvailabilityState getState(ApplicationAvailability applicationAvailability) {
        return LocalDateTime.now().isBefore(failReadyUntil.get())
            ? ReadinessState.REFUSING_TRAFFIC
            : ReadinessState.ACCEPTING_TRAFFIC;
    }
}