package org.alexmond.gotmpl4j.spring.view;

import java.io.Writer;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.alexmond.gotmpl4j.spring.GoTemplateService;

import org.springframework.web.servlet.view.AbstractTemplateView;

/**
 * Spring MVC {@link org.springframework.web.servlet.View View} that renders a gotmpl4j
 * template by name. The view's URL is the template name (the loader already keys
 * templates by their suffix-stripped relative path), so rendering delegates straight to
 * {@link GoTemplateService#render(String, Object)} with the merged model.
 */
public class GoTemplateView extends AbstractTemplateView {

	private GoTemplateService service;

	/**
	 * Set the rendering service. Injected by {@link GoTemplateViewResolver} when it
	 * builds the view.
	 * @param service the gotmpl4j service
	 */
	public void setService(GoTemplateService service) {
		this.service = service;
	}

	@Override
	protected void renderMergedTemplateModel(Map<String, Object> model, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String body = this.service.render(getUrl(), model);
		response.setContentType(getContentType());
		try (Writer writer = response.getWriter()) {
			writer.write(body);
		}
	}

}
