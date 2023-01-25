package com.sentrysoftware.matrix.connector.model.identity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sentrysoftware.matrix.connector.model.identity.criterion.Criterion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Detection implements Serializable {

	private static final long serialVersionUID = 1L;

	private ConnectionType connectionType;

	private boolean noAutoDetection;

	private String onLastResort;

	@Default
	private Set<String> appliesTo = new HashSet<>();

	@Default
	private Set<String> supersedes = new HashSet<>();

	@Default
	private List<Criterion> criteria = new ArrayList<>();
}
