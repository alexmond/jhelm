package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StatusActionTest {

    @Mock
    private KubeService kubeService;

    private StatusAction statusAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        statusAction = new StatusAction(kubeService);
    }

    @Test
    void testStatusReturnsRelease() throws Exception {
        Release mockRelease = Release.builder()
                .name("test-release")
                .namespace("default")
                .version(1)
                .build();

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(mockRelease));

        Optional<Release> result = statusAction.status("test-release", "default");

        assertTrue(result.isPresent());
        assertEquals("test-release", result.get().getName());
    }

    @Test
    void testStatusReturnsEmpty() throws Exception {
        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        Optional<Release> result = statusAction.status("non-existent", "default");

        assertFalse(result.isPresent());
    }
}
