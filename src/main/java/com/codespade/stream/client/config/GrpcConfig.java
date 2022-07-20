package com.codespade.stream.client.config;

import org.springframework.context.annotation.Configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

@Configuration
public class GrpcConfig {
	public ManagedChannel getChannel(AbstractGrpcConfig config) {
		ManagedChannelBuilder<?> managedChannelBuilder;
		if(config.isTls()) {
			managedChannelBuilder = NettyChannelBuilder
					.forAddress(config.getHost(), config.getPort())
					.negotiationType(NegotiationType.TLS);
		}else {
			managedChannelBuilder = NettyChannelBuilder
					.forAddress(config.getHost(), config.getPort())
					.negotiationType(NegotiationType.PLAINTEXT);
		}
		return managedChannelBuilder
				.enableRetry()
				.maxRetryAttempts(config.getMaxRetry())
				.build();
	}
}
