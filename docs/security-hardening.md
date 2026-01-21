# Security hardening

This document captures **additional hardening guidance** beyond the code fixes already included in this fork.

## CVE-2019-0227 hardening checklist

> Goal: reduce attack surface for request-driven and configuration-driven abuse by locking down
> deployment, XML processing, and resource usage.

1. **Lock down deployment and service exposure**
   - Ensure `server-config.wsdd` (and any service deployment descriptors) are **read-only** for the
     Axis runtime user. This prevents runtime edits or accidental re-deployments.
   - Disable any administrative endpoints or deployment handlers in your container/router layer,
     even if they are not present in this fork, to avoid accidental enablement later.
   - Run Axis under a **least-privilege OS account** with no write permissions to the webapp root
     or classpath directories.

2. **Disable external entity and remote schema access globally**
   Configure JVM-level XML security properties to prevent SSRF-style behavior and unexpected remote
   fetches during XML parsing or schema resolution:

   ```bash
   -Djavax.xml.accessExternalDTD=
   -Djavax.xml.accessExternalSchema=
   -Djavax.xml.accessExternalStylesheet=
   -Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl
   -Djavax.xml.parsers.DocumentBuilderFactory=com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl
   ```

   Combine this with **network egress allowlists** so the Axis runtime cannot access arbitrary
   outbound hosts.

3. **Apply strict request size and timeout limits**
   - Enforce **maximum request size**, **maximum header size**, and **timeouts** at the container
     layer (Tomcat/Jetty/Netty). Example for Tomcat:

     ```xml
     <Connector
         ...
         maxPostSize="2097152"
         maxSwallowSize="2097152"
         maxHttpHeaderSize="8192"
         connectionTimeout="20000" />
     ```

   - If you expose SOAP attachments, set explicit **attachment size limits** and consider
     **disallowing attachments entirely** unless strictly required.

4. **Constrain service routing and inputs**
   - Use a reverse proxy or API gateway to enforce a **SOAPAction allowlist** and XML schema
     validation before requests reach Axis.
   - Reject unknown services at the edge (do not allow `?wsdl` or `service` listing to be globally
     accessible unless needed).

5. **Isolate the runtime**
   - Deploy Axis in a container or sandbox with a **read-only filesystem**, no shell access, and
     a **deny-by-default outbound network policy**.
   - Use OS-level controls such as AppArmor/SELinux profiles where available.
