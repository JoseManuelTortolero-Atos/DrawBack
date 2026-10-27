package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DrawControllerTest {

	@Test
	void streamsZipWithExtendedTimeoutAndPreservesDownloadContract() throws Exception {
		var service = mock(DrawBatchService.class);
		when(service.zipName("batch")).thenReturn("Sorteo_D1_2027-28_1000_candidatos.zip");
		byte[] bytes = {80, 75, 3, 4};
		doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(1);
			out.write(bytes);
			return null;
		}).when(service).writeZip(eq("batch"), any());
		var mvc = MockMvcBuilders.standaloneSetup(new DrawController(service)).build();
		var pending = mvc.perform(get("/api/setup/draw-batches/batch/zip"))
				.andExpect(request().asyncStarted()).andReturn();
		assertEquals(1_800_000L, pending.getRequest().getAsyncContext().getTimeout());
		pending.getAsyncResult(5000);
		var response = mvc.perform(asyncDispatch(pending))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/zip"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"Sorteo_D1_2027-28_1000_candidatos.zip\""))
				.andReturn().getResponse();
		assertArrayEquals(bytes, response.getContentAsByteArray());
	}
}
