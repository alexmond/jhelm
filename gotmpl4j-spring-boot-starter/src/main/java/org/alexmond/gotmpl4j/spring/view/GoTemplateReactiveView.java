package org.alexmond.gotmpl4j.spring.view;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.alexmond.gotmpl4j.spring.GoTemplateService;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.result.view.AbstractUrlBasedView;
import org.springframework.web.server.ServerWebExchange;

/**
 * Spring WebFlux {@link org.springframework.web.reactive.result.view.View View} that
 * renders a gotmpl4j template by name. Like the servlet {@link GoTemplateView}, the
 * view's URL is the template name, so rendering delegates to
 * {@link GoTemplateService#render(String, Object)} and writes the result to the response.
 */
public class GoTemplateReactiveView extends AbstractUrlBasedView {

	private GoTemplateService service;

	private Charset charset = StandardCharsets.UTF_8;

	public void setService(GoTemplateService service) {
		this.service = service;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	@Override
	public boolean checkResourceExists(Locale locale) {
		return true;
	}

	@Override
	protected Mono<Void> renderInternal(Map<String, Object> model, MediaType contentType, ServerWebExchange exchange) {
		String body;
		try {
			body = this.service.render(getUrl(), model);
		}
		catch (RuntimeException ex) {
			return Mono.error(ex);
		}
		Charset cs = (contentType != null && contentType.getCharset() != null) ? contentType.getCharset()
				: this.charset;
		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(cs));
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

}
