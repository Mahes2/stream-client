package com.codespade.stream.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "grpc.1")
public class StreamGrpc1Config extends AbstractGrpcConfig{

}
