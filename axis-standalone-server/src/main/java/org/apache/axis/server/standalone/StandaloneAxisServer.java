/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
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
package org.apache.axis.server.standalone;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;

/**
 * Jetty based stand-alone Axis server.
 * 
 * @author Andreas Veithen
 */
public final class StandaloneAxisServer {
    private int port;
    private File workDir;
    private int maxSessions = -1;
    
    private Server server;
    private QuitListener quitListener;
    
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public File getWorkDir() {
        return workDir;
    }

    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public void init() throws ServerException {
        StandaloneAxisServlet servlet = new StandaloneAxisServlet();
        
        List resources = new ArrayList();
        
        // Add the work dir as a resource so that Axis can create its server-config.wsdd file there
        new File(workDir, "WEB-INF").mkdir();
        try {
            resources.add(Resource.newResource(workDir.getAbsolutePath()));
        } catch (IOException ex) {
            throw new ServerException(ex);
        }
        
        server = new Server(port);
        server.setStopTimeout(1000);
        server.setStopAtShutdown(true);
        ServletContextHandler context = new ServletContextHandler(server, "/axis", ServletContextHandler.SESSIONS);
        context.setBaseResource(new ResourceCollection((Resource[])resources.toArray(new Resource[resources.size()])));
        if (maxSessions != -1) {
            context.setSessionHandler(new LimitSessionManager(maxSessions));
        }
        quitListener = new QuitListener();
        context.setAttribute(QuitHandler.QUIT_LISTENER, quitListener);
        ServletHandler servletHandler = context.getServletHandler();
        ServletHolder axisServletHolder = new ServletHolder(servlet);
        axisServletHolder.setName("AxisServlet");
        servletHandler.addServlet(axisServletHolder);
        {
            ServletMapping mapping = new ServletMapping();
            mapping.setServletName("AxisServlet");
            mapping.setPathSpec("/services/*");
            servletHandler.addServletMapping(mapping);
        }
        {
            ServletMapping mapping = new ServletMapping();
            mapping.setServletName("AxisServlet");
            mapping.setPathSpec("/servlet/AxisServlet");
            servletHandler.addServletMapping(mapping);
        }
    }
    
    public void start() throws ServerException {
        try {
            server.start();
        } catch (Exception ex) {
            throw new ServerException(ex);
        }
    }
    
    public void awaitQuitRequest() throws InterruptedException {
        quitListener.awaitQuitRequest();
    }
    
    public void stop() throws ServerException {
        try {
            server.stop();
        } catch (Exception ex) {
            throw new ServerException(ex);
        }
    }
}
