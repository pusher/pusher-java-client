package com.pusher.client;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class PusherOptionsTest {

    private static final String API_KEY = "4PI_K3Y";

    private PusherOptions pusherOptions;
    private @Mock Authorizer mockAuthorizer;

    @Before
    public void setUp() {
        pusherOptions = new PusherOptions();
    }

    @Test
    public void testEncryptedInitializedAsTrue() {
        assert pusherOptions.isUseTLS();
    }

    @Test
    public void testUseTLSInitializedAsTrue() {
        assert pusherOptions.isUseTLS();
    }

    @Test
    public void testAuthorizerIsInitiallyNull() {
        assertNull(pusherOptions.getAuthorizer());
    }

    @Test
    public void testAuthorizerCanBeSet() {
        pusherOptions.setAuthorizer(mockAuthorizer);
        assertSame(mockAuthorizer, pusherOptions.getAuthorizer());
    }

    @Test
    public void testEncryptedCanBeSetToTrue() {
        pusherOptions.setUseTLS(true);
        assertTrue(pusherOptions.isUseTLS());
    }

    @Test
    public void testUseTLSCanBeSetToTrue() {
        pusherOptions.setUseTLS(true);
        assertTrue(pusherOptions.isUseTLS());
    }

    @Test
    public void testSetAuthorizerReturnsSelf() {
        assertSame(pusherOptions, pusherOptions.setAuthorizer(mockAuthorizer));
    }

    @Test
    public void testSetEncryptedReturnsSelf() {
        assertSame(pusherOptions, pusherOptions.setUseTLS(true));
    }

    @Test
    public void testSetUseTLSReturnsSelf() {
        assertSame(pusherOptions, pusherOptions.setUseTLS(true));
    }

    @Test
    public void testDefaultURL() {
        assertEquals(pusherOptions.buildUrl(API_KEY), "wss://ws.pusherapp.com:443/app/" + API_KEY
                + "?client=java-client&protocol=5&version=" + PusherOptions.LIB_VERSION);
    }

    @Test
    public void testNonSSLURLIsCorrect() {
        pusherOptions.setUseTLS(false);
        assertEquals(pusherOptions.buildUrl(API_KEY), "ws://ws.pusherapp.com:80/app/" + API_KEY
                + "?client=java-client&protocol=5&version=" + PusherOptions.LIB_VERSION);
    }

    @Test
    public void testClusterSetURLIsCorrect() {
        pusherOptions.setCluster("eu");
        assertEquals(pusherOptions.buildUrl(API_KEY), "wss://ws-eu.pusher.com:443/app/" + API_KEY
                + "?client=java-client&protocol=5&version=" + PusherOptions.LIB_VERSION);
    }

    @Test
    public void testClusterSetNonSSLURLIsCorrect() {
        pusherOptions.setCluster("eu").setUseTLS(false);
        assertEquals(pusherOptions.buildUrl(API_KEY), "ws://ws-eu.pusher.com:80/app/" + API_KEY
                + "?client=java-client&protocol=5&version=" + PusherOptions.LIB_VERSION);
    }

    @Test
    public void testCustomHostAndPortURLIsCorrect() {
        pusherOptions.setHost("subdomain.example.com").setWsPort(8080).setWssPort(8181);
        assertEquals(pusherOptions.buildUrl(API_KEY), "wss://subdomain.example.com:8181/app/" + API_KEY
                + "?client=java-client&protocol=5&version=" + PusherOptions.LIB_VERSION);
    }

    @Test
    public void testCustomHostAndPortNonSSLURLIsCorrect() {
        pusherOptions.setHost("subdomain.example.com").setWsPort(8080).setWssPort(8181).setUseTLS(false);
        assertEquals(pusherOptions.buildUrl(API_KEY), "ws://subdomain.example.com:8080/app/" + API_KEY
                + "?client=java-client&protocol=5&version=" + PusherOptions.LIB_VERSION);
    }

    @Test
    public void testSetProxy(){
        Proxy newProxy = new Proxy( Proxy.Type.HTTP, new InetSocketAddress( "proxyaddress", 80 ) );
        pusherOptions.setProxy(newProxy);
        assertEquals(pusherOptions.getProxy(), newProxy);
    }

    @Test
    public void testGetProxyReturnDefaultProxy(){
        assertEquals(pusherOptions.getProxy(), Proxy.NO_PROXY);
    }

}
