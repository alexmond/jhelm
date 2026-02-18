package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ListActionTest {

    @Mock
    private KubeService kubeService;

    private ListAction listAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listAction = new ListAction(kubeService);
    }

    @Test
    void testListReturnsReleases() throws Exception {
        Release release1 = Release.builder().name("release1").build();
        Release release2 = Release.builder().name("release2").build();

        when(kubeService.listReleases(anyString())).thenReturn(Arrays.asList(release1, release2));

        List<Release> result = listAction.list("default");

        assertEquals(2, result.size());
        assertEquals("release1", result.get(0).getName());
        assertEquals("release2", result.get(1).getName());
    }

    @Test
    void testListReturnsEmptyList() throws Exception {
        when(kubeService.listReleases(anyString())).thenReturn(Collections.emptyList());

        List<Release> result = listAction.list("default");

        assertTrue(result.isEmpty());
    }
}
