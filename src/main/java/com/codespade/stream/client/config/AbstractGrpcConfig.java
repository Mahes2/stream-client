package com.codespade.stream.client.config;

import lombok.Data;

@Data
public abstract class AbstractGrpcConfig {
	private String host;
	private int port;
	private int deadlineAfter;
	private boolean enableRetry;
	private int maxRetry;
	private boolean tls;
}
