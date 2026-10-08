package org.apache.tika.utils;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.tika.utils.XMLReaderUtils;
import org.mockito.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;

@ExtendWith(MockitoExtension.class)
public class XMLReaderUtils_getMaxEntityExpansions_33_0_Test {

    @Mock
    private XMLReaderUtils xmlReaderUtils;

    @BeforeEach
    public void setUp() {
        xmlReaderUtils = new XMLReaderUtils();
    }

    @Test
    public void testGetMaxEntityExpansions() throws Exception {
        Method method = xmlReaderUtils.getClass().getMethod("getMaxEntityExpansions");
        int maxEntityExpansions = (int) method.invoke(xmlReaderUtils);
        assertTrue(maxEntityExpansions > 0);
        assertEquals(XMLReaderUtils.DEFAULT_MAX_ENTITY_EXPANSIONS, maxEntityExpansions);
    }
}
