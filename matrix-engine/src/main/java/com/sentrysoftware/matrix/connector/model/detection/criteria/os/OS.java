package com.sentrysoftware.matrix.connector.model.detection.criteria.os;

import java.util.HashSet;
import java.util.Set;

import com.sentrysoftware.matrix.connector.model.common.OSType;
import com.sentrysoftware.matrix.connector.model.detection.criteria.Criterion;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OS extends Criterion {

	private static final long serialVersionUID = -8982076836753923149L;

	private Set<OSType> keepOnly = new HashSet<>();
	private Set<OSType> exclude = new HashSet<>();

	@Builder
	public OS(boolean forceSerialization, Set<OSType> keepOnly, Set<OSType> exclude, int index) {
		super(forceSerialization, index);
		this.keepOnly = keepOnly;
		this.exclude = exclude;

	}

}
