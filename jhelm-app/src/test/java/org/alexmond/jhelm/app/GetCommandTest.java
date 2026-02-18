package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class GetCommandTest {

    @Mock
    private GetAction getAction;

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void testGetCommandShowsUsage() {
        GetCommand getCommand = new GetCommand();
        getCommand.run();

        String output = outputStream.toString();
        assertTrue(output.contains("get") || output.contains("Usage"));
    }

    @Test
    void testValuesCommand() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getValues(release, false)).thenReturn("key: value\n");

        GetCommand.ValuesCommand cmd = new GetCommand.ValuesCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("key: value"));
    }

    @Test
    void testValuesCommandWithAllFlag() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getValues(release, true)).thenReturn("merged: values\n");

        GetCommand.ValuesCommand cmd = new GetCommand.ValuesCommand(getAction);
        new CommandLine(cmd).execute("my-release", "--all");

        assertTrue(outputStream.toString().contains("merged: values"));
    }

    @Test
    void testValuesCommandJsonOutput() throws Exception {
        Release release = createReleaseWithConfig();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.toJson(any())).thenReturn("{\"key\": \"value\"}");

        GetCommand.ValuesCommand cmd = new GetCommand.ValuesCommand(getAction);
        new CommandLine(cmd).execute("my-release", "-o", "json");

        assertTrue(outputStream.toString().contains("{\"key\": \"value\"}"));
    }

    @Test
    void testValuesCommandNotFound() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        GetCommand.ValuesCommand cmd = new GetCommand.ValuesCommand(getAction);
        new CommandLine(cmd).execute("missing");

        // error logged, no output
    }

    @Test
    void testValuesCommandWithError() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("fail"));

        GetCommand.ValuesCommand cmd = new GetCommand.ValuesCommand(getAction);
        new CommandLine(cmd).execute("my-release");
    }

    @Test
    void testManifestCommand() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getManifest(release)).thenReturn("---\nkind: Service\n");

        GetCommand.ManifestCommand cmd = new GetCommand.ManifestCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("kind: Service"));
    }

    @Test
    void testManifestCommandNotFound() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        GetCommand.ManifestCommand cmd = new GetCommand.ManifestCommand(getAction);
        new CommandLine(cmd).execute("missing");
    }

    @Test
    void testManifestCommandWithError() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("fail"));

        GetCommand.ManifestCommand cmd = new GetCommand.ManifestCommand(getAction);
        new CommandLine(cmd).execute("my-release");
    }

    @Test
    void testNotesCommand() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getNotes(release)).thenReturn("Thank you for installing.");

        GetCommand.NotesCommand cmd = new GetCommand.NotesCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("Thank you for installing."));
    }

    @Test
    void testNotesCommandEmpty() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getNotes(release)).thenReturn("");

        GetCommand.NotesCommand cmd = new GetCommand.NotesCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("No notes found"));
    }

    @Test
    void testNotesCommandNotFound() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        GetCommand.NotesCommand cmd = new GetCommand.NotesCommand(getAction);
        new CommandLine(cmd).execute("missing");
    }

    @Test
    void testNotesCommandWithError() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("fail"));

        GetCommand.NotesCommand cmd = new GetCommand.NotesCommand(getAction);
        new CommandLine(cmd).execute("my-release");
    }

    @Test
    void testHooksCommand() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getHooks(release)).thenReturn("kind: Job\nannotations:\n  helm.sh/hook: pre-install\n");

        GetCommand.HooksCommand cmd = new GetCommand.HooksCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("helm.sh/hook"));
    }

    @Test
    void testHooksCommandEmpty() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getHooks(release)).thenReturn("");

        GetCommand.HooksCommand cmd = new GetCommand.HooksCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("No hooks found"));
    }

    @Test
    void testHooksCommandNotFound() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        GetCommand.HooksCommand cmd = new GetCommand.HooksCommand(getAction);
        new CommandLine(cmd).execute("missing");
    }

    @Test
    void testHooksCommandWithError() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("fail"));

        GetCommand.HooksCommand cmd = new GetCommand.HooksCommand(getAction);
        new CommandLine(cmd).execute("my-release");
    }

    @Test
    void testMetadataCommand() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getMetadata(release)).thenReturn(Map.of("name", "my-release", "status", "deployed"));
        when(getAction.toYaml(any())).thenReturn("name: my-release\nstatus: deployed\n");

        GetCommand.MetadataCommand cmd = new GetCommand.MetadataCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        assertTrue(outputStream.toString().contains("name: my-release"));
    }

    @Test
    void testMetadataCommandJsonOutput() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getMetadata(release)).thenReturn(Map.of("name", "my-release"));
        when(getAction.toJson(any())).thenReturn("{\"name\":\"my-release\"}");

        GetCommand.MetadataCommand cmd = new GetCommand.MetadataCommand(getAction);
        new CommandLine(cmd).execute("my-release", "-o", "json");

        assertTrue(outputStream.toString().contains("{\"name\":\"my-release\"}"));
    }

    @Test
    void testMetadataCommandNotFound() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        GetCommand.MetadataCommand cmd = new GetCommand.MetadataCommand(getAction);
        new CommandLine(cmd).execute("missing");
    }

    @Test
    void testMetadataCommandWithError() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("fail"));

        GetCommand.MetadataCommand cmd = new GetCommand.MetadataCommand(getAction);
        new CommandLine(cmd).execute("my-release");
    }

    @Test
    void testAllCommand() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "default")).thenReturn(Optional.of(release));
        when(getAction.getAll(release, false)).thenReturn("MANIFEST:\n---\nVALUES:\n{}");

        GetCommand.AllCommand cmd = new GetCommand.AllCommand(getAction);
        new CommandLine(cmd).execute("my-release");

        String output = outputStream.toString();
        assertTrue(output.contains("MANIFEST:"));
        assertTrue(output.contains("VALUES:"));
    }

    @Test
    void testAllCommandNotFound() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        GetCommand.AllCommand cmd = new GetCommand.AllCommand(getAction);
        new CommandLine(cmd).execute("missing");
    }

    @Test
    void testAllCommandWithError() throws Exception {
        when(getAction.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("fail"));

        GetCommand.AllCommand cmd = new GetCommand.AllCommand(getAction);
        new CommandLine(cmd).execute("my-release");
    }

    @Test
    void testSubcommandWithRevision() throws Exception {
        Release release = createRelease();
        when(getAction.getReleaseByRevision("my-release", "default", 2)).thenReturn(Optional.of(release));
        when(getAction.getManifest(release)).thenReturn("---\nkind: Deployment\n");

        GetCommand.ManifestCommand cmd = new GetCommand.ManifestCommand(getAction);
        new CommandLine(cmd).execute("my-release", "--revision", "2");

        assertTrue(outputStream.toString().contains("kind: Deployment"));
    }

    @Test
    void testSubcommandWithNamespace() throws Exception {
        Release release = createRelease();
        when(getAction.getRelease("my-release", "prod")).thenReturn(Optional.of(release));
        when(getAction.getManifest(release)).thenReturn("---\nkind: Service\n");

        GetCommand.ManifestCommand cmd = new GetCommand.ManifestCommand(getAction);
        new CommandLine(cmd).execute("my-release", "-n", "prod");

        assertTrue(outputStream.toString().contains("kind: Service"));
    }

    private Release createRelease() {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .build();

        Chart chart = Chart.builder()
                .metadata(metadata)
                .build();

        Release.ReleaseInfo info = Release.ReleaseInfo.builder()
                .status("deployed")
                .lastDeployed(OffsetDateTime.now())
                .build();

        return Release.builder()
                .name("my-release")
                .namespace("default")
                .version(1)
                .chart(chart)
                .info(info)
                .manifest("---\nkind: Service\n")
                .build();
    }

    private Release createReleaseWithConfig() {
        Release release = createRelease();
        release.setConfig(Release.MapConfig.builder()
                .values(Map.of("key", "value"))
                .build());
        return release;
    }
}
