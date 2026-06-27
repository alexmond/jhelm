package org.alexmond.jhelm.core.model;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseStatusTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void getValueReturnsHelmWireStrings() {
		assertEquals("unknown", ReleaseStatus.UNKNOWN.getValue());
		assertEquals("deployed", ReleaseStatus.DEPLOYED.getValue());
		assertEquals("uninstalled", ReleaseStatus.UNINSTALLED.getValue());
		assertEquals("superseded", ReleaseStatus.SUPERSEDED.getValue());
		assertEquals("failed", ReleaseStatus.FAILED.getValue());
		assertEquals("uninstalling", ReleaseStatus.UNINSTALLING.getValue());
		assertEquals("pending-install", ReleaseStatus.PENDING_INSTALL.getValue());
		assertEquals("pending-upgrade", ReleaseStatus.PENDING_UPGRADE.getValue());
		assertEquals("pending-rollback", ReleaseStatus.PENDING_ROLLBACK.getValue());
	}

	@Test
	void fromValueMapsEachWireStringBack() {
		for (ReleaseStatus status : ReleaseStatus.values()) {
			assertEquals(status, ReleaseStatus.fromValue(status.getValue()));
		}
	}

	@Test
	void fromValueIsCaseInsensitive() {
		assertEquals(ReleaseStatus.DEPLOYED, ReleaseStatus.fromValue("DEPLOYED"));
		assertEquals(ReleaseStatus.DEPLOYED, ReleaseStatus.fromValue("Deployed"));
		assertEquals(ReleaseStatus.PENDING_INSTALL, ReleaseStatus.fromValue("Pending-Install"));
	}

	@Test
	void fromValueUnknownAndNullYieldUnknown() {
		assertEquals(ReleaseStatus.UNKNOWN, ReleaseStatus.fromValue("nonsense"));
		assertEquals(ReleaseStatus.UNKNOWN, ReleaseStatus.fromValue(null));
	}

	@Test
	void serializesToHelmWireString() {
		assertEquals("\"deployed\"", mapper.writeValueAsString(ReleaseStatus.DEPLOYED));
		assertEquals("\"pending-install\"", mapper.writeValueAsString(ReleaseStatus.PENDING_INSTALL));
	}

	@Test
	void deserializesFromHelmWireString() {
		assertEquals(ReleaseStatus.DEPLOYED, mapper.readValue("\"deployed\"", ReleaseStatus.class));
		assertEquals(ReleaseStatus.PENDING_UPGRADE, mapper.readValue("\"pending-upgrade\"", ReleaseStatus.class));
		assertEquals(ReleaseStatus.UNKNOWN, mapper.readValue("\"bogus\"", ReleaseStatus.class));
	}

	@Test
	void releaseInfoRoundTripsStatusField() {
		Release.ReleaseInfo info = Release.ReleaseInfo.builder().status(ReleaseStatus.DEPLOYED).build();
		String json = mapper.writeValueAsString(info);
		// the status field serializes to the Helm wire string
		assertTrue(json.contains("\"status\":\"deployed\""), "expected wire string in: " + json);

		Release.ReleaseInfo back = mapper.readValue(json, Release.ReleaseInfo.class);
		assertEquals(ReleaseStatus.DEPLOYED, back.getStatus());
	}

}
