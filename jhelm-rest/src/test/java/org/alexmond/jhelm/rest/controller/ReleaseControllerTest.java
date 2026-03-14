package org.alexmond.jhelm.rest.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.JhelmRestExceptionHandler;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReleaseController.class)
@Import(JhelmRestExceptionHandler.class)
@EnableConfigurationProperties(JhelmRestProperties.class)
class ReleaseControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ListAction listAction;

	@MockitoBean
	private StatusAction statusAction;

	@MockitoBean
	private GetAction getAction;

	@MockitoBean
	private HistoryAction historyAction;

	@MockitoBean
	private InstallAction installAction;

	@MockitoBean
	private UpgradeAction upgradeAction;

	@MockitoBean
	private UninstallAction uninstallAction;

	@MockitoBean
	private RollbackAction rollbackAction;

	@MockitoBean
	private TestAction testAction;

	@MockitoBean
	private ChartLoader chartLoader;

	@MockitoBean
	private RepoManager repoManager;

	private static Release sampleRelease() {
		return Release.builder()
			.name("my-release")
			.namespace("default")
			.version(1)
			.chart(Chart.builder()
				.metadata(ChartMetadata.builder().name("nginx").version("1.0.0").appVersion("1.25").build())
				.build())
			.info(Release.ReleaseInfo.builder()
				.status("deployed")
				.description("Install complete")
				.lastDeployed(OffsetDateTime.now())
				.build())
			.manifest("apiVersion: v1\nkind: ConfigMap")
			.build();
	}

	@Test
	void listReleases() throws Exception {
		when(this.listAction.list("default")).thenReturn(List.of(sampleRelease()));
		this.mockMvc.perform(get("/api/v1/releases").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("my-release"))
			.andExpect(jsonPath("$[0].chartName").value("nginx"));
	}

	@Test
	void statusReturnsRelease() throws Exception {
		when(this.statusAction.status("my-release", "default")).thenReturn(Optional.of(sampleRelease()));
		this.mockMvc.perform(get("/api/v1/releases/my-release").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("my-release"))
			.andExpect(jsonPath("$.status").value("deployed"));
	}

	@Test
	void statusReturnsNotFound() throws Exception {
		when(this.statusAction.status("missing", "default")).thenReturn(Optional.empty());
		this.mockMvc.perform(get("/api/v1/releases/missing").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound());
	}

	@Test
	void installCreatesRelease() throws Exception {
		stubPull();
		Chart chart = Chart.builder().metadata(ChartMetadata.builder().name("nginx").version("1.0.0").build()).build();
		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		when(this.installAction.install(any(), eq("my-release"), eq("default"), anyMap(), anyInt(), anyBoolean()))
			.thenReturn(sampleRelease());

		this.mockMvc
			.perform(post("/api/v1/releases").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartRef": "bitnami/nginx", "version": "1.0.0", "releaseName": "my-release"}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("my-release"));
	}

	@Test
	void installRejectsMissingChartRef() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/releases").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"releaseName": "my-release"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("chartRef is required"));
	}

	@Test
	void installUploadCreatesRelease() throws Exception {
		stubUntar();
		Chart chart = Chart.builder().metadata(ChartMetadata.builder().name("nginx").version("1.0.0").build()).build();
		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		when(this.installAction.install(any(), eq("my-release"), eq("default"), anyMap(), anyInt(), anyBoolean()))
			.thenReturn(sampleRelease());

		MockMultipartFile chartFile = new MockMultipartFile("chart", "nginx-1.0.0.tgz", "application/gzip",
				new byte[] { 1, 2, 3 });
		MockMultipartFile requestPart = new MockMultipartFile("request", "", "application/json", """
				{"releaseName": "my-release"}
				""".getBytes());

		this.mockMvc.perform(multipart("/api/v1/releases/upload").file(chartFile).file(requestPart))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("my-release"));
	}

	@Test
	void upgradeRelease() throws Exception {
		Release current = sampleRelease();
		when(this.getAction.getRelease("my-release", "default")).thenReturn(Optional.of(current));
		stubPull();
		Chart chart = Chart.builder().metadata(ChartMetadata.builder().name("nginx").version("2.0.0").build()).build();
		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		Release upgraded = sampleRelease();
		upgraded.setVersion(2);
		when(this.upgradeAction.upgrade(any(), any(), anyMap(), anyBoolean())).thenReturn(upgraded);

		this.mockMvc
			.perform(put("/api/v1/releases/my-release").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"chartRef": "bitnami/nginx", "version": "2.0.0"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void upgradeUploadRelease() throws Exception {
		Release current = sampleRelease();
		when(this.getAction.getRelease("my-release", "default")).thenReturn(Optional.of(current));
		stubUntar();
		Chart chart = Chart.builder().metadata(ChartMetadata.builder().name("nginx").version("2.0.0").build()).build();
		when(this.chartLoader.load(any(File.class))).thenReturn(chart);
		Release upgraded = sampleRelease();
		upgraded.setVersion(2);
		when(this.upgradeAction.upgrade(any(), any(), anyMap(), anyBoolean())).thenReturn(upgraded);

		MockMultipartFile chartFile = new MockMultipartFile("chart", "nginx-2.0.0.tgz", "application/gzip",
				new byte[] { 1, 2, 3 });
		MockMultipartFile requestPart = new MockMultipartFile("request", "", "application/json", "{}".getBytes());

		this.mockMvc.perform(multipart("/api/v1/releases/my-release/upgrade/upload").file(chartFile).file(requestPart))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void uninstallRelease() throws Exception {
		this.mockMvc.perform(delete("/api/v1/releases/my-release")).andExpect(status().isNoContent());
		verify(this.uninstallAction).uninstall("my-release", "default");
	}

	@Test
	void history() throws Exception {
		when(this.historyAction.history("my-release", "default")).thenReturn(List.of(sampleRelease()));
		this.mockMvc.perform(get("/api/v1/releases/my-release/history").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].version").value(1));
	}

	@Test
	void rollback() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/releases/my-release/rollback").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("""
						{"revision": 1}
						"""))
			.andExpect(status().isNoContent());
		verify(this.rollbackAction).rollback("my-release", "default", 1);
	}

	@Test
	void testEndpoint() throws Exception {
		TestAction.TestResult result = new TestAction.TestResult();
		result.setName("test-pod");
		result.setKind("Pod");
		result.setStatus(TestAction.TestStatus.PASSED);
		when(this.testAction.test("my-release", "default", 300)).thenReturn(List.of(result));

		this.mockMvc.perform(post("/api/v1/releases/my-release/test").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("PASSED"));
	}

	@Test
	void getValues() throws Exception {
		when(this.getAction.getRelease("my-release", "default")).thenReturn(Optional.of(sampleRelease()));
		when(this.getAction.getValues(any(), anyBoolean())).thenReturn("replicas: 2");

		this.mockMvc.perform(get("/api/v1/releases/my-release/values").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
	}

	@Test
	void getManifest() throws Exception {
		when(this.getAction.getRelease("my-release", "default")).thenReturn(Optional.of(sampleRelease()));
		when(this.getAction.getManifest(any())).thenReturn("apiVersion: v1\nkind: ConfigMap");

		this.mockMvc.perform(get("/api/v1/releases/my-release/manifest").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
	}

	@Test
	void getNotes() throws Exception {
		when(this.getAction.getRelease("my-release", "default")).thenReturn(Optional.of(sampleRelease()));
		when(this.getAction.getNotes(any())).thenReturn("Thank you for installing nginx!");

		this.mockMvc.perform(get("/api/v1/releases/my-release/notes").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
	}

	@Test
	void getHooks() throws Exception {
		when(this.getAction.getRelease("my-release", "default")).thenReturn(Optional.of(sampleRelease()));
		this.mockMvc.perform(get("/api/v1/releases/my-release/hooks").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	@Test
	void getResources() throws Exception {
		when(this.statusAction.status("my-release", "default")).thenReturn(Optional.of(sampleRelease()));
		when(this.statusAction.getResourceStatuses(any())).thenReturn(List.of());
		this.mockMvc.perform(get("/api/v1/releases/my-release/resources").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	@Test
	void getResourcesNotFound() throws Exception {
		when(this.statusAction.status("missing", "default")).thenReturn(Optional.empty());
		this.mockMvc.perform(get("/api/v1/releases/missing/resources").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound());
	}

	private void stubPull() throws Exception {
		doAnswer((invocation) -> {
			String destDir = invocation.getArgument(2);
			Path chartDir = Path.of(destDir).resolve("nginx");
			Files.createDirectories(chartDir);
			Files.writeString(chartDir.resolve("Chart.yaml"), "name: nginx\nversion: 1.0.0");
			return null;
		}).when(this.repoManager).pull(anyString(), any(), anyString());
	}

	private void stubUntar() throws Exception {
		doAnswer((invocation) -> {
			File destDir = invocation.getArgument(1);
			Path chartDir = destDir.toPath().resolve("nginx");
			Files.createDirectories(chartDir);
			Files.writeString(chartDir.resolve("Chart.yaml"), "name: nginx\nversion: 1.0.0");
			return null;
		}).when(this.repoManager).untar(any(File.class), any(File.class));
	}

}
