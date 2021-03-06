/*
 * Copyright (c) 2007 Henri Sivonen
 * Copyright (c) 2007-2017 Mozilla Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.htmlparser.xom;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import nu.validator.htmlparser.common.CharacterHandler;
import nu.validator.htmlparser.common.DocumentModeHandler;
import nu.validator.htmlparser.common.Heuristics;
import nu.validator.htmlparser.common.TokenHandler;
import nu.validator.htmlparser.common.TransitionHandler;
import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.impl.ErrorReportingTokenizer;
import nu.validator.htmlparser.impl.Tokenizer;
import nu.validator.htmlparser.io.Driver;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * This class implements an HTML5 parser that exposes data through the XOM 
 * interface. 
 * 
 * <p>By default, when using the constructor without arguments, the 
 * this parser coerces XML 1.0-incompatible infosets into XML 1.0-compatible
 * infosets. This corresponds to <code>ALTER_INFOSET</code> as the general 
 * XML violation policy. It is possible to treat XML 1.0 infoset violations 
 * as fatal by setting the general XML violation policy to <code>FATAL</code>. 
 * 
 * <p>The doctype is not represented in the tree.
 * 
 * <p>The document mode is represented via the <code>Mode</code> 
 * interface on the <code>Document</code> node if the node implements 
 * that interface (depends on the used node factory).
 * 
 * <p>The form pointer is stored if the node factory supports storing it.
 * 
 * <p>This package has its own node factory class because the official 
 * XOM node factory may return multiple nodes instead of one confusing 
 * the assumptions of the DOM-oriented HTML5 parsing algorithm.
 * 
 * @version $Id$
 * @author hsivonen
 */
public class HtmlBuilder extends Builder {

    private Driver driver;

    private final XOMTreeBuilder treeBuilder;

    private final SimpleNodeFactory simpleNodeFactory;

    private EntityResolver entityResolver;

    private ErrorHandler errorHandler = null;

    private DocumentModeHandler documentModeHandler = null;

    private boolean checkingNormalization = false;

    private boolean scriptingEnabled = false;

    private final List<CharacterHandler> characterHandlers = new LinkedList<CharacterHandler>();
    
    private XmlViolationPolicy contentSpacePolicy = XmlViolationPolicy.FATAL;

    private XmlViolationPolicy contentNonXmlCharPolicy = XmlViolationPolicy.FATAL;

    private XmlViolationPolicy commentPolicy = XmlViolationPolicy.FATAL;

    private XmlViolationPolicy namePolicy = XmlViolationPolicy.FATAL;

    private XmlViolationPolicy streamabilityViolationPolicy = XmlViolationPolicy.ALLOW;
    
    private boolean mappingLangToXmlLang = false;

    private XmlViolationPolicy xmlnsPolicy = XmlViolationPolicy.FATAL;
    
    private boolean reportingDoctype = true;

    private ErrorHandler treeBuilderErrorHandler = null;

    private Heuristics heuristics = Heuristics.NONE;

    private TransitionHandler transitionHandler = null;
    
    /**
     * Constructor with default node factory and fatal XML violation policy.
     */
    public HtmlBuilder() {
        this(new SimpleNodeFactory(), XmlViolationPolicy.FATAL);
    }
    
    /**
     * Constructor with given node factory and fatal XML violation policy.
     * @param nodeFactory the factory
     */
    public HtmlBuilder(SimpleNodeFactory nodeFactory) {
        this(nodeFactory, XmlViolationPolicy.FATAL);
    }

    /**
     * Constructor with default node factory and given XML violation policy.
     * @param xmlPolicy the policy
     */
    public HtmlBuilder(XmlViolationPolicy xmlPolicy) {
        this(new SimpleNodeFactory(), xmlPolicy);
    }
    
    /**
     * Constructor with given node factory and given XML violation policy.
     * @param nodeFactory the factory
     * @param xmlPolicy the policy
     */
    public HtmlBuilder(SimpleNodeFactory nodeFactory, XmlViolationPolicy xmlPolicy) {
        super();
        this.simpleNodeFactory = nodeFactory;
        this.treeBuilder = new XOMTreeBuilder(nodeFactory);
        this.driver = null;
        setXmlPolicy(xmlPolicy);
    }

    private Tokenizer newTokenizer(TokenHandler handler, boolean newAttributesEachTime) {
        if (errorHandler == null && transitionHandler == null
                && contentNonXmlCharPolicy == XmlViolationPolicy.ALLOW) {
            return new Tokenizer(handler, newAttributesEachTime);
        } else {
            return new ErrorReportingTokenizer(handler, newAttributesEachTime);
        }
   }
    
    /**
     * This class wraps different tree builders depending on configuration. This 
     * method does the work of hiding this from the user of the class.
     */
    private void lazyInit() {
        if (driver == null) {
            this.driver = new Driver(newTokenizer(treeBuilder, false));
            this.driver.setErrorHandler(errorHandler);
            this.driver.setTransitionHandler(transitionHandler);
            this.treeBuilder.setErrorHandler(treeBuilderErrorHandler);
            this.driver.setCheckingNormalization(checkingNormalization);
            this.driver.setCommentPolicy(commentPolicy);
            this.driver.setContentNonXmlCharPolicy(contentNonXmlCharPolicy);
            this.driver.setContentSpacePolicy(contentSpacePolicy);
            this.driver.setMappingLangToXmlLang(mappingLangToXmlLang);
            this.driver.setXmlnsPolicy(xmlnsPolicy);
            this.driver.setHeuristics(heuristics);
            for (CharacterHandler characterHandler : characterHandlers) {
                this.driver.addCharacterHandler(characterHandler);
            }
            this.treeBuilder.setDocumentModeHandler(documentModeHandler);
            this.treeBuilder.setScriptingEnabled(scriptingEnabled);
            this.treeBuilder.setReportingDoctype(reportingDoctype);
            this.treeBuilder.setNamePolicy(namePolicy);
        }
    }

    
    private void tokenize(InputSource is) throws ParsingException, IOException,
            MalformedURLException {
        try {
            if (is == null) {
                throw new IllegalArgumentException("Null input.");
            }
            if (is.getByteStream() == null && is.getCharacterStream() == null) {
                String systemId = is.getSystemId();
                if (systemId == null) {
                    throw new IllegalArgumentException(
                            "No byte stream, no character stream nor URI.");
                }
                if (entityResolver != null) {
                    is = entityResolver.resolveEntity(is.getPublicId(),
                            systemId);
                }
                if (is.getByteStream() == null
                        || is.getCharacterStream() == null) {
                    is = new InputSource();
                    is.setSystemId(systemId);
                    is.setByteStream(new URL(systemId).openStream());
                }
            }
            driver.tokenize(is);
        } catch (SAXParseException e) {
            throw new ParsingException(e.getMessage(), e.getSystemId(), e.getLineNumber(),
                    e.getColumnNumber(), e);
        } catch (SAXException e) {
            throw new ParsingException(e.getMessage(), e);
        }
    }

    /**
     * Parse from SAX <code>InputSource</code>.
     * @param is the <code>InputSource</code>
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     */
    public Document build(InputSource is) throws ParsingException, IOException {
        lazyInit();
        treeBuilder.setFragmentContext(null);
        tokenize(is);
        return treeBuilder.getDocument();
    }

    /**
     * Parse a fragment from SAX <code>InputSource</code> assuming an HTML
     * context.
     * @param is the <code>InputSource</code>
     * @param context the name of the context element (HTML namespace assumed)
     * @return the fragment
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     */
    public Nodes buildFragment(InputSource is, String context)
            throws IOException, ParsingException {
        lazyInit();
        treeBuilder.setFragmentContext(context.intern());
        tokenize(is);
        return treeBuilder.getDocumentFragment();
    }

    /**
     * Parse a fragment from SAX <code>InputSource</code>.
     * @param is the <code>InputSource</code>
     * @param contextLocal the local name of the context element
     * @parem contextNamespace the namespace of the context element
     * @return the fragment
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     */
    public Nodes buildFragment(InputSource is, String contextLocal, String contextNamespace)
            throws IOException, ParsingException {
        lazyInit();
        treeBuilder.setFragmentContext(contextLocal.intern(), contextNamespace.intern(), null, false);
        tokenize(is);
        return treeBuilder.getDocumentFragment();
    }
    
    /**
     * Parse from <code>File</code>.
     * @param file the file
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.io.File)
     */
    @Override
    public Document build(File file) throws ParsingException,
            ValidityException, IOException {
        return build(new FileInputStream(file), file.toURI().toASCIIString());
    }

    /**
     * Parse from <code>InputStream</code>.
     * @param stream the stream
     * @param uri the base URI
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.io.InputStream, java.lang.String)
     */
    @Override
    public Document build(InputStream stream, String uri)
            throws ParsingException, ValidityException, IOException {
        InputSource is = new InputSource(stream);
        is.setSystemId(uri);
        return build(is);
    }

    /**
     * Parse from <code>InputStream</code>.
     * @param stream the stream
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.io.InputStream)
     */
    @Override
    public Document build(InputStream stream) throws ParsingException,
            ValidityException, IOException {
        return build(new InputSource(stream));
    }

    /**
     * Parse from <code>Reader</code>.
     * @param stream the reader
     * @param uri the base URI
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.io.Reader, java.lang.String)
     */
    @Override
    public Document build(Reader stream, String uri) throws ParsingException,
            ValidityException, IOException {
        InputSource is = new InputSource(stream);
        is.setSystemId(uri);
        return build(is);
    }

    /**
     * Parse from <code>Reader</code>.
     * @param stream the reader
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.io.Reader)
     */
    @Override
    public Document build(Reader stream) throws ParsingException,
            ValidityException, IOException {
        return build(new InputSource(stream));
    }

    /**
     * Parse from <code>String</code>.
     * @param content the HTML source as string
     * @param uri the base URI
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.lang.String, java.lang.String)
     */
    @Override
    public Document build(String content, String uri) throws ParsingException,
            ValidityException, IOException {
        return build(new StringReader(content), uri);
    }

    /**
     * Parse from URI.
     * @param uri the URI of the document
     * @return the document
     * @throws ParsingException in case of an XML violation
     * @throws IOException if IO goes wrang
     * @see nu.xom.Builder#build(java.lang.String)
     */
    @Override
    public Document build(String uri) throws ParsingException,
            ValidityException, IOException {
        return build(new InputSource(uri));
    }

    /**
     * Gets the node factory
     */
    public SimpleNodeFactory getSimpleNodeFactory() {
        return simpleNodeFactory;
    }

    /**
     * @see org.xml.sax.XMLReader#setEntityResolver(org.xml.sax.EntityResolver)
     */
    public void setEntityResolver(EntityResolver resolver) {
        entityResolver = resolver;
    }

    /**
     * @see org.xml.sax.XMLReader#setErrorHandler(org.xml.sax.ErrorHandler)
     */
    public void setErrorHandler(ErrorHandler handler) {
        errorHandler = handler;
        treeBuilderErrorHandler = handler;
        driver = null;
    }
    
    public void setTransitionHander(TransitionHandler handler) {
        transitionHandler = handler;
        driver = null;
    }

    /**
     * Indicates whether NFC normalization of source is being checked.
     * @return <code>true</code> if NFC normalization of source is being checked.
     * @see nu.validator.htmlparser.impl.Tokenizer#isCheckingNormalization()
     */
    public boolean isCheckingNormalization() {
        return checkingNormalization;
    }

    /**
     * Toggles the checking of the NFC normalization of source.
     * @param enable <code>true</code> to check normalization
     * @see nu.validator.htmlparser.impl.Tokenizer#setCheckingNormalization(boolean)
     */
    public void setCheckingNormalization(boolean enable) {
        this.checkingNormalization = enable;
        if (driver != null) {
            driver.setCheckingNormalization(checkingNormalization);
        }
    }

    /**
     * Sets the policy for consecutive hyphens in comments.
     * @param commentPolicy the policy
     * @see nu.validator.htmlparser.impl.Tokenizer#setCommentPolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setCommentPolicy(XmlViolationPolicy commentPolicy) {
        this.commentPolicy = commentPolicy;
        if (driver != null) {
            driver.setCommentPolicy(commentPolicy);
        }
    }

    /**
     * Sets the policy for non-XML characters except white space.
     * @param contentNonXmlCharPolicy the policy
     * @see nu.validator.htmlparser.impl.Tokenizer#setContentNonXmlCharPolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setContentNonXmlCharPolicy(
            XmlViolationPolicy contentNonXmlCharPolicy) {
        this.contentNonXmlCharPolicy = contentNonXmlCharPolicy;
        driver = null;
    }

    /**
     * Sets the policy for non-XML white space.
     * @param contentSpacePolicy the policy
     * @see nu.validator.htmlparser.impl.Tokenizer#setContentSpacePolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setContentSpacePolicy(XmlViolationPolicy contentSpacePolicy) {
        this.contentSpacePolicy = contentSpacePolicy;
        if (driver != null) {
            driver.setContentSpacePolicy(contentSpacePolicy);
        }
    }

    /**
     * Whether the parser considers scripting to be enabled for noscript treatment.
     * 
     * @return <code>true</code> if enabled
     * @see nu.validator.htmlparser.impl.TreeBuilder#isScriptingEnabled()
     */
    public boolean isScriptingEnabled() {
        return scriptingEnabled;
    }

    /**
     * Sets whether the parser considers scripting to be enabled for noscript treatment.
     * @param scriptingEnabled <code>true</code> to enable
     * @see nu.validator.htmlparser.impl.TreeBuilder#setScriptingEnabled(boolean)
     */
    public void setScriptingEnabled(boolean scriptingEnabled) {
        this.scriptingEnabled = scriptingEnabled;
        if (treeBuilder != null) {
            treeBuilder.setScriptingEnabled(scriptingEnabled);
        }
    }

    /**
     * Returns the document mode handler.
     * 
     * @return the documentModeHandler
     */
    public DocumentModeHandler getDocumentModeHandler() {
        return documentModeHandler;
    }

    /**
     * Sets the document mode handler.
     * 
     * @param documentModeHandler
     *            the documentModeHandler to set
     * @see nu.validator.htmlparser.impl.TreeBuilder#setDocumentModeHandler(nu.validator.htmlparser.common.DocumentModeHandler)
     */
    public void setDocumentModeHandler(DocumentModeHandler documentModeHandler) {
        this.documentModeHandler = documentModeHandler;
    }

    /**
     * Returns the streamabilityViolationPolicy.
     * 
     * @return the streamabilityViolationPolicy
     */
    public XmlViolationPolicy getStreamabilityViolationPolicy() {
        return streamabilityViolationPolicy;
    }

    /**
     * Sets the streamabilityViolationPolicy.
     * 
     * @param streamabilityViolationPolicy
     *            the streamabilityViolationPolicy to set
     */
    public void setStreamabilityViolationPolicy(
            XmlViolationPolicy streamabilityViolationPolicy) {
        this.streamabilityViolationPolicy = streamabilityViolationPolicy;
        driver = null;
    }

    /**
     * Returns the <code>Locator</code> during parse.
     * @return the <code>Locator</code>
     */
    public Locator getDocumentLocator() {
        return driver.getDocumentLocator();
    }

    /**
     * Whether <code>lang</code> is mapped to <code>xml:lang</code>.
     * @param mappingLangToXmlLang
     * @see nu.validator.htmlparser.impl.Tokenizer#setMappingLangToXmlLang(boolean)
     */
    public void setMappingLangToXmlLang(boolean mappingLangToXmlLang) {
        this.mappingLangToXmlLang = mappingLangToXmlLang;
        if (driver != null) {
            driver.setMappingLangToXmlLang(mappingLangToXmlLang);
        }
    }

    /**
     * Whether <code>lang</code> is mapped to <code>xml:lang</code>.
     * 
     * @return the mappingLangToXmlLang
     */
    public boolean isMappingLangToXmlLang() {
        return mappingLangToXmlLang;
    }

    /**
     * Whether the <code>xmlns</code> attribute on the root element is 
     * passed to through. (FATAL not allowed.)
     * @param xmlnsPolicy
     * @see nu.validator.htmlparser.impl.Tokenizer#setXmlnsPolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setXmlnsPolicy(XmlViolationPolicy xmlnsPolicy) {
        if (xmlnsPolicy == XmlViolationPolicy.FATAL) {
            throw new IllegalArgumentException("Can't use FATAL here.");
        }
        this.xmlnsPolicy = xmlnsPolicy;
        if (driver != null) {
            driver.setXmlnsPolicy(xmlnsPolicy);
        }
    }

    /**
     * Returns the xmlnsPolicy.
     * 
     * @return the xmlnsPolicy
     */
    public XmlViolationPolicy getXmlnsPolicy() {
        return xmlnsPolicy;
    }

    /**
     * Returns the commentPolicy.
     * 
     * @return the commentPolicy
     */
    public XmlViolationPolicy getCommentPolicy() {
        return commentPolicy;
    }

    /**
     * Returns the contentNonXmlCharPolicy.
     * 
     * @return the contentNonXmlCharPolicy
     */
    public XmlViolationPolicy getContentNonXmlCharPolicy() {
        return contentNonXmlCharPolicy;
    }

    /**
     * Returns the contentSpacePolicy.
     * 
     * @return the contentSpacePolicy
     */
    public XmlViolationPolicy getContentSpacePolicy() {
        return contentSpacePolicy;
    }

    /**
     * @param reportingDoctype
     * @see nu.validator.htmlparser.impl.TreeBuilder#setReportingDoctype(boolean)
     */
    public void setReportingDoctype(boolean reportingDoctype) {
        this.reportingDoctype = reportingDoctype;
        if (treeBuilder != null) {
            treeBuilder.setReportingDoctype(reportingDoctype);
        }
    }

    /**
     * Returns the reportingDoctype.
     * 
     * @return the reportingDoctype
     */
    public boolean isReportingDoctype() {
        return reportingDoctype;
    }

    /**
     * The policy for non-NCName element and attribute names.
     * @param namePolicy
     * @see nu.validator.htmlparser.impl.Tokenizer#setNamePolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setNamePolicy(XmlViolationPolicy namePolicy) {
        this.namePolicy = namePolicy;
        if (driver != null) {
            driver.setNamePolicy(namePolicy);
            treeBuilder.setNamePolicy(namePolicy);
        }
    }
    
    /**
     * Sets the encoding sniffing heuristics.
     * 
     * @param heuristics the heuristics to set
     * @see nu.validator.htmlparser.impl.Tokenizer#setHeuristics(nu.validator.htmlparser.common.Heuristics)
     */
    public void setHeuristics(Heuristics heuristics) {
        this.heuristics = heuristics;
        if (driver != null) {
            driver.setHeuristics(heuristics);
        }
    }
    
    public Heuristics getHeuristics() {
        return this.heuristics;
    }

    /**
     * This is a catch-all convenience method for setting name, xmlns, content space, 
     * content non-XML char and comment policies in one go. This does not affect the 
     * streamability policy or doctype reporting.
     * 
     * @param xmlPolicy
     */
    public void setXmlPolicy(XmlViolationPolicy xmlPolicy) {
        setNamePolicy(xmlPolicy);
        setXmlnsPolicy(xmlPolicy == XmlViolationPolicy.FATAL ? XmlViolationPolicy.ALTER_INFOSET : xmlPolicy);
        setContentSpacePolicy(xmlPolicy);
        setContentNonXmlCharPolicy(xmlPolicy);
        setCommentPolicy(xmlPolicy);
    }

    /**
     * The policy for non-NCName element and attribute names.
     * 
     * @return the namePolicy
     */
    public XmlViolationPolicy getNamePolicy() {
        return namePolicy;
    }

    /**
     * Does nothing.
     * @deprecated
     */
    public void setBogusXmlnsPolicy(
            XmlViolationPolicy bogusXmlnsPolicy) {
    }

    /**
     * Returns <code>XmlViolationPolicy.ALTER_INFOSET</code>.
     * @deprecated
     * @return <code>XmlViolationPolicy.ALTER_INFOSET</code>
     */
    public XmlViolationPolicy getBogusXmlnsPolicy() {
        return XmlViolationPolicy.ALTER_INFOSET;
    }
    
    public void addCharacterHandler(CharacterHandler characterHandler) {
        this.characterHandlers.add(characterHandler);
        if (driver != null) {
            driver.addCharacterHandler(characterHandler);
        }
    }

    
    /**
     * Sets whether comment nodes appear in the tree.
     * @param ignoreComments <code>true</code> to ignore comments
     * @see nu.validator.htmlparser.impl.TreeBuilder#setIgnoringComments(boolean)
     */
    public void setIgnoringComments(boolean ignoreComments) {
        treeBuilder.setIgnoringComments(ignoreComments);
    }

}
