package com.sentrysoftware.hardware.agent.dto.protocol;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.sentrysoftware.hardware.agent.deserialization.TimeDeserializer;
import com.sentrysoftware.matrix.engine.protocol.IProtocolConfiguration;
import com.sentrysoftware.matrix.engine.protocol.SshProtocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SshProtocolDto extends AbstractProtocolDto {

	@Default
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	private String username;
	private char[] password;
	private File privateKey;
	private boolean useSudo;

	@Default
	private Set<String> useSudoCommands = new HashSet<>();

	@Default
	private String sudoCommand = "sudo";

	/**
	 * Create a new {@link SshProtocol} instance based on the current members
	 *
	 * @return The {@link SshProtocol} instance
	 */
	@Override
	public IProtocolConfiguration toProtocol() {
		return SshProtocol
				.builder()
				.username(username)
				.password(super.decrypt(password))
				.privateKey(privateKey)
				.timeout(timeout)
				.useSudo(useSudo)
				.useSudoCommands(useSudoCommands)
				.sudoCommand(sudoCommand)
				.build();
	}

	@Override
	public String toString() {
		String desc = "SSH";
		if (username != null) {
			desc = desc + " as " + username;
		}
		return desc;
	}
}
