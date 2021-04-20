package com.pusher.client.util;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;

/**
 * Used to authenticate a {@link com.pusher.client.channel.PrivateChannel
 * private} or {@link com.pusher.client.channel.PresenceChannel presence}
 * channel subscription.
 *
 * <p>
 * Makes an HTTP request to a defined HTTP endpoint. Expects an authentication
 * token to be returned.
 * </p>
 *
 * <p>
 * For more information see the <a
 * href="http://pusher.com/docs/authenticating_users">Authenticating Users
 * documentation</a>.
 */
public class HttpAuthorizer implements Authorizer {

    private final URL endPoint;
    private Map<String, String> mHeaders = new HashMap<String, String>();
    private ConnectionFactory mConnectionFactory = null;

    /**
     * Creates a new authorizer.
     *
     * @param endPoint
     *            The endpoint to be called when authenticating.
     */
    public HttpAuthorizer(final String endPoint) {
        try {
            this.endPoint = new URL(endPoint);
            this.mConnectionFactory = new UrlEncodedConnectionFactory();
        }
        catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Could not parse authentication end point into a valid URL", e);
        }
    }

    /**
     * Creates a new authorizer.
     *
     * @param endPoint The endpoint to be called when authenticating.
     * @param connectionFactory a custom connection factory to be used for building the connection
     */
    public HttpAuthorizer(final String endPoint, final ConnectionFactory connectionFactory) {
        try {
            this.endPoint = new URL(endPoint);
            this.mConnectionFactory = connectionFactory;
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Could not parse authentication end point into a valid URL", e);
        }
    }

    /**
     * Set additional headers to be sent as part of the request.
     *
     * @param headers A map of headers
     */
    public void setHeaders(final Map<String, String> headers) {
        mHeaders = headers;
    }

    /**
     * Identifies if the HTTP request will be sent over HTTPS.
     * @return true if the endpoint protocol is 'https'
     */
    public Boolean isSSL() {
        return endPoint.getProtocol().equals("https");
    }

    @Override
    public String authorize(final String channelName, final String socketId) throws AuthorizationFailureException {
        try {
            mConnectionFactory.setChannelName(channelName);
            mConnectionFactory.setSocketId(socketId);
            String body = mConnectionFactory.getBody();

            final HashMap<String, String> defaultHeaders = new HashMap<String, String>();
            defaultHeaders.put("Content-Type", mConnectionFactory.getContentType());
            defaultHeaders.put("charset", mConnectionFactory.getCharset());

            HttpURLConnection connection;
            if (isSSL()) {
                connection = (HttpsURLConnection)endPoint.openConnection();
            }
            else {
                connection = (HttpURLConnection)endPoint.openConnection();
            }
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");

            // Add in the user defined headers
            defaultHeaders.putAll(mHeaders);
            // Add in the Content-Length, so it can't be overwritten by mHeaders
            defaultHeaders.put("Content-Length","" + Integer.toString(body.getBytes().length));

            for (final String headerName : defaultHeaders.keySet()) {
                final String headerValue = defaultHeaders.get(headerName);
                connection.setRequestProperty(headerName, headerValue);
            }

            connection.setUseCaches(false);

            // Send request
            final DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(body);
            wr.flush();
            wr.close();

            // Read response
            final InputStream is = getResponseInputStream(connection);
            final BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            final StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
            }
            rd.close();

            final int responseHttpStatus = connection.getResponseCode();
            if (responseHttpStatus != 200 && responseHttpStatus != 201) {
                throw new AuthorizationFailureException(response.toString());
            }

            return response.toString();

        }
        catch (final IOException e) {
            throw new AuthorizationFailureException(e);
        }
    }

    private InputStream getResponseInputStream(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() >= HTTP_BAD_REQUEST) {
            return connection.getErrorStream();
        }
        return connection.getInputStream();
    }
}
