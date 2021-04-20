package com.pusher.client.util;

import com.pusher.client.AuthorizationFailureException;
import junit.framework.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;

import static com.google.common.truth.Truth.assertThat;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class HttpAuthorizerTest {

    private static ClientAndServer mockServer;

    @BeforeClass
    public static void startMockServer() {
        mockServer = startClientAndServer(1080);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructWithMalformedURLThrowsRuntimeException() {
        new HttpAuthorizer("bad url");
    }

    @Test
    public void testHTTPURLIsIdentfiedAsSSL() {
        final HttpAuthorizer auth = new HttpAuthorizer("http://example.com/auth");
        Assert.assertFalse(auth.isSSL());
    }

    @Test
    public void testHTTPSURLIsIdentfiedAsSSL() {
        final HttpAuthorizer auth = new HttpAuthorizer("https://example.com/auth");
        Assert.assertTrue(auth.isSSL());
    }

    @Test(expected = AuthorizationFailureException.class)
    public void testNon200ResponseThrowsAuthorizationFailureException() {
        final HttpAuthorizer auth = new HttpAuthorizer("https://127.0.0.1/no-way-this-is-a-valid-url");
        auth.authorize("private-fish", "some socket id");
    }

    @Test
    public void testResponseStatusHttpBadRequestOrAboveThrowsAuthorizationExceptionWithDetailedErrorMessage() {
        String errorResponseMessage = "No cats allowed";
        mockServer.when(request().withPath("/no-way-this-is-a-valid-url/wanna-bet"))
                .respond(response()
                        .withStatusCode(HTTP_FORBIDDEN)
                        .withBody(errorResponseMessage)
                );
        final HttpAuthorizer auth = new HttpAuthorizer("http://127.0.0.1:1080/no-way-this-is-a-valid-url/wanna-bet");

        try {
            auth.authorize("private-dog", "barking");
        } catch (AuthorizationFailureException e) {
                assertThat(e).hasMessageThat().isEqualTo(errorResponseMessage);
        }
    }

    @Test
    public void testResponseStatusLowerThanHttpBadRequestReturnsSuccessResponse() {
        String expectedResponseMessage = "Keep on purring";
        mockServer.when(request().withPath("/no-way-this-is-a-valid-url/wanna-bet"))
                .respond(response()
                        .withStatusCode(HTTP_OK)
                        .withBody(expectedResponseMessage)
                );
        final HttpAuthorizer auth = new HttpAuthorizer("http://127.0.0.1:1080/no-way-this-is-a-valid-url/wanna-bet");

        String responseMessage = auth.authorize("private-cat", "purring");
        assertThat(responseMessage).isEqualTo(expectedResponseMessage);
    }

    @AfterClass
    public static void stopMockServer() {
        mockServer.stop();
    }
}
