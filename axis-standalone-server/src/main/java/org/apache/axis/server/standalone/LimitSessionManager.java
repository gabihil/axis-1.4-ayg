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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.session.Session;
import org.eclipse.jetty.server.session.SessionHandler;

/**
 * {@link SessionHandler} extension that limits the number of concurrently active sessions.
 *
 * @author Andreas Veithen
 */
final class LimitSessionManager extends SessionHandler {
    private final int maxSessions;
    private final Map<String, Long> sessionAccessOrder =
        new LinkedHashMap<String, Long>(16, 0.75f, true);

    LimitSessionManager(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    @Override
    public HttpSession newHttpSession(HttpServletRequest request) {
        HttpSession session = super.newHttpSession(request);
        trackAccess(session);
        enforceLimit(session.getId());
        return session;
    }

    @Override
    public HttpCookie access(HttpSession session, boolean secure) {
        HttpCookie cookie = super.access(session, secure);
        trackAccess(session);
        return cookie;
    }

    @Override
    public Session removeSession(String id, boolean invalidate) {
        Session removed = super.removeSession(id, invalidate);
        synchronized (sessionAccessOrder) {
            sessionAccessOrder.remove(id);
        }
        return removed;
    }

    private void trackAccess(HttpSession session) {
        synchronized (sessionAccessOrder) {
            sessionAccessOrder.put(session.getId(), System.currentTimeMillis());
        }
    }

    private void enforceLimit(String currentSessionId) {
        String evictId = null;
        synchronized (sessionAccessOrder) {
            if (sessionAccessOrder.size() <= maxSessions) {
                return;
            }
            Iterator<String> iterator = sessionAccessOrder.keySet().iterator();
            if (iterator.hasNext()) {
                evictId = iterator.next();
                iterator.remove();
            }
        }
        if (evictId != null && !evictId.equals(currentSessionId)) {
            Session session = getSession(evictId);
            if (session != null) {
                session.invalidate();
            }
        }
    }
}
