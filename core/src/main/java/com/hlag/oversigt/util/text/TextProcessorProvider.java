package com.hlag.oversigt.util.text;

import javax.xml.datatype.DatatypeFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jayway.jsonpath.Configuration;

@Singleton
public class TextProcessorProvider {
	@Inject
	private Configuration jsonPathConfiguration;

	@Inject
	private DatatypeFactory dataTypeFactory;

	public TextProcessorProvider() {
		// nothing to do
	}

	public TextProcessor createTextProcessor() {
		return new TextProcessor(jsonPathConfiguration, dataTypeFactory);
	}
}
