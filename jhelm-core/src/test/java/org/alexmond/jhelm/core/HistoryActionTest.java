package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class HistoryActionTest {

    @Mock
    private KubeService kubeService;

    private HistoryAction historyAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        historyAction = new HistoryAction(kubeService);
    }

    @Test
    void testHistoryReturnsReleases() throws Exception {
        Release v1 = Release.builder().name("myapp").version(1).build();
        Release v2 = Release.builder().name("myapp").version(2).build();

        when(kubeService.getReleaseHistory(anyString(), anyString()))
                .thenReturn(Arrays.asList(v1, v2));

        List<Release> result = historyAction.history("myapp", "default");

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getVersion());
        assertEquals(2, result.get(1).getVersion());
    }
}
