/*
Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.eclipse.lsp4xml.extensions.synapse.contentmodel.participants.diagnostics;

import org.apache.xerces.parsers.SAXParser;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.extensions.contentmodel.participants.diagnostics.LSPErrorReporterForXML;
import org.eclipse.lsp4xml.extensions.contentmodel.settings.ContentModelSettings;
import org.eclipse.lsp4xml.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lsp4xml.extensions.synapse.contentmodel.utils.SynapseDiagnosticException;
import org.eclipse.lsp4xml.extensions.synapse.utils.Constants;
import org.eclipse.lsp4xml.services.extensions.diagnostics.LSPContentHandler;
import org.eclipse.lsp4xml.uriresolver.IExternalSchemaLocationProvider;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * XML validator utilities class.
 */
class SynapseXMLValidator {

    private SynapseXMLValidator() {
    }

    static void validate(DOMDocument document, XMLEntityResolver entityResolver, List<Diagnostic> diagnostics,
                         ContentModelSettings contentModelSettings, CancelChecker monitor)
            throws SynapseDiagnosticException {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        javax.xml.parsers.SAXParser parser;

        try {
            parser = factory.newSAXParser();

            parser.setProperty("http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                               "http://www.w3.org/2001/XMLSchema");
            parser.setProperty("http://java.sun.com/xml/jaxp/properties/schemaSource",
                               Constants.SCHEMA_LOCATION);

            parser.setProperty("http://apache.org/xml/properties/internal/error-reporter",
                               new LSPErrorReporterForXML(document, diagnostics));

            XMLReader reader = parser.getXMLReader();

            reader.setFeature("http://apache.org/xml/features/continue-after-fatal-error", false); //$NON-NLS-1$
            reader.setFeature("http://xml.org/sax/features/namespace-prefixes",
                              true /* document.hasNamespaces() */); //$NON-NLS-1$
            reader.setFeature("http://xml.org/sax/features/namespaces",
                              true /* document.hasNamespaces() */); //$NON-NLS-1$

            reader.setContentHandler(new LSPContentHandler(monitor));

            if (entityResolver != null) {
                reader.setProperty("http://apache.org/xml/properties/internal/entity-resolver",
                                   entityResolver); //$NON-NLS-1$
            }

            // If diagnostics for Schema preference is enabled
            XMLValidationSettings validationSettings = contentModelSettings != null
                    ? contentModelSettings.getValidation() : null;
            if ((validationSettings == null) || validationSettings.isSchema()) {
                checkExternalSchema(document.getExternalSchemaLocation(), (SAXParser) reader);
            }

            String content = document.getText();
            String uri = document.getDocumentURI();
            InputSource inputSource = new InputSource();
            inputSource.setByteStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            inputSource.setSystemId(uri);

            reader.parse(inputSource);

            List<Diagnostic> newDiagnostic = new ArrayList<>();
            int initialNoOfDiagnostics = diagnostics.size();
            for (int i = 0; i < initialNoOfDiagnostics; i++) {
                Diagnostic diagnostic = diagnostics.get(i);
                String errorCode = diagnostic.getCode();
                if (errorCode.equals("sch-props-correct.2") || errorCode.equals("cos-all-limited.1.2") ||
                        errorCode.equals("src-resolve") || errorCode.equals("src-resolve.4.2")) {
                    newDiagnostic.add(diagnostic);
                }
            }
            diagnostics.removeAll(newDiagnostic);
        } catch (IOException e) {
            throw new SynapseDiagnosticException("Synapse Schema validation failed", e);
        } catch (ParserConfigurationException | SAXException e) {
            // ..
        }
    }

    private static void checkExternalSchema(Map<String, String> result, SAXParser reader)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (result == null) {
            return;
        }
        String noNamespaceSchemaLocation = result.get(IExternalSchemaLocationProvider.NO_NAMESPACE_SCHEMA_LOCATION);
        if (noNamespaceSchemaLocation != null) {
            reader.setProperty(IExternalSchemaLocationProvider.NO_NAMESPACE_SCHEMA_LOCATION,
                               noNamespaceSchemaLocation);
        }
    }
}
