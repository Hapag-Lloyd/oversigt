package com.hlag.oversigt.util.text;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.larssh.json.dom.JsonDomDocument;
import de.larssh.json.dom.values.JacksonDomValue;
import de.larssh.utils.dom.XPathExpressions;
import de.larssh.utils.function.ThrowingFunction;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class XPathFunction implements ThrowingFunction<String, String> {
	@SuppressFBWarnings(value = "XFB_XML_FACTORY_BYPASS", justification = "There is no factory for JsonDomDocument.")
	private static Optional<JsonDomDocument<JsonNode>> createJsonDocument(final String probablyJson) {
		final JsonNode jsonNode;
		try {
			jsonNode = new ObjectMapper().readTree(probablyJson);
		} catch (@SuppressWarnings("unused") final IOException e) {
			return Optional.empty();
		}
		return Optional.of(new JsonDomDocument<>(new JacksonDomValue(jsonNode)));
	}

	private static Optional<Document> createXmlDocument(final String probablyXml) {
		try {
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			return Optional.of(
					documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(probablyXml))));
		} catch (@SuppressWarnings("unused") final SAXException | IOException | ParserConfigurationException e) {
			return Optional.empty();
		}
	}

	private Optional<? extends Document> document = Optional.empty();

	private boolean documentHasBeenExtracted = false;

	private final String probablyJsonOrXml;

	XPathFunction(final String probablyJsonOrXml) {
		this.probablyJsonOrXml = probablyJsonOrXml;
	}

	XPathFunction(final Optional<Document> document) {
		this.document = document;
		documentHasBeenExtracted = true;
		probablyJsonOrXml = "";
	}

	@Nullable
	@Override
	@SuppressFBWarnings(value = "XPATH_INJECTION", justification = "xPath injection by design")
	public String applyThrowing(@Nullable final String xPathString) throws XPathExpressionException {
		Objects.requireNonNull(xPathString, "Input for a XPath must be non null");

		if (!documentHasBeenExtracted) {
			document = createJsonDocument(probablyJsonOrXml);
			if (!document.isPresent()) {
				document = createXmlDocument(probablyJsonOrXml);
			}
			documentHasBeenExtracted = true;
		}

		if (!document.isPresent()) {
			return null;
		}

		return XPathExpressions.getString(document.get(), XPathFactory.newInstance().newXPath().compile(xPathString));
	}
}
