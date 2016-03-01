package com.github.ambry.rest;

import com.github.ambry.utils.UtilsTest;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.Random;
import junit.framework.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class HealthCheckHandlerTest {
  private final RestServerState restServerState;
  private final String healthCheckUri = "/healthCheck";
  private final String goodStr = "GOOD";
  private final String badStr = "BAD";

  public HealthCheckHandlerTest() {
    this.restServerState = new RestServerState(healthCheckUri);
  }

  /**
   * Tests for the common case request handling flow for health check requests.
   * @throws java.io.IOException
   */
  @Test
  public void requestHandleWithHealthCheckRequestTest()
      throws IOException {
    // test with keep alive
    testHealthCheckRequest(HttpMethod.GET, true, true);
    testHealthCheckRequest(HttpMethod.GET, false, true);

    // test without keep alive
    testHealthCheckRequest(HttpMethod.GET, true, false);
    testHealthCheckRequest(HttpMethod.GET, false, false);
  }

  /**
   * Tests non health check requests handling
   * @throws java.io.IOException
   */
  @Test
  public void requestHandleWithNonHealthCheckRequestTest()
      throws IOException {
    testNonHealthCheckRequest(HttpMethod.POST, "POST");
    testNonHealthCheckRequest(HttpMethod.GET, "GET");
    testNonHealthCheckRequest(HttpMethod.DELETE, "DELETE");
  }

  /**
   * Does a test to see that a health check request results in expected response from the health check handler
   * @param httpMethod the {@link HttpMethod} for the request.
   * @param keepAlive true if keep alive has to be set in the request, false otherwise
   * @throws IOException
   */
  private void testHealthCheckRequest(HttpMethod httpMethod, boolean isServiceUp, boolean keepAlive)
      throws IOException {
    EmbeddedChannel channel = createChannel();
    for (int i = 0; i < 2; i++) {
      if (isServiceUp) {
        restServerState.markServiceUp();
      }
      HttpRequest request = RestTestUtils.createRequest(httpMethod, healthCheckUri, null);
      HttpHeaders.setKeepAlive(request, keepAlive);
      FullHttpResponse response = sendRequestAndGetResponse(channel, request);
      HttpResponseStatus httpResponseStatus =
          (isServiceUp) ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE;
      assertEquals("Unexpected response status", httpResponseStatus, response.getStatus());
      String expectedStr = (isServiceUp) ? goodStr : badStr;
      assertEquals("Unexpected content", expectedStr, RestTestUtils.getContentString(response));
      restServerState.markServiceDown();
      if (keepAlive && isServiceUp) {
        Assert.assertTrue("Channel should not be closed ", channel.isOpen());
      } else {
        Assert.assertFalse("Channel should have been closed by now ", channel.isOpen());
        channel = createChannel();
      }
    }
    channel.close();
  }

  /**
   * Does a test to see that a non health check request results in expected responses
   * @param httpMethod the {@link HttpMethod} for the request.
   * @param uri Uri to be used during the request
   * @throws IOException
   */
  private void testNonHealthCheckRequest(HttpMethod httpMethod, String uri)
      throws IOException {
    EmbeddedChannel channel = createChannel();
    HttpRequest request = RestTestUtils.createRequest(httpMethod, uri, null);
    FullHttpResponse response = sendRequestAndGetResponse(channel, request);
    assertEquals("Unexpected response status", HttpResponseStatus.OK, response.getStatus());
    assertEquals("Unexpected content", httpMethod.toString(), RestTestUtils.getContentString(response));
    channel.close();
  }

  /**
   * Sends request to the passed in channel and returns the response
   * @param channel to which the request has to be sent
   * @param request {@link HttpRequest} which has to be sent to the passed in channel
   * @return the HttpResponse in response to the request sent to the channel
   */
  private FullHttpResponse sendRequestAndGetResponse(EmbeddedChannel channel, HttpRequest request) {
    channel.writeInbound(request);
    channel.writeInbound(new DefaultLastHttpContent());
    FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
    return response;
  }

  // helpers
  // general

  /**
   * Creates an {@link EmbeddedChannel} that incorporates an instance of {@link HealthCheckHandler}
   * and {@link EchoMethodHandler}.
   * @return an {@link EmbeddedChannel} that incorporates an instance of {@link HealthCheckHandler}
   * and {@link EchoMethodHandler}.
   */
  private EmbeddedChannel createChannel() {
    return new EmbeddedChannel(new HealthCheckHandler(restServerState), new EchoMethodHandler());
  }
}
