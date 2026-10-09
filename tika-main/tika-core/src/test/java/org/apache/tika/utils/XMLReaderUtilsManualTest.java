/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.utils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.parser.ParseContext;

public class XMLReaderUtilsManualTest {

    @Test
    public void getAttrValueReturnsValueOfFirstAttribute() {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "id", "id", "CDATA", "abc");
        assertEquals("abc", XMLReaderUtils.getAttrValue("id", atts));
    }

    @Test
    public void getAttrValueFindsAttributeAfterOthers() {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "a", "a", "CDATA", "1");
        atts.addAttribute("", "b", "b", "CDATA", "2");
        atts.addAttribute("", "c", "c", "CDATA", "3");
        assertEquals("3", XMLReaderUtils.getAttrValue("c", atts));
        assertEquals("2", XMLReaderUtils.getAttrValue("b", atts));
    }

    @Test
    public void getAttrValueReturnsNullWhenAttributeAbsent() {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "id", "id", "CDATA", "abc");
        assertNull(XMLReaderUtils.getAttrValue("other", atts));
    }

    @Test
    public void getAttrValueReturnsNullOnEmptyAttributes() {
        assertNull(XMLReaderUtils.getAttrValue("id", new AttributesImpl()));
    }

    @Test
    public void getAttrValueMatchesLocalNameNotQualifiedName() {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("urn:x", "name", "x:name", "CDATA", "v");
        assertEquals("v", XMLReaderUtils.getAttrValue("name", atts));
        assertNull(XMLReaderUtils.getAttrValue("x:name", atts));
    }


    @Test
    public void getTransformerFactoryIsSecured() throws Exception {
        TransformerFactory factory = XMLReaderUtils.getTransformerFactory();
        assertNotNull(factory);
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertEquals("", factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_DTD));
        assertEquals("", factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET));
    }

    private static class FakeFactory extends TransformerFactory {
        Throwable toThrow;
        String lastName;
        Object lastValue;

        @Override
        public void setAttribute(String name, Object value) {
            if (toThrow instanceof RuntimeException) {
                throw (RuntimeException) toThrow;
            }
            if (toThrow instanceof Error) {
                throw (Error) toThrow;
            }
            lastName = name;
            lastValue = value;
        }

        @Override public Object getAttribute(String name) { return null; }
        @Override public Transformer newTransformer(Source s)
            throws TransformerConfigurationException { return null; }
        @Override public Transformer newTransformer()
            throws TransformerConfigurationException { return null; }
        @Override public Templates newTemplates(Source s)
            throws TransformerConfigurationException { return null; }
        @Override public Source getAssociatedStylesheet(Source s, String m, String t, String c)
            throws TransformerConfigurationException { return null; }
        @Override public void setURIResolver(URIResolver r) { }
        @Override public URIResolver getURIResolver() { return null; }
        @Override public void setFeature(String n, boolean v) { }
        @Override public boolean getFeature(String n) { return false; }
        @Override public void setErrorListener(ErrorListener l) { }
        @Override public ErrorListener getErrorListener() { return null; }
    }

    private static void callTry(TransformerFactory f, String attr, String value)
        throws Exception {
        Method m = XMLReaderUtils.class.getDeclaredMethod("trySetTransformerAttribute",
            TransformerFactory.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(null, f, attr, value);
    }

    @Test
    public void trySetPassesAttributeAndValueThrough() throws Exception {
        FakeFactory f = new FakeFactory();
        callTry(f, "attr", "val");
        assertEquals("attr", f.lastName);
        assertEquals("val", f.lastValue);
    }

    @Test
    public void trySetRethrowsSecurityException() {
        FakeFactory f = new FakeFactory();
        f.toThrow = new SecurityException("blocked");
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
            () -> callTry(f, "attr", "val"));
        assertTrue(e.getCause() instanceof SecurityException);
    }

    @Test
    public void trySetSwallowsOtherExceptions() throws Exception {
        FakeFactory f = new FakeFactory();
        f.toThrow = new IllegalArgumentException("unsupported");
        callTry(f, "attr", "val");
    }

    @Test
    public void trySetSwallowsAbstractMethodError() throws Exception {
        FakeFactory f = new FakeFactory();
        f.toThrow = new AbstractMethodError("old parser");
        callTry(f, "attr", "val");
    }
    @Test
    public void getDocumentBuilderReturnsBuilderFromContext() throws Exception {
        DocumentBuilder custom = XMLReaderUtils.getDocumentBuilder();
        ParseContext ctx = new ParseContext();
        ctx.set(DocumentBuilder.class, custom);
        assertSame(custom, XMLReaderUtils.getDocumentBuilder(ctx));
    }

    @Test
    public void getDocumentBuilderCreatesOneWhenContextIsEmpty() throws Exception {
        DocumentBuilder b = XMLReaderUtils.getDocumentBuilder(new ParseContext());
        assertNotNull(b);
    }

    private static void callClear(XMLReader reader) throws Exception {
        Method m = XMLReaderUtils.class.getDeclaredMethod("clearReader", XMLReader.class);
        m.setAccessible(true);
        m.invoke(null, reader);
    }

    @Test
    public void clearReaderReplacesAllFourHandlers() throws Exception {
        XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        DefaultHandler mine = new DefaultHandler();
        reader.setContentHandler(mine);
        reader.setDTDHandler(mine);
        reader.setEntityResolver(mine);
        reader.setErrorHandler(mine);
        callClear(reader);
        assertNotNull(reader.getContentHandler());
        assertNotSame(mine, reader.getContentHandler());
        assertNotNull(reader.getDTDHandler());
        assertNotSame(mine, reader.getDTDHandler());
        assertNotNull(reader.getEntityResolver());
        assertNotSame(mine, reader.getEntityResolver());
        assertNotNull(reader.getErrorHandler());
        assertNotSame(mine, reader.getErrorHandler());
    }

    @Test
    public void clearReaderAcceptsNull() throws Exception {
        callClear(null);
    }

    private static class FakeSAXParser extends SAXParser {
        boolean resetSupported;
        boolean propertySupported;
        final Map<String, Object> props = new HashMap<>();
        final SAXParser real;

        FakeSAXParser() throws Exception {
            real = SAXParserFactory.newInstance().newSAXParser();
        }

        @Override public void reset() {
            if (!resetSupported) {
                throw new UnsupportedOperationException();
            }
        }
        @Override public org.xml.sax.Parser getParser() throws org.xml.sax.SAXException {
            return real.getParser();
        }
        int readerRequests;
        @Override public XMLReader getXMLReader() throws org.xml.sax.SAXException {
            readerRequests++;
            return real.getXMLReader();
        }
        @Override public boolean isNamespaceAware() { return false; }
        @Override public boolean isValidating() { return false; }
        @Override public void setProperty(String name, Object value)
            throws SAXNotRecognizedException {
            if (!propertySupported) {
                throw new SAXNotRecognizedException(name);
            }
            props.put(name, value);
        }
        @Override public Object getProperty(String name) { return props.get(name); }
    }

    private static String poolParserKind(SAXParser p) throws Exception {
        Method m = XMLReaderUtils.class.getDeclaredMethod("buildPoolParser",
            int.class, SAXParser.class);
        m.setAccessible(true);
        return m.invoke(null, 1, p).getClass().getSimpleName();
    }

    @Test
    public void jdkParserGivesBuiltInPoolParser() throws Exception {
        SAXParser real = SAXParserFactory.newInstance().newSAXParser();
        assertEquals("BuiltInPoolSAXParser", poolParserKind(real));
    }

    @Test
    public void parserWithoutResetIsUnrecognized() throws Exception {
        FakeSAXParser p = new FakeSAXParser();
        p.resetSupported = false;
        p.propertySupported = true;
        assertEquals("UnrecognizedPoolSAXParser", poolParserKind(p));
    }

    @Test
    public void parserWithoutEntityLimitPropertyIsUnrecognized() throws Exception {
        FakeSAXParser p = new FakeSAXParser();
        p.resetSupported = true;
        p.propertySupported = false;
        assertEquals("UnrecognizedPoolSAXParser", poolParserKind(p));
    }

    @Test
    public void entityExpansionLimitIsSetOnParser() throws Exception {
        FakeSAXParser p = new FakeSAXParser();
        p.resetSupported = true;
        p.propertySupported = true;
        poolParserKind(p);
        assertEquals(Integer.valueOf(XMLReaderUtils.getMaxEntityExpansions()),
            p.props.get("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit"));
    }

    @Test
    public void unsupportedLimitWarningIsThrottled() throws Exception {
        Field last = XMLReaderUtils.class.getDeclaredField("LAST_LOG");
        last.setAccessible(true);
        last.setLong(null, 0L);
        FakeSAXParser p = new FakeSAXParser();
        p.resetSupported = true;
        p.propertySupported = false;
        poolParserKind(p);
        long first = last.getLong(null);
        assertTrue(first > 0);
        Thread.sleep(5);
        poolParserKind(p);
        assertEquals(first, last.getLong(null));
    }

    @TempDir
    Path tmp;

    private static final String XML = "<a><b>x</b><b/></a>";

    private static InputStream xmlStream() {
        return new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8));
    }

    private static Object field(String name) throws Exception {
        Field f = XMLReaderUtils.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static int queueSize(String name) throws Exception {
        return ((Collection<?>) field(name)).size();
    }

    private static Object head(String name) throws Exception {
        return ((Queue<?>) field(name)).peek();
    }

    private static void assertXmlDom(Document d) {
        assertEquals("a", d.getDocumentElement().getTagName());
        assertEquals(2, d.getElementsByTagName("b").getLength());
    }

    private static class CountingHandler extends DefaultHandler {
        int elements;
        @Override
        public void startElement(String u, String l, String q, Attributes a) {
            elements++;
        }
    }

    private interface PoolTest {
        void run() throws Exception;
    }

    // remet toujours l'état statique du pool, partagé par tous les tests
    private static void withPool(int size, int maxReuses, PoolTest t) throws Exception {
        int oldSize = XMLReaderUtils.getPoolSize();
        int oldReuses = XMLReaderUtils.getMaxNumReuses();
        try {
            XMLReaderUtils.setMaxNumReuses(maxReuses);
            XMLReaderUtils.setPoolSize(size);
            t.run();
        } finally {
            XMLReaderUtils.setMaxNumReuses(oldReuses);
            XMLReaderUtils.setPoolSize(oldSize);
        }
    }

    private static class CountingBuilder extends DocumentBuilder {
        final DocumentBuilder real;
        int parses;
        CountingBuilder(DocumentBuilder real) { this.real = real; }
        @Override public Document parse(InputSource is)
            throws org.xml.sax.SAXException, IOException {
            parses++;
            return real.parse(is);
        }
        @Override public boolean isNamespaceAware() { return real.isNamespaceAware(); }
        @Override public boolean isValidating() { return real.isValidating(); }
        @Override public void setEntityResolver(EntityResolver er) { real.setEntityResolver(er); }
        @Override public void setErrorHandler(ErrorHandler eh) { real.setErrorHandler(eh); }
        @Override public Document newDocument() { return real.newDocument(); }
        @Override public DOMImplementation getDOMImplementation() {
            return real.getDOMImplementation();
        }
    }

// ---------- buildDOM : les quatre surcharges ----------

    @Test
    public void buildDOMFromStreamReturnsDocumentAndReleasesBuilder() throws Exception {
        withPool(2, 100, () -> {
            assertXmlDom(XMLReaderUtils.buildDOM(xmlStream()));
            assertEquals(2, queueSize("DOM_BUILDERS"));
        });
    }

    @Test
    public void buildDOMFromReaderReturnsDocumentAndReleasesBuilder() throws Exception {
        withPool(2, 100, () -> {
            assertXmlDom(XMLReaderUtils.buildDOM(new StringReader(XML), new ParseContext()));
            assertEquals(2, queueSize("DOM_BUILDERS"));
        });
    }

    @Test
    public void buildDOMFromPathAndUriReturnDocument() throws Exception {
        Path file = tmp.resolve("a.xml");
        Files.write(file, XML.getBytes(StandardCharsets.UTF_8));
        withPool(2, 100, () -> {
            assertXmlDom(XMLReaderUtils.buildDOM(file));
            assertEquals(2, queueSize("DOM_BUILDERS"));
            assertXmlDom(XMLReaderUtils.buildDOM(file.toUri().toString()));
            assertEquals(2, queueSize("DOM_BUILDERS"));
        });
    }

    @Test
    public void buildDOMFallsBackWhenPoolIsEmpty() throws Exception {
        withPool(2, 100, () -> {
            ((Collection<?>) field("DOM_BUILDERS")).clear();
            assertXmlDom(XMLReaderUtils.buildDOM(xmlStream()));
            assertEquals(0, queueSize("DOM_BUILDERS"));
        });
    }

    @Test
    public void buildDOMUsesBuilderFromContext() throws Exception {
        withPool(2, 100, () -> {
            CountingBuilder cb = new CountingBuilder(XMLReaderUtils.getDocumentBuilder());
            ParseContext ctx = new ParseContext();
            ctx.set(DocumentBuilder.class, cb);
            assertXmlDom(XMLReaderUtils.buildDOM(new StringReader(XML), ctx));
            assertEquals(1, cb.parses);
            assertEquals(2, queueSize("DOM_BUILDERS"));
        });
    }

// ---------- parseSAX : les deux surcharges ----------

    @Test
    public void parseSAXFromStreamDeliversEventsAndReleasesParser() throws Exception {
        withPool(2, 100, () -> {
            CountingHandler h = new CountingHandler();
            XMLReaderUtils.parseSAX(xmlStream(), h, new ParseContext());
            assertEquals(3, h.elements);
            assertEquals(2, queueSize("SAX_PARSERS"));
        });
    }

    @Test
    public void parseSAXFromReaderDeliversEventsAndReleasesParser() throws Exception {
        withPool(2, 100, () -> {
            CountingHandler h = new CountingHandler();
            XMLReaderUtils.parseSAX(new StringReader(XML), h, new ParseContext());
            assertEquals(3, h.elements);
            assertEquals(2, queueSize("SAX_PARSERS"));
        });
    }

    @Test
    public void parseSAXFallsBackWhenPoolIsEmpty() throws Exception {
        withPool(2, 100, () -> {
            ((Collection<?>) field("SAX_PARSERS")).clear();
            CountingHandler h = new CountingHandler();
            XMLReaderUtils.parseSAX(xmlStream(), h, new ParseContext());
            assertEquals(3, h.elements);
            assertEquals(0, queueSize("SAX_PARSERS"));
        });
    }

// ---------- réutilisation : incrementUses et >= MAX_NUM_REUSES ----------

    @Test
    public void saxParserIsRecycledThenReplacedAtMaxReuses() throws Exception {
        withPool(1, 2, () -> {
            Object before = head("SAX_PARSERS");
            XMLReaderUtils.parseSAX(xmlStream(), new DefaultHandler(), new ParseContext());
            assertSame(before, head("SAX_PARSERS"));
            XMLReaderUtils.parseSAX(xmlStream(), new DefaultHandler(), new ParseContext());
            assertNotSame(before, head("SAX_PARSERS"));
        });
    }


// ---------- setPoolSize et accesseurs ----------

    @Test
    public void setPoolSizeRebuildsBothPools() throws Exception {
        withPool(3, 100, () -> {
            assertEquals(3, XMLReaderUtils.getPoolSize());
            assertEquals(3, queueSize("SAX_PARSERS"));
            assertEquals(3, queueSize("DOM_BUILDERS"));
        });
    }

    @Test
    public void poolSizeZeroEmptiesPoolsAndStillParses() throws Exception {
        withPool(0, 100, () -> {
            assertEquals(0, XMLReaderUtils.getPoolSize());
            assertEquals(0, queueSize("SAX_PARSERS"));
            assertEquals(0, queueSize("DOM_BUILDERS"));
            assertXmlDom(XMLReaderUtils.buildDOM(xmlStream()));
            CountingHandler h = new CountingHandler();
            XMLReaderUtils.parseSAX(xmlStream(), h, new ParseContext());
            assertEquals(3, h.elements);
        });
    }

    @Test
    public void setPoolSizeRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> XMLReaderUtils.setPoolSize(-1));
    }

    @Test
    public void maxNumReusesRoundTrip() {
        int old = XMLReaderUtils.getMaxNumReuses();
        try {
            XMLReaderUtils.setMaxNumReuses(7);
            assertEquals(7, XMLReaderUtils.getMaxNumReuses());
        } finally {
            XMLReaderUtils.setMaxNumReuses(old);
        }
    }

// ---------- verrous (remplace le test de mon message précédent) ----------

    @Test
    public void locksAreReleasedAfterUse() throws Exception {
        withPool(2, 100, () -> assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            XMLReaderUtils.buildDOM(xmlStream());
            XMLReaderUtils.parseSAX(xmlStream(), new DefaultHandler(), new ParseContext());
            XMLReaderUtils.setPoolSize(2);
        }));
    }

    @Test
    public void parseSAXUsesParserFromContext() throws Exception {
        withPool(2, 100, () -> {
            FakeSAXParser fp = new FakeSAXParser();
            ParseContext ctx = new ParseContext();
            ctx.set(SAXParser.class, fp);
            CountingHandler h = new CountingHandler();
            XMLReaderUtils.parseSAX(xmlStream(), h, ctx);
            assertTrue(fp.readerRequests > 0);
            assertEquals(3, h.elements);
            assertEquals(2, queueSize("SAX_PARSERS"));
        });
    }

    private static final String ORACLE_LIMIT =
        "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit";

    private static void callPrivate(String name, Class<?> type, Object arg) throws Exception {
        Method m = XMLReaderUtils.class.getDeclaredMethod(name, type);
        m.setAccessible(true);
        m.invoke(null, arg);
    }

    private static class FakeDbf extends DocumentBuilderFactory {
        boolean unsupported;
        final Map<String, Object> attrs = new HashMap<>();

        @Override public DocumentBuilder newDocumentBuilder()
            throws ParserConfigurationException { throw new ParserConfigurationException(); }
        @Override public void setAttribute(String n, Object v) {
            if (unsupported) {
                throw new IllegalArgumentException(n);
            }
            attrs.put(n, v);
        }
        @Override public Object getAttribute(String n) { return attrs.get(n); }
        @Override public void setFeature(String n, boolean v) { }
        @Override public boolean getFeature(String n) { return false; }
    }

    // appelle deux fois : la première doit journaliser, la seconde être bloquée par la limite de 5 min
    private static void assertWarningThrottled(String method, Class<?> type, Object target)
        throws Exception {
        Field last = XMLReaderUtils.class.getDeclaredField("LAST_LOG");
        last.setAccessible(true);
        last.setLong(null, 0L);
        callPrivate(method, type, target);
        long first = last.getLong(null);
        assertTrue(first > 0);
        Thread.sleep(5);
        callPrivate(method, type, target);
        assertEquals(first, last.getLong(null));
    }

// ---------- DocumentBuilderFactory (lignes 851 et 856) ----------

    @Test
    public void dbfEntityExpansionLimitAttributeIsSet() throws Exception {
        FakeDbf f = new FakeDbf();
        callPrivate("trySetXercesSecurityManager", DocumentBuilderFactory.class, f);
        assertEquals(Integer.valueOf(XMLReaderUtils.getMaxEntityExpansions()),
            f.attrs.get(ORACLE_LIMIT));
    }

    @Test
    public void dbfUnsupportedLimitIsSwallowedAndLogThrottled() throws Exception {
        FakeDbf f = new FakeDbf();
        f.unsupported = true;
        assertWarningThrottled("trySetXercesSecurityManager", DocumentBuilderFactory.class, f);
    }

// ---------- SAXParser (lignes 896 et 900), avec le FakeSAXParser de la réponse précédente ----------

    @Test
    public void saxParserEntityExpansionLimitPropertyIsSet() throws Exception {
        FakeSAXParser p = new FakeSAXParser();
        p.propertySupported = true;
        callPrivate("trySetXercesSecurityManager", SAXParser.class, p);
        assertEquals(Integer.valueOf(XMLReaderUtils.getMaxEntityExpansions()),
            p.props.get(ORACLE_LIMIT));
    }

    @Test
    public void saxParserUnsupportedLimitIsSwallowedAndLogThrottled() throws Exception {
        FakeSAXParser p = new FakeSAXParser();
        p.propertySupported = false;
        assertWarningThrottled("trySetXercesSecurityManager", SAXParser.class, p);
    }

// ---------- StAX (lignes 911 à 917) ----------

    @Test
    public void staxFallsBackToWoodstoxPropertyWhenJdkOneIsRejected() throws Exception {
        XMLInputFactory f = mock(XMLInputFactory.class);
        doThrow(new IllegalArgumentException()).when(f).setProperty(eq(ORACLE_LIMIT), any());
        callPrivate("trySetStaxSecurityManager", XMLInputFactory.class, f);
        verify(f).setProperty("com.ctc.wstx.maxEntityCount",
            Integer.valueOf(XMLReaderUtils.getMaxEntityExpansions()));
    }

    @Test
    public void staxSwallowsFailureOfBothProperties() throws Exception {
        Field warned = XMLReaderUtils.class.getDeclaredField("HAS_WARNED_STAX");
        warned.setAccessible(true);
        AtomicBoolean flag = (AtomicBoolean) warned.get(null);
        boolean old = flag.get();
        try {
            flag.set(false);
            XMLInputFactory f = mock(XMLInputFactory.class);
            doThrow(new IllegalArgumentException()).when(f).setProperty(any(), any());
            callPrivate("trySetStaxSecurityManager", XMLInputFactory.class, f);
            assertTrue(flag.get());
        } finally {
            flag.set(old);
        }
    }



    private static Field findField(Class<?> c, String name) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // on remonte dans la hiérarchie
            }
        }
        throw new NoSuchFieldException(name);
    }



}
