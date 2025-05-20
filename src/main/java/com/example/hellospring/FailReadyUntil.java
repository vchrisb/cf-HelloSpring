package com.example.hellospring;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class FailReadyUntil {

	private volatile LocalDateTime failReadyUntil = LocalDateTime.now();

	LocalDateTime get() {
		return failReadyUntil;
	}

	void set() {
		failReadyUntil = LocalDateTime.now().plusMinutes(1);
	}

	void clear() {
		failReadyUntil = LocalDateTime.now();
	}

}
