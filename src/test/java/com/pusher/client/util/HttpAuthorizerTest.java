package com.pusher.client.util;

import junit.framework.Assert;

import org.junit.Test;

import com.pusher.client.AuthorizationFailureException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpAuthorizerTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructWithMalformedURLThrowsRuntimeException() {
        new HttpAuthorizer("bad url");
    }

    @Test
    public void testHTTPURLIsIdentifiedAsSSL() {
        final HttpAuthorizer auth = new HttpAuthorizer("http://example.com/auth");
        assertFalse(auth.isSSL());
    }

    @Test
    public void testHTTPSURLIsIdentifiedAsSSL() {
        final HttpAuthorizer auth = new HttpAuthorizer("https://example.com/auth");
        assertTrue(auth.isSSL());
    }

    @Test(expected = AuthorizationFailureException.class)
    public void testNon200ResponseThrowsAuthorizationFailureException() {
        final HttpAuthorizer auth = new HttpAuthorizer("https://127.0.0.1/no-way-this-is-a-valid-url");
        auth.authorize("private-fish", "some socket id");
    }
}
