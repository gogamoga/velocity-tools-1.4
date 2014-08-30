package org.apache.velocity.tools.test.blackbox;

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

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.tools.view.tools.LinkTool;
import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.context.ChainedContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

/**
 * <p>LinkTool tests.</p>
 *
 * @author Christopher Schultz
 * @version $Id$
 */
public class LinkToolTests
{
    public @Test void testAddAllParameters()
    {
        HashMap params = new HashMap();
        params.put("a", "b");
        InvocationHandler handler = new ServletAdaptor("/test", params);
        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addQueryData("foo", "bar")
            .addQueryData("bar", "baz")
            .addAllParameters()
            .toString();

        Assert.assertEquals("/test/target?foo=bar&amp;bar=baz&amp;a=b", url);
    }

    public @Test void testAddMultiValueParameters()
    {
        HashMap params = new HashMap();
        params.put("a", new String[] { "a", "b", "c" });
        InvocationHandler handler = new ServletAdaptor("/test", params);
        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addQueryData("foo", "bar")
            .addQueryData("bar", "baz")
            .addAllParameters()
            .toString();

        Assert.assertEquals("/test/target?foo=bar&amp;bar=baz&amp;a=a&amp;a=b&amp;a=c", url);
    }

    public @Test void testAddIgnoreParameters()
    {
        HashMap params = new HashMap();
        params.put("a", "b");
        params.put("b", "c");
        InvocationHandler handler = new ServletAdaptor("/test", params);

        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addQueryData("foo", "bar")
            .addQueryData("bar", "baz")
            .addIgnore("b")
            .addAllParameters()
            .toString();

        Assert.assertEquals("/test/target?foo=bar&amp;bar=baz&amp;a=b", url);
    }

    public @Test void testAddAllParametersFirst()
    {
        HashMap params = new HashMap();
        params.put("a", "b");
        InvocationHandler handler = new ServletAdaptor("/test", params);
        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addAllParameters()
            .addQueryData("foo", "bar")
            .addQueryData("bar", "baz")
            .toString();

        Assert.assertEquals("/test/target?a=b&amp;foo=bar&amp;bar=baz", url);
    }

    public @Test void testAddAdditionalValue()
    {
        HashMap params = new HashMap();
        params.put("a", "b");
        InvocationHandler handler = new ServletAdaptor("/test", params);

        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);
        link.configure(Collections.singletonMap(LinkTool.AUTO_IGNORE_PARAMETERS_KEY, Boolean.FALSE));

        String url = link.setRelative("/target")
            .addQueryData("a", "c")
            .addAllParameters()
            .toString();

        Assert.assertEquals("/test/target?a=c&amp;a=b", url);
    }

    public @Test void testAddAdditionalValueAfter()
    {
        HashMap params = new HashMap();
        params.put("a", "b");
        InvocationHandler handler = new ServletAdaptor("/test", params);

        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);
        link.configure(Collections.singletonMap(LinkTool.AUTO_IGNORE_PARAMETERS_KEY, Boolean.FALSE));

        String url = link.setRelative("/target")
            .addAllParameters()
            .addQueryData("a", "c")
            .toString();

        Assert.assertEquals("/test/target?a=b&amp;a=c", url);
    }

    public @Test void testAutoIgnore()
    {
        HashMap params = new HashMap();
        params.put("a", "b");
        InvocationHandler handler = new ServletAdaptor("/test", params);

        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addQueryData("a", "c")
            .toString();

        Assert.assertEquals("/test/target?a=c", url);
    }

    public @Test void testAutoIgnoreMultiple()
    {
        HashMap params = new HashMap();
        params.put("a", new String[] { "a", "b", "c" } );
        InvocationHandler handler = new ServletAdaptor("/test", params);

        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addQueryData("a", "d")
            .addAllParameters()
            .toString();

        Assert.assertEquals("/test/target?a=d", url);
    }

    public @Test void testNoIgnoreMultiple_WrongOrder()
    {
        HashMap params = new HashMap();
        params.put("a", new String[] { "a", "b", "c" } );
        InvocationHandler handler = new ServletAdaptor("/test", params);

        ViewContext vc = createViewContext(handler);

        LinkTool link = new LinkTool();
        link.init(vc);

        String url = link.setRelative("/target")
            .addAllParameters()
            .addQueryData("a", "d")
            .toString();

        Assert.assertEquals("/test/target?a=a&amp;a=b&amp;a=c&amp;a=d", url);
    }

    private ViewContext createViewContext(InvocationHandler handler)
    {
        Object proxy
            = Proxy.newProxyInstance(this.getClass().getClassLoader(),
                                     new Class[] { HttpServletRequest.class,
                                                   HttpServletResponse.class,
                                                   ServletContext.class },
                                     handler);

        HttpServletRequest request = (HttpServletRequest)proxy;
        HttpServletResponse response = (HttpServletResponse)proxy;
        ServletContext application = (ServletContext)proxy;

        return new ChainedContext(null, // Velocity
                                  request,
                                  response,
                                  application
                                  );
    }
}
