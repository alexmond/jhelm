package org.alexmond.gotmpl4j.spring.view;

import org.alexmond.gotmpl4j.spring.GoTemplateService;

import org.springframework.web.servlet.view.AbstractTemplateViewResolver;
import org.springframework.web.servlet.view.AbstractUrlBasedView;

/**
 * Spring MVC {@link org.springframework.web.servlet.ViewResolver ViewResolver} that
 * resolves a view name to a {@link GoTemplateView}. The view name is used as the template
 * name directly (the loader already applies the prefix/suffix), so this resolver keeps
 * {@code UrlBasedViewResolver}'s empty prefix/suffix defaults.
 */
public class GoTemplateViewResolver extends AbstractTemplateViewResolver {

	private final GoTemplateService service;

	public GoTemplateViewResolver(GoTemplateService service) {
		this.service = service;
	}

	@Override
	protected Class<?> getViewClass() {
		return GoTemplateView.class;
	}

	@Override
	protected AbstractUrlBasedView buildView(String viewName) throws Exception {
		GoTemplateView view = (GoTemplateView) super.buildView(viewName);
		view.setService(this.service);
		return view;
	}

}
