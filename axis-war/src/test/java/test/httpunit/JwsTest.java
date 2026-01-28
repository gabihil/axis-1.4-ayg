/*
 * Copyright 2002-2004 The Apache Software Foundation.
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


package test.httpunit;

import java.net.URLEncoder;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * test for JWS pages being processed
 * @author Steve Loughran
 * @created Jul 10, 2002 12:09:20 AM
 */

public class JwsTest extends HttpUnitTestBase {

    public JwsTest(String name) {
        super(name);
    }

    private boolean isJwsHandlerAvailable() {
        try {
            Class.forName("org.apache.axis.handlers.JWSHandler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void testStockQuote() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url+"/StockQuoteService.jws?wsdl");
        expectJwsDisabled(request);
    }

    public void testEchoHeadersWsdl() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url + "/EchoHeaders.jws?wsdl");
        expectJwsDisabled(request);
    }


    public void testEchoHeaders() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url + "/EchoHeaders.jws");
        expectJwsDisabled(request);
    }

    /**
     * see that we get a hello back
     * @throws Exception
     */
    public void testEchoHeadersWhoami() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders.jws");
        request.setParameter("method", "whoami");
        expectJwsDisabled(request);
    }

    /**
     * do we get a list of headers back?
     * @throws Exception
     */
    public void testEchoHeadersList() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders.jws");
        request.setHeaderField("x-header","echo-header-test");
        request.setParameter("method", "list");
        expectJwsDisabled(request);
    }

    /**
     * send an echo with a space down
     * @throws Exception
     */
    public void testEchoHeadersEcho() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders.jws?method=echo&param=foo+bar");
        expectJwsDisabled(request);
    }

    /**
     * we throw an error on missing JWS pages
     * @throws Exception
     */
    public void testMissingJWSRaisesException() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders-not-really-there.jws");
        expectJwsDisabled(request);
    }

    /**
     * axis faults.
     * @throws Exception
     */
    public void testAxisFaultIsXML() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders.jws?method=throwAxisFault&param=oops!");
        expectJwsDisabled(request);
    }

    /**
     * exceptions are user faults
     * @throws Exception
     */
    public void testExceptionIsXML() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders.jws?method=throwAxisFault&param=oops!");
        expectJwsDisabled(request);
    }

    /**
     * 
     */

    /**
     * send a complex unicode round the loop and see what happens
     * @throws Exception
     */
    public void testEchoHeadersEchoUnicode() throws Exception {
        if (!isJwsHandlerAvailable()) {
            return;
        }
        WebRequest request = new GetMethodWebRequest(url
                + "/EchoHeaders.jws?method=echo&param=" + URLEncoder.encode("\u221a", "UTF-8"));
        expectJwsDisabled(request);
    }

    private void expectJwsDisabled(WebRequest request) throws Exception {
        expectErrorCode(request, 404, "JWS support is disabled.");
    }
}
