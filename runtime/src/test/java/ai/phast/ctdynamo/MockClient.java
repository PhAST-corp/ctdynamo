package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dynamo client that expects exact requests and returns canned responses for each.
 */
public class MockClient implements DynamoDbClient {

    public List<?> expectedRequests;

    public List<?> cannedResponses;

    private int id = 0;

    private boolean unordered = false;

    /**
     * A client that throws an exception on any request.
     */
    public MockClient() {
        this(null, null);
    }

    /**
     * Build a client that expects a single request, and returns the canned response.
     * @param expectedRequest The expected request
     * @param cannedResponse The canned response
     */
    public MockClient(Object expectedRequest, Object cannedResponse) {
        expectedRequests = Collections.singletonList(expectedRequest);
        cannedResponses = Collections.singletonList(cannedResponse);
    }

    /**
     * Build a client that expects a series of requests, in order, and returns the canned response for each
     * @param expectedRequests The expected request
     * @param cannedResponses The canned response
     */
    public MockClient(List<?> expectedRequests, List<?> cannedResponses) {
        this.expectedRequests = expectedRequests;
        this.cannedResponses = cannedResponses;
    }

    /**
     * Build a client that expects a series of requests, in any order, and returns the canned response for each
     * @param expectedRequests The requests
     * @param cannedResponses The responses
     * @param unordered If true, then we will accept the requests in any order
     */
    public MockClient(List<?> expectedRequests, List<?> cannedResponses, boolean unordered) {
        this.expectedRequests = new ArrayList<>(expectedRequests);
        this.cannedResponses = new ArrayList<>(cannedResponses);
        this.unordered = unordered;
    }

    @Override
    public GetItemResponse getItem(GetItemRequest getItemRequest) {
        return testAndReturn(GetItemResponse.class, getItemRequest);
    }

    @Override
    public BatchGetItemResponse batchGetItem(BatchGetItemRequest batchGetItemRequest) {
        return testAndReturn(BatchGetItemResponse.class, batchGetItemRequest);
    }

    @Override
    public PutItemResponse putItem(PutItemRequest putItemRequest) {
        return testAndReturn(PutItemResponse.class, putItemRequest);
    }

    @Override
    public UpdateItemResponse updateItem(UpdateItemRequest updateItemRequest) {
        return testAndReturn(UpdateItemResponse.class, updateItemRequest);
    }


    @Override
    public BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest batchWriteItemRequest) {
        return testAndReturn(BatchWriteItemResponse.class, batchWriteItemRequest);
    }

    @Override
    public DeleteItemResponse deleteItem(DeleteItemRequest deleteItemRequest) {
        return testAndReturn(DeleteItemResponse.class, deleteItemRequest);
    }

    @Override
    public QueryResponse query(QueryRequest queryRequest) {
        return testAndReturn(QueryResponse.class, queryRequest);
    }

    @Override
    public ScanResponse scan(ScanRequest scanRequest) {
        return testAndReturn(ScanResponse.class, scanRequest);
    }

    @Override
    public TransactWriteItemsResponse transactWriteItems(TransactWriteItemsRequest request) {
        return testAndReturn(TransactWriteItemsResponse.class, request);
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {

    }

    /**
     * If any requests haven't been seen yet, thraw on exception and fail
     */
    public synchronized void assertDone() {
        if (id != expectedRequests.size()) {
            throw new RuntimeException("Not all requests were consumed; remaining = " +
                                           (unordered ? expectedRequests : expectedRequests.subList(id, expectedRequests.size())));
        }
    }

    /**
     * Test to ensure that the input is in our expected requests. Return the matching response
     * @param klass The class to cast the response to
     * @param input The object we got as a request
     * @param <T> The type of the response
     * @return The response
     * @throws RuntimeException if the canned response is actually an exception
     * @throws org.opentest4j.AssertionFailedError if the input doesn't match the expected response
     */
    private synchronized <T> T testAndReturn(Class<T> klass, Object input) {
        if (unordered) {
            for (int i = 0; i < expectedRequests.size(); ++i) {
                if (expectedRequests.get(i).equals(input)) {
                    //noinspection SuspiciousListRemoveInLoop
                    expectedRequests.remove(i);
                    var response = cannedResponses.remove(i);
                    if (response instanceof RuntimeException) {
                        throw (RuntimeException)response;
                    } else {
                        return klass.cast(response);
                    }
                }
            }
            throw new RuntimeException("Found no entry matching " + input + "; requests: " + expectedRequests);
        } else {
            Assertions.assertTrue(id < expectedRequests.size(),
                "Already processed the " + expectedRequests.size() + " expected requests, got another: " + input);
            Assertions.assertEquals(expectedRequests.get(id), input);
            var response = cannedResponses.get(id++);
            if (response instanceof RuntimeException) {
                throw (RuntimeException)response;
            } else {
                return klass.cast(response);
            }
        }
    }
}
