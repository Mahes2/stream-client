package com.codespade.stream.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Response {
	@JsonProperty("status")
	private String status;
	
	@JsonProperty("total_id")
	private int totalId;
	
	@JsonProperty("verified_id")
	private int verifiedId;
	
	@JsonProperty("blocked_id")
	private int blockedId;
}
