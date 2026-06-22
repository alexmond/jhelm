package org.alexmond.gotmpl4j.spring.view;

import java.nio.charset.Charset;

import org.alexmond.gotmpl4j.spring.GoTemplateService;

import org.springframework.web.reactive.result.view.AbstractUrlBasedView;
import org.springframework.web.reactive.result.view.UrlBasedViewResolver;

/**
 * Spring WebFlux {@link org.springframework.web.reactive.result.view.ViewResolver
 * ViewResolver} that resolves a view name to a {@link GoTemplateReactiveView}. As with
 * the servlet resolver the view name is the template name, so the empty prefix/suffix
 * defaults are kept; {@link #getViewClass()} is overridden so no overridable setter is
 * called from the constructor.
 */
public class GoTemplateReactiveViewResolver extends UrlBasedViewResolver {

	private final GoTemplateService service;

	private Charset charset;

	public GoTemplateReactiveViewResolver(GoTemplateService service) {
		this.service = service;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	@Override
	protected Class<?> getViewClass() {
		return GoTemplateReactiveView.class;
	}

	@Override
	protected AbstractUrlBasedView createView(String viewName) {
		AbstractUrlBasedView view = super.createView(viewName);
		if (view instanceof GoTemplateReactiveView goView) {
			goView.setService(this.service);
			if (this.charset != null) {
				goView.setCharset(this.charset);
			}
		}
		return view;
	}

}
