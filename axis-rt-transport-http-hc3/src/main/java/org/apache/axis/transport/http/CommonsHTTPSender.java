/*
 * Copyright 2001-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.axis.transport.http;

import org.apache.axis.AxisFault;
import org.apache.axis.Constants;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.components.logger.LogFactory;
import org.apache.axis.components.net.CommonsHTTPClientProperties;
import org.apache.axis.components.net.CommonsHTTPClientPropertiesFactory;
import org.apache.axis.components.net.TransportClientProperties;
import org.apache.axis.components.net.TransportClientPropertiesFactory;
import org.apache.axis.handlers.BasicHandler;
import org.apache.axis.soap.SOAP12Constants;
import org.apache.axis.soap.SOAPConstants;
import org.apache.axis.utils.JavaUtils;
import org.apache.axis.utils.Messages;
import org.apache.axis.utils.NetworkUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.commons.logging.Log;

import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class uses Jakarta Commons's HttpClient to call a SOAP server.
 *
 * @author Davanum Srinivas (dims@yahoo.com)
 * History: By Chandra Talluri
 * Modifications done for maintaining sessions. Cookies needed to be set on
 * HttpState not on MessageContext, since ttpMethodBase overwrites the cookies 
 * from HttpState. Also we need to setCookiePolicy on HttpState to 
 * CookiePolicy.COMPATIBILITY else it is defaulting to RFC2109Spec and adding 
 * Version information to it and tomcat server not recognizing it
 */
public class CommonsHTTPSender extends BasicHandler {
    
    /** Field log           */
    protected static Log log =
        LogFactory.getLog(CommonsHTTPSender.class.getName());
    
    protected PoolingHttpClientConnectionManager connectionManager;
    protected CommonsHTTPClientProperties clientProperties;
    boolean httpChunkStream = true; //Use HTTP chunking or not.

    public CommonsHTTPSender() {
        initialize();
    }

    protected void initialize() {
        this.clientProperties = CommonsHTTPClientPropertiesFactory.create();
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().build();
        cm.setDefaultMaxPerRoute(clientProperties.getMaximumConnectionsPerHost());
        cm.setMaxTotal(clientProperties.getMaximumTotalConnections());
        this.connectionManager = cm;
    }
    
    /**
     * invoke creates a socket connection, sends the request SOAP message and then
     * reads the response SOAP message back from the SOAP server
     *
     * @param msgContext the messsage context
     *
     * @throws AxisFault
     */
    public void invoke(MessageContext msgContext) throws AxisFault {
        HttpUriRequestBase request = null;
        if (log.isDebugEnabled()) {
            log.debug(Messages.getMessage("enter00",
                                          "CommonsHTTPSender::invoke"));
        }
        try {
            URL targetURL =
                new URL(msgContext.getStrProp(MessageContext.TRANS_URL));

            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            HttpHost proxyHost = getProxyHost(targetURL, credentialsProvider);

            try (CloseableHttpClient httpClient = buildHttpClient(credentialsProvider, proxyHost)) {
                boolean posting = true;
                
                // If we're SOAP 1.2, allow the web method to be set from the
                // MessageContext.
                if (msgContext.getSOAPConstants() == SOAPConstants.SOAP12_CONSTANTS) {
                    String webMethod = msgContext.getStrProp(SOAP12Constants.PROP_WEBMETHOD);
                    if (webMethod != null) {
                        posting = webMethod.equals(HTTPConstants.HEADER_POST);
                    }
                }

                if (posting) {
                    Message reqMessage = msgContext.getRequestMessage();
                    HttpPost post = new HttpPost(targetURL.toString());
                    request = post;

                    addContextInfo(request, credentialsProvider, msgContext, targetURL);

                    boolean useChunking = httpChunkStream;
                    String httpVersion = msgContext.getStrProp(MessageContext.HTTP_TRANSPORT_VERSION);
                    if (HTTPConstants.HEADER_PROTOCOL_V10.equals(httpVersion)) {
                        request.setVersion(HttpVersion.HTTP_1_0);
                        useChunking = false;
                    }

                    HttpEntity requestEntity = new MessageEntity(reqMessage, useChunking,
                        msgContext.isPropertyTrue(HTTPConstants.MC_GZIP_REQUEST));
                    post.setEntity(requestEntity);
                } else {
                    request = new HttpGet(targetURL.toString());
                    addContextInfo(request, credentialsProvider, msgContext, targetURL);
                }

                if (msgContext.getMaintainSession()) {
                    addCookieHeaders(msgContext, request);
                }

                try (CloseableHttpResponse response = httpClient.execute(request, HttpClientContext.create())) {
                int returnCode = response.getCode();

                String contentType = 
                    getHeader(response, HTTPConstants.HEADER_CONTENT_TYPE);
                String contentLocation = 
                    getHeader(response, HTTPConstants.HEADER_CONTENT_LOCATION);
                String contentLength = 
                    getHeader(response, HTTPConstants.HEADER_CONTENT_LENGTH);

                if ((returnCode > 199) && (returnCode < 300)) {
                    
                    // SOAP return is OK - so fall through
                } else if (msgContext.getSOAPConstants() ==
                           SOAPConstants.SOAP12_CONSTANTS) {
                    // For now, if we're SOAP 1.2, fall through, since the range of
                    // valid result codes is much greater
                } else if ((contentType != null) && !contentType.equals("text/html")
                           && ((returnCode > 499) && (returnCode < 600))) {
                    
                    // SOAP Fault should be in here - so fall through
                } else {
                    String statusMessage = response.getReasonPhrase();
                    AxisFault fault = new AxisFault("HTTP",
                                                    "(" + returnCode + ")"
                                                    + statusMessage, null,
                                                    null);
                    
                    try {
                        String body = null;
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            body = EntityUtils.toString(entity);
                        }
                        fault.setFaultDetailString(
                             Messages.getMessage("return01",
                                                 "" + returnCode,
                                                 body));
                        fault.addFaultDetail(Constants.QNAME_FAULTDETAIL_HTTPERRORCODE,
                                             Integer.toString(returnCode));
                        throw fault;
                    } finally {
                        EntityUtils.consumeQuietly(response.getEntity());
                    }
                }
                
                // wrap the response body stream so that close() also releases 
                // the connection back to the pool.
                InputStream releaseConnectionOnCloseStream = 
                    createConnectionReleasingInputStream(response);

                Header contentEncoding = 
                    response.getFirstHeader(HTTPConstants.HEADER_CONTENT_ENCODING);
                if (contentEncoding != null) {
                    if (contentEncoding.getValue().
                            equalsIgnoreCase(HTTPConstants.COMPRESSION_GZIP)) {
                        releaseConnectionOnCloseStream = 
                            new GZIPInputStream(releaseConnectionOnCloseStream);
                    } else {
                        AxisFault fault = new AxisFault("HTTP",
                                "unsupported content-encoding of '" 
                                + contentEncoding.getValue()
                                + "' found", null, null);
                        throw fault;
                    }
                        
                }
                Message outMsg = new Message(releaseConnectionOnCloseStream,
                                             false, contentType, contentLocation);
                // Transfer HTTP headers of HTTP message to MIME headers of SOAP message
                Header[] responseHeaders = response.getHeaders();
                MimeHeaders responseMimeHeaders = outMsg.getMimeHeaders();
                for (int i = 0; i < responseHeaders.length; i++) {
                    Header responseHeader = responseHeaders[i];
                    responseMimeHeaders.addHeader(responseHeader.getName(), 
                                                  responseHeader.getValue());
                }
                outMsg.setMessageType(Message.RESPONSE);
                msgContext.setResponseMessage(outMsg);
                if (log.isDebugEnabled()) {
                    if (null == contentLength) {
                        log.debug("\n"
                        + Messages.getMessage("no00", "Content-Length"));
                    }
                    log.debug("\n" + Messages.getMessage("xmlRecd00"));
                    log.debug("-----------------------------------------------");
                    log.debug(outMsg.getSOAPPartAsString());
                }
                
                // if we are maintaining session state,
                // handle cookies (if any)
                if (msgContext.getMaintainSession()) {
                    Header[] headers = response.getHeaders();

                    for (int i = 0; i < headers.length; i++) {
                        if (headers[i].getName().equalsIgnoreCase(HTTPConstants.HEADER_SET_COOKIE)) {
                            handleCookie(HTTPConstants.HEADER_COOKIE, headers[i].getValue(), msgContext);
                        } else if (headers[i].getName().equalsIgnoreCase(HTTPConstants.HEADER_SET_COOKIE2)) {
                            handleCookie(HTTPConstants.HEADER_COOKIE2, headers[i].getValue(), msgContext);
                        }
                    }
                }

                // always release the connection back to the pool if 
                // it was one way invocation
                    if (msgContext.isPropertyTrue("axis.one.way")) {
                        EntityUtils.consumeQuietly(response.getEntity());
                    }
                }
            }
        } catch (Exception e) {
            log.debug(e);
            throw AxisFault.makeFault(e);
        }
        
        if (log.isDebugEnabled()) {
            log.debug(Messages.getMessage("exit00",
                                          "CommonsHTTPSender::invoke"));
        }
    }

    /**
     * little helper function for cookies. fills up the message context with
     * a string or an array of strings (if there are more than one Set-Cookie)
     *
     * @param cookieName
     * @param setCookieName
     * @param cookie
     * @param msgContext
     */
    public void handleCookie(String cookieName, String cookie,
            MessageContext msgContext) {
        
        cookie = cleanupCookie(cookie);
        int keyIndex = cookie.indexOf("=");
        String key = (keyIndex != -1) ? cookie.substring(0, keyIndex) : cookie;
        
        ArrayList cookies = new ArrayList();
        Object oldCookies = msgContext.getProperty(cookieName);
        boolean alreadyExist = false;
        if(oldCookies != null) {
            if(oldCookies instanceof String[]) {
                String[] oldCookiesArray = (String[])oldCookies;
                for(int i = 0; i < oldCookiesArray.length; i++) {
                    String anOldCookie = oldCookiesArray[i];
                    if (key != null && anOldCookie.indexOf(key) == 0) { // same cookie key
                        anOldCookie = cookie;             // update to new one
                        alreadyExist = true;
                    }
                    cookies.add(anOldCookie);
                }
            } else {
                String oldCookie = (String)oldCookies;
                if (key != null && oldCookie.indexOf(key) == 0) { // same cookie key
                    oldCookie = cookie;             // update to new one
                    alreadyExist = true;
                }
                cookies.add(oldCookie);
            }
        }
        
        if (!alreadyExist) {
            cookies.add(cookie);
        }
        
        if(cookies.size()==1) {
            msgContext.setProperty(cookieName, cookies.get(0));
        } else if (cookies.size() > 1) {
            msgContext.setProperty(cookieName, cookies.toArray(new String[cookies.size()]));
        }
    }
    
    private void addCookieHeaders(MessageContext msgContext, HttpUriRequestBase request) {
        addCookieHeader(msgContext, request, HTTPConstants.HEADER_COOKIE);
        addCookieHeader(msgContext, request, HTTPConstants.HEADER_COOKIE2);
    }

    private void addCookieHeader(MessageContext msgContext, HttpUriRequestBase request, String header) {
        Object cookies = msgContext.getProperty(header);
        if (cookies == null) {
            return;
        }
        if (cookies instanceof String[]) {
            String[] cookieArray = (String[]) cookies;
            for (int i = 0; i < cookieArray.length; i++) {
                request.addHeader(header, cookieArray[i]);
            }
        } else {
            request.addHeader(header, (String) cookies);
        }
    }

    private CloseableHttpClient buildHttpClient(BasicCredentialsProvider credentialsProvider, HttpHost proxyHost) {
        org.apache.hc.client5.http.impl.classic.HttpClientBuilder builder = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setConnectionManagerShared(true)
            .setDefaultCredentialsProvider(credentialsProvider);
        if (proxyHost != null) {
            builder.setProxy(proxyHost);
        }
        return builder.build();
    }

    /**
     * cleanup the cookie value.
     *
     * @param cookie initial cookie value
     *
     * @return a cleaned up cookie value.
     */
    private String cleanupCookie(String cookie) {
        cookie = cookie.trim();
        // chop after first ; a la Apache SOAP (see HTTPUtils.java there)
        int index = cookie.indexOf(';');
        if (index != -1) {
            cookie = cookie.substring(0, index);
        }
        return cookie;
    }
    
    private HttpHost getProxyHost(URL targetURL, BasicCredentialsProvider credentialsProvider) {
        TransportClientProperties tcp = 
            TransportClientPropertiesFactory.create(targetURL.getProtocol()); // http or https
        boolean hostInNonProxyList =
            isHostInNonProxyList(targetURL.getHost(), tcp.getNonProxyHosts());
        if (hostInNonProxyList) {
            return null;
        }
        if (tcp.getProxyHost().length() == 0 || tcp.getProxyPort().length() == 0) {
            return null;
        }
        int proxyPort = Integer.parseInt(tcp.getProxyPort());
        HttpHost proxyHost = new HttpHost(tcp.getProxyHost(), proxyPort);
        if (tcp.getProxyUser().length() != 0) {
            Credentials proxyCred =
                new UsernamePasswordCredentials(tcp.getProxyUser(), tcp.getProxyPassword().toCharArray());
            // if the username is in the form "user\domain" 
            // then use NTCredentials instead.
            int domainIndex = tcp.getProxyUser().indexOf("\\");
            if (domainIndex > 0) {
                String domain = tcp.getProxyUser().substring(0, domainIndex);
                if (tcp.getProxyUser().length() > domainIndex + 1) {
                    String user = tcp.getProxyUser().substring(domainIndex + 1);
                    proxyCred = new NTCredentials(user,
                            tcp.getProxyPassword().toCharArray(),
                            tcp.getProxyHost(), domain);
                }
            }
            credentialsProvider.setCredentials(new AuthScope(proxyHost), proxyCred);
        }
        return proxyHost;
    }

    private int resolvePort(URL targetURL) {
        int port = targetURL.getPort();
        if (port == -1) {
            if ("https".equalsIgnoreCase(targetURL.getProtocol())) {
                return 443;
            }
            return 80;
        }
        return port;
    }
    
    /**
     * Extracts info from message context.
     *
     * @param request request
     * @param credentialsProvider credentials provider for auth
     * @param msgContext the message context
     * @param tmpURL the url to post to.
     *
     * @throws Exception
     */
    private void addContextInfo(HttpUriRequestBase request,
                                BasicCredentialsProvider credentialsProvider,
                                MessageContext msgContext, 
                                URL tmpURL)
        throws Exception {
        RequestConfig.Builder requestConfig = RequestConfig.custom();

        // optionally set a timeout for the request
        if (msgContext.getTimeout() != 0) {
            /* ISSUE: these are not the same, but MessageContext has only one
                      definition of timeout */
            Timeout timeout = Timeout.ofMilliseconds(msgContext.getTimeout());
            requestConfig.setConnectTimeout(timeout);
            requestConfig.setResponseTimeout(timeout);
        } else {
            if (clientProperties.getDefaultConnectionTimeout() > 0) {
                requestConfig.setConnectTimeout(Timeout.ofMilliseconds(clientProperties.getDefaultConnectionTimeout()));
            }
            if (clientProperties.getDefaultSoTimeout() > 0) {
                requestConfig.setResponseTimeout(Timeout.ofMilliseconds(clientProperties.getDefaultSoTimeout()));
            }
        }
        if (clientProperties.getConnectionPoolTimeout() > 0) {
            requestConfig.setConnectionRequestTimeout(
                Timeout.ofMilliseconds(clientProperties.getConnectionPoolTimeout()));
        }
        
        // Get SOAPAction, default to ""
        String action = msgContext.useSOAPAction()
            ? msgContext.getSOAPActionURI()
            : "";
        
        if (action == null) {
            action = "";
        }

        Message msg = msgContext.getRequestMessage();
        if (msg != null){
            request.setHeader(HTTPConstants.HEADER_CONTENT_TYPE,
                              msg.getContentType(msgContext.getSOAPConstants()));
        }
        request.setHeader(HTTPConstants.HEADER_SOAP_ACTION, 
                          "\"" + action + "\"");
        request.setHeader(HTTPConstants.HEADER_USER_AGENT, Messages.getMessage("axisUserAgent"));
        String userID = msgContext.getUsername();
        String passwd = msgContext.getPassword();
        
        // if UserID is not part of the context, but is in the URL, use
        // the one in the URL.
        if ((userID == null) && (tmpURL.getUserInfo() != null)) {
            String info = tmpURL.getUserInfo();
            int sep = info.indexOf(':');
            
            if ((sep >= 0) && (sep + 1 < info.length())) {
                userID = info.substring(0, sep);
                passwd = info.substring(sep + 1);
            } else {
                userID = info;
            }
        }
        if (userID != null) {
            Credentials userCred =
                new UsernamePasswordCredentials(userID,
                                                passwd == null ? new char[0] : passwd.toCharArray());
            // if the username is in the form "user\domain"
            // then use NTCredentials instead.
            int domainIndex = userID.indexOf("\\");
            if (domainIndex > 0) {
                String domain = userID.substring(0, domainIndex);
                if (userID.length() > domainIndex + 1) {
                    String user = userID.substring(domainIndex + 1);
                    userCred = new NTCredentials(user,
                                    passwd == null ? new char[0] : passwd.toCharArray(),
                                    NetworkUtils.getLocalHostname(), domain);
                }
            }
            int targetPort = resolvePort(tmpURL);
            credentialsProvider.setCredentials(new AuthScope(tmpURL.getHost(), targetPort), userCred);
        }
        
        // add compression headers if needed
        if (msgContext.isPropertyTrue(HTTPConstants.MC_ACCEPT_GZIP)) {
            request.addHeader(HTTPConstants.HEADER_ACCEPT_ENCODING, 
                    HTTPConstants.COMPRESSION_GZIP);
        }
        if (msgContext.isPropertyTrue(HTTPConstants.MC_GZIP_REQUEST)) {
            request.addHeader(HTTPConstants.HEADER_CONTENT_ENCODING, 
                    HTTPConstants.COMPRESSION_GZIP);
        }
        
        // Transfer MIME headers of SOAPMessage to HTTP headers. 
        if (msg != null) {
            MimeHeaders mimeHeaders = msg.getMimeHeaders();
            if (mimeHeaders != null) {
                for (Iterator i = mimeHeaders.getAllHeaders(); i.hasNext(); ) {
                    MimeHeader mimeHeader = (MimeHeader) i.next();
                    //HEADER_CONTENT_TYPE and HEADER_SOAP_ACTION are already set.
                    //Let's not duplicate them.
                    String headerName = mimeHeader.getName();
                    if (headerName.equals(HTTPConstants.HEADER_CONTENT_TYPE)
                            || headerName.equals(HTTPConstants.HEADER_SOAP_ACTION)) {
                            continue;
                    }
                    request.addHeader(mimeHeader.getName(), 
                                      mimeHeader.getValue());
                }
            }
        }

        // process user defined headers for information.
        Hashtable userHeaderTable =
            (Hashtable) msgContext.getProperty(HTTPConstants.REQUEST_HEADERS);
        
        if (userHeaderTable != null) {
            boolean expectContinue = false;
            for (Iterator e = userHeaderTable.entrySet().iterator();
                 e.hasNext();) {
                Map.Entry me = (Map.Entry) e.next();
                Object keyObj = me.getKey();
                
                if (null == keyObj) {
                    continue;
                }
                String key = keyObj.toString().trim();
                String value = me.getValue().toString().trim();
                
                if (key.equalsIgnoreCase(HTTPConstants.HEADER_EXPECT) &&
                    value.equalsIgnoreCase(HTTPConstants.HEADER_EXPECT_100_Continue)) {
                    expectContinue = true;
                } else if (key.equalsIgnoreCase(HTTPConstants.HEADER_TRANSFER_ENCODING_CHUNKED)) {
                    String val = me.getValue().toString();
                    if (null != val)  {
                        httpChunkStream = JavaUtils.isTrue(val);
                    }
                } else {
                    request.addHeader(key, value);
                }
            }
            requestConfig.setExpectContinueEnabled(expectContinue);
        }

        request.setConfig(requestConfig.build());
    }
    
    /**
     * Check if the specified host is in the list of non proxy hosts.
     *
     * @param host host name
     * @param nonProxyHosts string containing the list of non proxy hosts
     *
     * @return true/false
     */
    protected boolean isHostInNonProxyList(String host, String nonProxyHosts) {
        
        if ((nonProxyHosts == null) || (host == null)) {
            return false;
        }
        
        /*
         * The http.nonProxyHosts system property is a list enclosed in
         * double quotes with items separated by a vertical bar.
         */
        StringTokenizer tokenizer = new StringTokenizer(nonProxyHosts, "|\"");
        
        while (tokenizer.hasMoreTokens()) {
            String pattern = tokenizer.nextToken();
            
            if (log.isDebugEnabled()) {
                log.debug(Messages.getMessage("match00",
                new String[]{"HTTPSender",
                host,
                pattern}));
            }
            if (match(pattern, host, false)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Matches a string against a pattern. The pattern contains two special
     * characters:
     * '*' which means zero or more characters,
     *
     * @param pattern the (non-null) pattern to match against
     * @param str     the (non-null) string that must be matched against the
     *                pattern
     * @param isCaseSensitive
     *
     * @return <code>true</code> when the string matches against the pattern,
     *         <code>false</code> otherwise.
     */
    protected static boolean match(String pattern, String str,
                                   boolean isCaseSensitive) {
        
        char[] patArr = pattern.toCharArray();
        char[] strArr = str.toCharArray();
        int patIdxStart = 0;
        int patIdxEnd = patArr.length - 1;
        int strIdxStart = 0;
        int strIdxEnd = strArr.length - 1;
        char ch;
        boolean containsStar = false;
        
        for (int i = 0; i < patArr.length; i++) {
            if (patArr[i] == '*') {
                containsStar = true;
                break;
            }
        }
        if (!containsStar) {
            
            // No '*'s, so we make a shortcut
            if (patIdxEnd != strIdxEnd) {
                return false;        // Pattern and string do not have the same size
            }
            for (int i = 0; i <= patIdxEnd; i++) {
                ch = patArr[i];
                if (isCaseSensitive && (ch != strArr[i])) {
                    return false;    // Character mismatch
                }
                if (!isCaseSensitive
                && (Character.toUpperCase(ch)
                != Character.toUpperCase(strArr[i]))) {
                    return false;    // Character mismatch
                }
            }
            return true;             // String matches against pattern
        }
        if (patIdxEnd == 0) {
            return true;    // Pattern contains only '*', which matches anything
        }
        
        // Process characters before first star
        while ((ch = patArr[patIdxStart]) != '*'
        && (strIdxStart <= strIdxEnd)) {
            if (isCaseSensitive && (ch != strArr[strIdxStart])) {
                return false;    // Character mismatch
            }
            if (!isCaseSensitive
            && (Character.toUpperCase(ch)
            != Character.toUpperCase(strArr[strIdxStart]))) {
                return false;    // Character mismatch
            }
            patIdxStart++;
            strIdxStart++;
        }
        if (strIdxStart > strIdxEnd) {
            
            // All characters in the string are used. Check if only '*'s are
            // left in the pattern. If so, we succeeded. Otherwise failure.
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (patArr[i] != '*') {
                    return false;
                }
            }
            return true;
        }
        
        // Process characters after last star
        while ((ch = patArr[patIdxEnd]) != '*' && (strIdxStart <= strIdxEnd)) {
            if (isCaseSensitive && (ch != strArr[strIdxEnd])) {
                return false;    // Character mismatch
            }
            if (!isCaseSensitive
            && (Character.toUpperCase(ch)
            != Character.toUpperCase(strArr[strIdxEnd]))) {
                return false;    // Character mismatch
            }
            patIdxEnd--;
            strIdxEnd--;
        }
        if (strIdxStart > strIdxEnd) {
            
            // All characters in the string are used. Check if only '*'s are
            // left in the pattern. If so, we succeeded. Otherwise failure.
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (patArr[i] != '*') {
                    return false;
                }
            }
            return true;
        }
        
        // process pattern between stars. padIdxStart and patIdxEnd point
        // always to a '*'.
        while ((patIdxStart != patIdxEnd) && (strIdxStart <= strIdxEnd)) {
            int patIdxTmp = -1;
            
            for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
                if (patArr[i] == '*') {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patIdxStart + 1) {
                
                // Two stars next to each other, skip the first one.
                patIdxStart++;
                continue;
            }
            
            // Find the pattern between padIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            int patLength = (patIdxTmp - patIdxStart - 1);
            int strLength = (strIdxEnd - strIdxStart + 1);
            int foundIdx = -1;
            
            strLoop:
                for (int i = 0; i <= strLength - patLength; i++) {
                    for (int j = 0; j < patLength; j++) {
                        ch = patArr[patIdxStart + j + 1];
                        if (isCaseSensitive
                        && (ch != strArr[strIdxStart + i + j])) {
                            continue strLoop;
                        }
                        if (!isCaseSensitive && (Character
                        .toUpperCase(ch) != Character
                        .toUpperCase(strArr[strIdxStart + i + j]))) {
                            continue strLoop;
                        }
                    }
                    foundIdx = strIdxStart + i;
                    break;
                }
                if (foundIdx == -1) {
                    return false;
                }
                patIdxStart = patIdxTmp;
                strIdxStart = foundIdx + patLength;
        }
        
        // All characters in the string are used. Check if only '*'s are left
        // in the pattern. If so, we succeeded. Otherwise failure.
        for (int i = patIdxStart; i <= patIdxEnd; i++) {
            if (patArr[i] != '*') {
                return false;
            }
        }
        return true;
    }

    private static String getHeader(CloseableHttpResponse response, String headerName) {
        Header header = response.getFirstHeader(headerName);
        return (header == null) ? null : header.getValue().trim();
    }

    private InputStream createConnectionReleasingInputStream(final CloseableHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            response.close();
            return new ByteArrayInputStream(new byte[0]);
        }
        return new FilterInputStream(entity.getContent()) {
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        response.close();
                    }
                }
            };
    }

    private static class MessageEntity implements HttpEntity {
        
        private final Message message;
        private final boolean chunked;
        private final boolean gzip;
        private ByteArrayOutputStream cachedStream;

        MessageEntity(Message message, boolean chunked, boolean gzip) {
            this.message = message;
            this.chunked = chunked;
            this.gzip = gzip;
        }

        public boolean isRepeatable() {
            return true;
        }

        public boolean isChunked() {
            return chunked;
        }

        public long getContentLength() {
            if (chunked) {
                return -1;
            }
            if (gzip) {
                ByteArrayOutputStream cached = getCachedStream();
                return cached == null ? -1 : cached.size();
            }
            try {
                return message.getContentLength();
            } catch (Exception e) {
                return -1;
            }
        }

        public String getContentType() {
            return null; // a separate header is added
        }

        public String getContentEncoding() {
            return null;
        }

        public InputStream getContent() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeTo(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        }

        public void writeTo(OutputStream out) throws IOException {
            if (cachedStream != null) {
                cachedStream.writeTo(out);
                return;
            }
            if (gzip) {
                GZIPOutputStream gzStream = new GZIPOutputStream(out);
                writeMessage(gzStream);
                gzStream.finish();
            } else {
                writeMessage(out);
            }
        }

        public boolean isStreaming() {
            return false;
        }

        public Set<String> getTrailerNames() {
            return Collections.emptySet();
        }

        public Supplier<java.util.List<? extends Header>> getTrailers() {
            return new Supplier<java.util.List<? extends Header>>() {
                public java.util.List<? extends Header> get() {
                    return Collections.emptyList();
                }
            };
        }

        public void close() throws IOException {
        }

        private void writeMessage(OutputStream out) throws IOException {
            try {
                message.writeTo(out);
            } catch (SOAPException e) {
                throw new IOException(e.getMessage());
            }
        }

        private ByteArrayOutputStream getCachedStream() {
            if (chunked) {
                return null;
            }
            if (cachedStream != null) {
                return cachedStream;
            }
            if (!gzip) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                writeTo(baos);
                cachedStream = baos;
                return cachedStream;
            } catch (IOException e) {
                return null;
            }
        }
    }
}
