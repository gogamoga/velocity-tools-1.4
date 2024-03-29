package org.apache.velocity.tools.view.servlet;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import org.apache.commons.digester.RuleSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.tools.view.ToolInfo;
import org.apache.velocity.tools.view.XMLToolboxManager;
import org.apache.velocity.tools.view.context.ViewContext;


/**
 * <p>A toolbox manager for the servlet environment.</p>
 *
 * <p>A toolbox manager is responsible for automatically filling the Velocity
 * context with a set of view tools. This class provides the following
 * features:</p>
 * <ul>
 *   <li>configurable through an XML-based configuration file</li>
 *   <li>assembles a set of view tools (the toolbox) on request</li>
 *   <li>handles different tool scopes (request, session, application)</li>
 *   <li>supports any class with a public constructor without parameters
 *     to be used as a view tool</li>
 *   <li>supports adding primitive data values to the context(String,Number,Boolean)</li>
 * </ul>
 *
 *
 * <p><strong>Configuration</strong></p>
 * <p>The toolbox manager is configured through an XML-based configuration
 * file. The configuration file is passed to the {@link #load(java.io.InputStream input)}
 * method. The format is shown in the following example:</p>
 * <pre>
 * &lt;?xml version="1.0"?&gt;
 *
 * &lt;toolbox&gt;
 *   &lt;tool&gt;
 *      &lt;key&gt;link&lt;/key&gt;
 *      &lt;scope&gt;request&lt;/scope&gt;
 *      &lt;class&gt;org.apache.velocity.tools.view.tools.LinkTool&lt;/class&gt;
 *   &lt;/tool&gt;
 *   &lt;tool&gt;
 *      &lt;key&gt;date&lt;/key&gt;
 *      &lt;scope&gt;application&lt;/scope&gt;
 *      &lt;class&gt;org.apache.velocity.tools.generic.DateTool&lt;/class&gt;
 *   &lt;/tool&gt;
 *   &lt;data type="number"&gt;
 *      &lt;key&gt;luckynumber&lt;/key&gt;
 *      &lt;value&gt;1.37&lt;/value&gt;
 *   &lt;/data&gt;
 *   &lt;data type="string"&gt;
 *      &lt;key&gt;greeting&lt;/key&gt;
 *      &lt;value&gt;Hello World!&lt;/value&gt;
 *   &lt;/data&gt;
 *   &lt;xhtml&gt;true&lt;/xhtml&gt;
 * &lt;/toolbox&gt;
 * </pre>
 * <p>The recommended location for the configuration file is the WEB-INF directory of the
 * web application.</p>
 *
 * @author <a href="mailto:sidler@teamup.com">Gabriel Sidler</a>
 * @author Nathan Bubna
 * @author <a href="mailto:geirm@apache.org">Geir Magnusson Jr.</a>
 * @author <a href="mailto:henning@schmiedehausen.org">Henning P. Schmiedehausen</a>
 * @version $Id$
 */
public class ServletToolboxManager extends XMLToolboxManager
{

    // --------------------------------------------------- Properties ---------

    public static final String SESSION_TOOLS_KEY =
        ServletToolboxManager.class.getName() + ":session-tools";

    protected static final Log LOG = LogFactory.getLog(ServletToolboxManager.class);

    private ServletContext servletContext;
    private Map appTools;
    private ArrayList sessionToolInfo;
    private ArrayList requestToolInfo;
    private boolean createSession;

    private static HashMap managersMap = new HashMap();
    private static RuleSet servletRuleSet = new ServletToolboxRuleSet();


    // --------------------------------------------------- Constructor --------

    /**
     * Use getInstance(ServletContext,String) instead
     * to ensure there is exactly one ServletToolboxManager
     * per xml toolbox configuration file.
     */
    protected ServletToolboxManager(ServletContext servletContext)
    {
        this.servletContext = servletContext;
        appTools = new HashMap();
        sessionToolInfo = new ArrayList();
        requestToolInfo = new ArrayList();
        createSession = true;
    }


    // -------------------------------------------- Public Methods ------------

    /**
     * ServletToolboxManager factory method.
     * This method will ensure there is exactly one ServletToolboxManager
     * per xml toolbox configuration file.
     */
    public static synchronized ServletToolboxManager getInstance(ServletContext servletContext,
                                                                 String toolboxFile)
    {
        // little fix up
        if (!toolboxFile.startsWith("/"))
        {
            toolboxFile = "/" + toolboxFile;
        }

        // get the unique key for this toolbox file in this servlet context
        String uniqueKey = servletContext.hashCode() + ':' + toolboxFile;

        // check if an instance already exists
        ServletToolboxManager toolboxManager =
            (ServletToolboxManager)managersMap.get(uniqueKey);

        if (toolboxManager == null)
        {
            // if not, build one
            InputStream is = null;
            try
            {
                // get the bits
                is = servletContext.getResourceAsStream(toolboxFile);

                if (is != null)
                {
                    LOG.info("Using config file '" + toolboxFile + "'");

                    toolboxManager = new ServletToolboxManager(servletContext);
                    toolboxManager.load(is);

                    // remember it
                    managersMap.put(uniqueKey, toolboxManager);

                    LOG.debug("Toolbox setup complete.");
                }
                else
                {
                    LOG.debug("No toolbox was found at '" + toolboxFile + "'");
                }
            }
            catch(Exception e)
            {
                LOG.error("Problem loading toolbox '" + toolboxFile + "'", e);
            }
            finally
            {
                try
                {
                    if (is != null)
                    {
                        is.close();
                    }
                }
                catch(Exception ee) {}
            }
        }
        return toolboxManager;
    }


    /**
     * <p>Sets whether or not to create a new session when none exists for the
     * current request and session-scoped tools have been defined for this
     * toolbox.</p>
     *
     * <p>If true, then a call to {@link #getToolbox(Object)} will
     * create a new session if none currently exists for this request and
     * the toolbox has one or more session-scoped tools designed.</p>
     *
     * <p>If false, then a call to getToolbox(Object) will never
     * create a new session for the current request.
     * This effectively means that no session-scoped tools will be added to
     * the ToolboxContext for a request that does not have a session object.
     * </p>
     *
     * The default value is true.
     */
    public void setCreateSession(boolean b)
    {
        createSession = b;
        LOG.debug("create-session is set to " + b);
    }


    /**
     * <p>Sets an application attribute to tell velocimacros and tools
     * (especially the LinkTool) whether they should output XHTML or HTML.</p>
     *
     * @see ViewContext#XHTML
     * @since VelocityTools 1.1
     */
    public void setXhtml(Boolean value)
    {
        servletContext.setAttribute(ViewContext.XHTML, value);
        LOG.info(ViewContext.XHTML + " is set to " + value);
    }


    // ------------------------------ XMLToolboxManager Overrides -------------

    /**
     * <p>Retrieves the rule set Digester should use to parse and load
     * the toolbox for this manager.</p>
     *
     * <p>The DTD corresponding to the ServletToolboxRuleSet is:
     * <pre>
     *  &lt;?xml version="1.0"?&gt;
     *  &lt;!ELEMENT toolbox (create-session?,xhtml?,tool*,data*,#PCDATA)&gt;
     *  &lt;!ELEMENT create-session (#CDATA)&gt;
     *  &lt;!ELEMENT xhtml          (#CDATA)&gt;
     *  &lt;!ELEMENT tool           (key,scope?,class,parameter*,#PCDATA)&gt;
     *  &lt;!ELEMENT data           (key,value)&gt;
     *      &lt;!ATTLIST data type (string|number|boolean) "string"&gt;
     *  &lt;!ELEMENT key            (#CDATA)&gt;
     *  &lt;!ELEMENT scope          (#CDATA)&gt;
     *  &lt;!ELEMENT class          (#CDATA)&gt;
     *  &lt;!ELEMENT parameter (EMPTY)&gt;
     *      &lt;!ATTLIST parameter name CDATA #REQUIRED&gt;
     *      &lt;!ATTLIST parameter value CDATA #REQUIRED&gt;
     *  &lt;!ELEMENT value          (#CDATA)&gt;
     * </pre></p>
     *
     * @since VelocityTools 1.1
     */
    protected RuleSet getRuleSet()
    {
        return servletRuleSet;
    }

    /**
     * Ensures that application-scoped tools do not have request path
     * restrictions set for them, as those will not be enforced.
     *
     * @param info a ToolInfo object
     * @return true if the ToolInfo is valid
     * @since VelocityTools 1.3
     */
    protected boolean validateToolInfo(ToolInfo info)
    {
        if (!super.validateToolInfo(info))
        {
            return false;
        }
        if (info instanceof ServletToolInfo)
        {
            ServletToolInfo sti = (ServletToolInfo)info;
            if (sti.getRequestPath() != null &&
                !ViewContext.REQUEST.equalsIgnoreCase(sti.getScope()))
            {
                LOG.error(sti.getKey() + " must be a request-scoped tool to have a request path restriction!");
                return false;
            }
        }
        return true;
    }


    /**
     * Overrides XMLToolboxManager to separate tools by scope.
     * For this to work, we obviously override getToolbox(Object) as well.
     */
    public void addTool(ToolInfo info)
    {
        if (validateToolInfo(info))
        {
            if (info instanceof ServletToolInfo)
            {
                ServletToolInfo sti = (ServletToolInfo)info;

                if (ViewContext.REQUEST.equalsIgnoreCase(sti.getScope()))
                {
                    requestToolInfo.add(sti);
                    return;
                }
                else if (ViewContext.SESSION.equalsIgnoreCase(sti.getScope()))
                {
                    sessionToolInfo.add(sti);
                    return;
                }
                else if (ViewContext.APPLICATION.equalsIgnoreCase(sti.getScope()))
                {
                    /* add application scoped tools to appTools and
                     * initialize them with the ServletContext */
                    appTools.put(sti.getKey(), sti.getInstance(servletContext));
                    return;
                }
                else
                {
                    LOG.warn("Unknown scope '" + sti.getScope() + "' - " +
                            sti.getKey() + " will be request scoped.");

                    //default is request scope
                    requestToolInfo.add(info);
                }
            }
            else
            {
                //default is request scope
                requestToolInfo.add(info);
            }
        }
    }

    /**
     * Overrides XMLToolboxManager to put data into appTools map
     */
    public void addData(ToolInfo info)
    {
        if (validateToolInfo(info))
        {
            appTools.put(info.getKey(), info.getInstance(null));
        }
    }

    /**
     * Overrides XMLToolboxManager to handle the separate
     * scopes.
     *
     * Application scope tools were initialized when the toolbox was loaded.
     * Session scope tools are initialized once per session and stored in a
     * map in the session attributes.
     * Request scope tools are initialized on every request.
     *
     * @param initData the {@link ViewContext} for the current servlet request
     */
    public Map getToolbox(Object initData)
    {
        //we know the initData is a ViewContext
        ViewContext ctx = (ViewContext)initData;
        String requestPath = ServletUtils.getPath(ctx.getRequest());

        //create the toolbox map with the application tools in it
        Map toolbox = new HashMap(appTools);

        if (!sessionToolInfo.isEmpty())
        {
            HttpSession session = ctx.getRequest().getSession(createSession);
            if (session != null)
            {
                // allow only one thread per session at a time
                synchronized(getMutex(session))
                {
                    // get the session tools
                    Map stmap = (Map)session.getAttribute(SESSION_TOOLS_KEY);
                    if (stmap == null)
                    {
                        // init and store session tools map
                        stmap = new HashMap(sessionToolInfo.size());
                        Iterator i = sessionToolInfo.iterator();
                        while(i.hasNext())
                        {
                            ServletToolInfo sti = (ServletToolInfo)i.next();
                            stmap.put(sti.getKey(), sti.getInstance(ctx));
                        }
                        session.setAttribute(SESSION_TOOLS_KEY, stmap);
                    }
                    // add them to the toolbox
                    toolbox.putAll(stmap);
                }
            }
        }

        //add and initialize request tools
        Iterator i = requestToolInfo.iterator();
        while(i.hasNext())
        {
            ToolInfo info = (ToolInfo)i.next();
            if (info instanceof ServletToolInfo)
            {
                ServletToolInfo sti = (ServletToolInfo)info;
                if (!sti.allowsRequestPath(requestPath))
                {
                    continue;
                }
            }
            toolbox.put(info.getKey(), info.getInstance(ctx));
        }

        return toolbox;
    }


    /**
     * Returns a mutex (lock object) unique to the specified session
     * to allow for reliable synchronization on the session.
     */
    protected Object getMutex(HttpSession session)
    {
        // yes, this uses double-checked locking, but it is safe here
        // since partial initialization of the lock is not an issue
        Object lock = session.getAttribute("session.mutex");
        if (lock == null)
        {
            // one thread per toolbox manager at a time
            synchronized(this)
            {
                // in case another thread already came thru
                lock = session.getAttribute("session.mutex");
                if (lock == null)
                {
                    // use a Boolean because it is serializable and small
                    lock = new Boolean(true);
                    session.setAttribute("session.mutex", lock);
                }
            }
        }
        return lock;
    }

}
