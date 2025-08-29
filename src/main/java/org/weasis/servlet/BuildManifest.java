/*
 * Copyright (c) 2011-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.mf.thread.ManifestBuilder;

/**
 * Servlet responsible for building DICOM manifests. Handles both GET and POST requests to generate
 * manifest URLs or redirect to manifest endpoints.
 *
 * @author Nicolas Roduit
 */
@WebServlet(name = "BuildManifest", urlPatterns = "/manifest")
public class BuildManifest extends HttpServlet {

  @Serial private static final long serialVersionUID = 575795035231900320L;
  private static final Logger LOGGER = LoggerFactory.getLogger(BuildManifest.class);

  public BuildManifest() {
    super();
  }

  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/xml");
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    buildManifest(request, response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    buildManifest(request, response);
  }

  private void buildManifest(HttpServletRequest request, HttpServletResponse response) {

    response.setStatus(HttpServletResponse.SC_ACCEPTED);

    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
    response.setHeader("Pragma", "no-cache"); // HTTP 1.0
    response.setDateHeader("Expires", -1); // Proxies

    try {
      if (LOGGER.isDebugEnabled()) {
        ServletUtil.logInfo(request, LOGGER);
      }
      ConnectorProperties connectorProperties =
          (ConnectorProperties) this.getServletContext().getAttribute("componentProperties");
      // Check if the source of this request is allowed
      if (!ServletUtil.isRequestAllowed(request, connectorProperties, LOGGER)) {
        return;
      }

      ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);

      boolean gzip = request.getParameter("gzip") != null;

      ManifestBuilder builder = ServletUtil.buildManifest(request, props);
      // BUILDER IS NULL WHEN NO ALLOWED PARAMETER ARE GIVEN WHICH LEADS TO NO MANIFEST BUILT

      if (builder == null) {
        // NO body in response, see: https://httpstatuses.com/204
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setHeader("Cause", "No allowed parameters have been given to build a manifest");
      } else {
        String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, gzip);
        wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);
        response.setStatus(HttpServletResponse.SC_OK);

        if (request.getParameter(ConnectorProperties.PARAM_URL) != null) {
          response.setContentType("text/plain");
          response.getWriter().print(wadoQueryUrl);
        } else {
          response.sendRedirect(wadoQueryUrl);
        }
      }

    } catch (Exception e) {
      LOGGER.error("Building manifest", e);
      ServletUtil.sendResponseError(
          response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }
}
