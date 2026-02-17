package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UninstallActionTest {

    @Mock
    private KubeService kubeService;

    private UninstallAction uninstallAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uninstallAction = new UninstallAction(kubeService);
    }

    @Test
    void testUninstallSuccess() throws Exception {
        Release release = Release.builder()
                .name("myapp")
                .namespace("default")
                .manifest("---\nkind: Service\n")
                .build();

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(release));
        doNothing().when(kubeService).delete(anyString(), anyString());
        doNothing().when(kubeService).deleteReleaseHistory(anyString(), anyString());

        uninstallAction.uninstall("myapp", "default");

        verify(kubeService).delete("default", "---\nkind: Service\n");
        verify(kubeService).deleteReleaseHistory("myapp", "default");
    }

    @Test
    void testUninstallThrowsWhenReleaseNotFound() throws Exception {
        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            uninstallAction.uninstall("non-existent", "default");
        });

        assertTrue(exception.getMessage().contains("Release not found"));
    }
}
