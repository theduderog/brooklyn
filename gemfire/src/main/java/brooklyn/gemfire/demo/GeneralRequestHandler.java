package brooklyn.gemfire.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class GeneralRequestHandler implements HttpHandler {

    private static final String GATEWAY_ADDED_MESSAGE       = "Added gateway: %s";
    private static final String GATEWAY_REMOVED_MESSAGE     = "Removed gateway: %s";
    private static final String GATEWAY_NOT_REMOVED_MESSAGE = "Gateway %s not removed";
    
    private static final String REGION_ADDED_MESSAGE       = "Added region: %s";
    private static final String REGION_NOT_ADDED_MESSAGE   = "Failed to add region: %s";
    private static final String REGION_REMOVED_MESSAGE     = "Removed region: %s";
    private static final String REGION_NOT_REMOVED_MESSAGE = "Region %s not removed";

    private static final String ID_KEY = "id";
    private static final String ENDPOINT_ID_KEY = "endpointId";
    private static final String PORT_KEY = "port";
    private static final String HOST_KEY = "host";
    private static final String PATH_KEY = "path";

    // For GatewayQueueAttributes - see com.gemstone.gemfire.cache.util.GatewayQueueAttributes
    private static final String DISK_STORE_NAME_KEY = "diskStoreName"; //String
    private static final String MAX_QUEUE_MEMORY_KEY = "maximumQueueMemory"; //int
    private static final String BATCH_SIZE_KEY = "batchSize"; //int
    private static final String BATCH_TIME_INTERVAL_KEY = "batchTimeInterval"; //int
    private static final String BATCH_CONFLATION_KEY = "batchConflation"; //boolean
    private static final String ENABLE_PERSISTENCE_KEY = "enablePersistence"; // boolean
    private static final String ALERT_THRESHOLD_KEY = "alertThreshold"; //int

    private final GatewayManager gatewayManager;
    private final RegionManager regionManager;
    
    public GeneralRequestHandler(GatewayManager gatewayManager, RegionManager regionManager) {
        this.gatewayManager = gatewayManager;
        this.regionManager = regionManager;
    }

    private static final String USAGE = "Example usage:\n" +
            "GET http://host:port/gateway/add?id=US&endpointId=US-1&host=localhost&port=33333 \n" +
            "GET http://host:port/region/remove?name=trades";

    private static final String URL_GATEWAY = "/gateway/";
    private static final String URL_REGION = "/region/";

    public void handle(HttpExchange httpExchange) throws IOException {
        URI uri = httpExchange.getRequestURI();
        String path = uri.getPath();
        try {
            if (path.equals("/")) {
                handleRoot(httpExchange);
            } else if (path.startsWith(URL_GATEWAY)) {
                handleGateway(httpExchange);
            } else if (path.startsWith(URL_REGION)) {
                handleRegion(httpExchange);
            } else {
                handleUnknown(httpExchange);
            }
        } catch (Throwable t) {
            sendResponse(httpExchange, 500, t.getMessage());
            t.printStackTrace();
            throw new IOException("error on path: " + path, t);
        }
    }

    private void handleRoot(HttpExchange httpExchange) throws IOException {
        sendResponse(httpExchange, 200, USAGE);
    }

    private void handleRegion(HttpExchange httpExchange) throws IOException {
        String subpath = httpExchange.getRequestURI().getPath().substring(URL_REGION.length());
        if (subpath.equals("add")) {
            handleAddRegion(httpExchange);
        } else if (subpath.equals("remove")) {
            handleRemoveRegion(httpExchange);
        } else if (subpath.equals("list")) {
            handleListRegions(httpExchange);
        } else {
            handleUnknown(httpExchange);
        }
    }

    private void handleGateway(HttpExchange httpExchange) throws IOException {
        String subpath = httpExchange.getRequestURI().getPath().substring(URL_GATEWAY.length());
        if (subpath.equals("add")) {
            handleAddGateway(httpExchange);
        } else if (subpath.equals("remove")) {
            handleRemoveGateway(httpExchange);
        } else {
            handleUnknown(httpExchange);
        }
    }
    
    private void handleAddGateway(HttpExchange httpExchange) throws IOException {
        String query = httpExchange.getRequestURI().getRawQuery();
        Map<String,Object> parameters = new ParameterParser().parse(query);

        String id = (String) parameters.get(ID_KEY);
        String endpointId = (String) parameters.get(ENDPOINT_ID_KEY);
        String host = (String) parameters.get(HOST_KEY);
        int port = Integer.parseInt((String) parameters.get(PORT_KEY));
        
        GatewayQueueAttributes attributes = getQueueAttributes(parameters);

        gatewayManager.addGateway(id, endpointId, host, port, attributes);

        sendResponse(httpExchange, 200, String.format(GATEWAY_ADDED_MESSAGE, id));
    }
    
    private void handleRemoveGateway(HttpExchange httpExchange) throws IOException {
        String query = httpExchange.getRequestURI().getRawQuery();
        Map<String,Object> parameters = new ParameterParser().parse(query);

        String id = (String)parameters.get(ID_KEY);
        boolean result = gatewayManager.removeGateway(id);

        String message = result ? GATEWAY_REMOVED_MESSAGE : GATEWAY_NOT_REMOVED_MESSAGE;
        sendResponse(httpExchange, 200, String.format(message, id));
    }

    private void handleAddRegion(HttpExchange httpExchange) throws IOException {
        String query = httpExchange.getRequestURI().getRawQuery();
        Map<String,Object> parameters = new ParameterParser().parse(query);
        String path = (String) parameters.get(PATH_KEY);

        boolean result = false;
        int responseCode = 200;
        try {
            result = regionManager.addRegion(path, true);
        } catch (Exception e) {
            responseCode = 500;
        }
        String message = result ? REGION_ADDED_MESSAGE : REGION_NOT_ADDED_MESSAGE;
        sendResponse(httpExchange, responseCode, String.format(message, path));

    }
    
    private void handleRemoveRegion(HttpExchange httpExchange) throws IOException {
        String query = httpExchange.getRequestURI().getRawQuery();
        Map<String,Object> parameters = new ParameterParser().parse(query);
        String path = (String) parameters.get(PATH_KEY);

        boolean result = false;
        int responseCode = 200;
        try {
            result = regionManager.removeRegion(path);
        } catch (Exception e) {
            responseCode = 500;
        }
        String message = result ? REGION_REMOVED_MESSAGE : REGION_NOT_REMOVED_MESSAGE;
        sendResponse(httpExchange, responseCode, String.format(message, path));
    }
    
    private void handleListRegions(HttpExchange httpExchange) throws IOException {
    	StringBuilder sb = new StringBuilder();
    	for (String s : regionManager.regionList()) {
    		sb.append(s).append(",");
    	}
    	String out = sb.length() > 0 ? sb.substring(0, sb.length()-1) : "";
    	sendResponse(httpExchange, 200, out);
    }
    
    private void handleUnknown(HttpExchange httpExchange) throws IOException {
        sendResponse(httpExchange, 404, USAGE);
    }

    private void sendResponse(HttpExchange httpExchange, int code, String message) throws IOException {
        Headers responseHeaders = httpExchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "text/plain");
        httpExchange.sendResponseHeaders(code, 0);
        OutputStream response = httpExchange.getResponseBody();
        response.write((message+"\n").getBytes());
        response.close();
    }

    private GatewayQueueAttributes getQueueAttributes(Map<String,Object> parameters) {
        GatewayQueueAttributes result = new GatewayQueueAttributes();

        String diskStoreName = (String)parameters.get(DISK_STORE_NAME_KEY);
        if (diskStoreName != null) result.setDiskStoreName(diskStoreName);

        Integer maxQueueMemory = getAsInteger(MAX_QUEUE_MEMORY_KEY, parameters);
        if (maxQueueMemory!= null) result.setMaximumQueueMemory(maxQueueMemory);

        Integer batchSize = getAsInteger(BATCH_SIZE_KEY, parameters);
        if (batchSize!=null) result.setBatchSize(batchSize);

        Integer batchTimeInterval = getAsInteger(BATCH_TIME_INTERVAL_KEY, parameters);
        if (batchTimeInterval!=null) result.setBatchTimeInterval(batchTimeInterval);

        Integer alertThreshHold = getAsInteger(ALERT_THRESHOLD_KEY, parameters);
        if (alertThreshHold!=null) result.setAlertThreshold(alertThreshHold);

        Boolean batchConflation = getAsBoolean(BATCH_CONFLATION_KEY,parameters);
        if (batchConflation!=null) result.setBatchConflation(batchConflation);

        Boolean enablePersistence = getAsBoolean(ENABLE_PERSISTENCE_KEY,parameters);
        if(enablePersistence!=null) result.setEnablePersistence(enablePersistence);

        return result;
    }

    private Integer getAsInteger(String key, Map<String, Object> params) {
        String asString = (String)params.get(key);
        return (asString == null) ? null : Integer.parseInt(asString);
    }

    private Boolean getAsBoolean(String key, Map<String,Object> params) {
        String asString = (String)params.get(key);
        return (asString == null) ? null : Boolean.parseBoolean(asString);
    }
}