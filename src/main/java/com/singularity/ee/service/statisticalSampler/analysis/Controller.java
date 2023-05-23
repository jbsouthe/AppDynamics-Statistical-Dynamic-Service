package com.singularity.ee.service.statisticalSampler.analysis;

import com.singularity.ee.service.statisticalSampler.analysis.auth.AccessToken;
import com.singularity.ee.service.statisticalSampler.analysis.metric.*;
import com.singularity.ee.service.statisticalSampler.analysis.model.*;
import com.singularity.ee.service.statisticalSampler.analysis.exceptions.ControllerBadStatusException;
import com.singularity.ee.service.statisticalSampler.analysis.http.*;
import com.singularity.ee.service.statisticalSampler.analysis.util.Utility;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.codec.Charsets;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class Controller {
    private static final Logger logger = LogManager.getFormatterLogger();

    public String hostname;
    public URL url;
    private String clientId, clientSecret;
    private AccessToken accessToken = null;
    public List<Application> applications = new ArrayList<>();
    public Model controllerModel = null;
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    HttpClient client = null;
    private ResponseHandler<String> responseHandler;
    private String application, tier;

    public Controller( String urlString, String clientId, String clientSecret, String application, String tier ) throws MalformedURLException {
        if( !urlString.endsWith("/") ) urlString+="/"; //this simplifies some stuff downstream
        this.url = new URL(urlString);
        this.hostname = this.url.getHost();
        this.clientId = clientId;
        if( !this.clientId.contains("@") ) this.clientId += "@"+ this.hostname.split("\\.")[0];
        this.clientSecret = clientSecret;
        this.client = HttpClientFactory.getHttpClient();
        this.responseHandler = HttpClientFactory.getStringResponseHandler("controller");
        this.application = application;
        this.tier = tier;
        this.controllerModel = getModel();
        if( application != null ) {
            this.applications.add(controllerModel.getApplication(application));
        } else {
            this.applications.addAll(Arrays.asList(controllerModel.getApplications()));
        }
        for( Application appModel : this.applications )
            appModel.filterTiersDown(tier);
        StringBuilder stringBuilder = new StringBuilder("Application Count ");
        stringBuilder.append(applications.size()).append(" Tiers:\n");
        for( Application appModel : this.applications )
            stringBuilder.append("\t").append(appModel).append("\n");
        logger.info("Controller initialized %s", stringBuilder.toString());
    }

    public String getBearerToken() {
        if( isAccessTokenExpired() && !refreshAccessToken()) return null;
        return "Bearer "+ accessToken.access_token;
    }

    private boolean isAccessTokenExpired() {
        return( accessToken == null || accessToken.isExpired() );
    }

    private boolean refreshAccessToken() { //returns true on successful refresh, false if an error occurs
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(clientId, clientSecret);
        logger.trace("credentials configured: %s",credentials.toString());
        provider.setCredentials(AuthScope.ANY, credentials);
        logger.trace("provider configured: %s",provider.toString());
        HttpPost request = new HttpPost(url.toString()+"/controller/api/oauth/access_token");
        //request.addHeader(HttpHeaders.CONTENT_TYPE,"application/vnd.appd.cntrl+protobuf;v=1");
        ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add( new BasicNameValuePair("grant_type","client_credentials"));
        postParameters.add( new BasicNameValuePair("client_id",clientId));
        postParameters.add( new BasicNameValuePair("client_secret",clientSecret));
        try {
            request.setEntity(new UrlEncodedFormEntity(postParameters,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            logger.warn("Unsupported Encoding Exception in post parameter encoding: %s",e.getMessage());
        }

        if( HttpClientFactory.isWireTraceEnabled("controller") ){
            logger.info("Request to run: %s",request.toString());
            for( Header header : request.getAllHeaders())
                logger.info("with header: %s",header.toString());
            logger.info("post parameters: %s", postParameters);
        }

        HttpResponse response = null;
        int tries=0;
        boolean succeeded=false;
        while( !succeeded && tries < 3 ) {
            try {
                response = client.execute(request);
                succeeded=true;
                logger.trace("Response Status Line: %s", response.getStatusLine());
            } catch (IOException e) {
                logger.error("Exception in attempting to get access token, Exception: %s", e.getMessage());
                tries++;
            } catch (java.lang.IllegalStateException illegalStateException) {
                tries++;
                this.client = HttpClientFactory.getHttpClient(true);
                logger.warn("Caught exception on connection, building a new connection for retry, Exception: %s", illegalStateException.getMessage());
            }
        }
        if( !succeeded ) return false;
        HttpEntity entity = response.getEntity();
        Header encodingHeader = entity.getContentEncoding();
        Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
        String json = null;
        try {
            json = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("IOException parsing returned encoded string to json text: "+ e.getMessage());
            return false;
        }
        if( response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            logger.warn("Access Key retreival returned bad status: %s message: %s", response.getStatusLine(), json);
            return false;
        }
        this.accessToken = gson.fromJson(json, AccessToken.class); //if this doesn't work consider creating a custom instance creator
        this.accessToken.expires_at = new Date().getTime() + (accessToken.expires_in*1000); //hoping this is enough, worry is the time difference
        return true;
    }

    public MetricData[] getMetricValue(Application application, String metricName, long startTimestamp, long endTimestamp ) {
        MetricData[] metrics = null;

        int tries=0;
        boolean succeeded = false;
        while (! succeeded && tries < 3 ) {
            try {
                metrics = getMetricValue(String.format("%scontroller/rest/applications/%s/metric-data?metric-path=%s&time-range-type=BETWEEN_TIMES&start-time=%d&end-time=%d&output=JSON&rollup=true",
                        this.url, Utility.encode(application.name), Utility.encode(metricName), startTimestamp, endTimestamp)
                );
                succeeded=true;
            } catch (ControllerBadStatusException controllerBadStatusException) {
                tries++;
                logger.warn("Attempt number %d failed, status returned: %s for request %s",tries,controllerBadStatusException.getMessage(), controllerBadStatusException.urlRequestString);
            }
        }
        if( !succeeded)
            logger.warn("Gave up after %d tries, not getting %s back", tries, metricName);
        return metrics;
    }

    public MetricData[] getMetricValue( String urlString ) throws ControllerBadStatusException {
        MetricData[] metricData = null;
        if( urlString == null ) return null;
        logger.trace("metric url: %s",urlString);
        if( ! urlString.contains("output=JSON") ) urlString += "&output=JSON";
        HttpGet request = new HttpGet(urlString);
        request.addHeader(HttpHeaders.AUTHORIZATION, getBearerToken());
        if( HttpClientFactory.isWireTraceEnabled("controller") ) {
            logger.info("Wire Trace Request: '%s'",request.toString());
        }
        String json = null;
        try {
            json = client.execute(request, this.responseHandler);
        } catch (ControllerBadStatusException controllerBadStatusException) {
            controllerBadStatusException.setURL(urlString);
            throw controllerBadStatusException;
        } catch (IOException e) {
            logger.error("Exception in attempting to get url, Exception: %s", e.getMessage());
            return null;
        }
        metricData = gson.fromJson(json, MetricData[].class);
        return metricData;
    }

    public TreeNode[] getApplicationMetricFolders(Application application, String path) {
        String json = null;

        int tries=0;
        boolean succeeded=false;
        while( !succeeded && tries < 3 ) {
            try {
                if ("".equals(path)) {
                    json = getRequest(String.format("controller/rest/applications/%s/metrics?output=JSON", Utility.encode(application.name)));
                } else {
                    json = getRequest(String.format("controller/rest/applications/%s/metrics?metric-path=%s&output=JSON", Utility.encode(application.name), Utility.encode(path)));
                }
                succeeded=true;
            } catch (ControllerBadStatusException controllerBadStatusException) {
                tries++;
                logger.warn("Try %d failed for request to get app application metric folders for %s with error: %s",tries,application.name,controllerBadStatusException.getMessage());
            }
        }
        if(!succeeded) logger.warn("Failing on get of application metric folder, controller may be down");

        TreeNode[] treeNodes = null;
        try {
            treeNodes = gson.fromJson(json, TreeNode[].class);
        } catch (JsonSyntaxException jsonSyntaxException) {
            logger.warn("Error in parsing returned text, this may be a bug JSON '%s' Exception: %s",json, jsonSyntaxException.getMessage());
        }
        return treeNodes;
    }


    private String postRequest( String requestUri, String body ) throws ControllerBadStatusException {
        HttpPost request = new HttpPost(String.format("%s%s", this.url.toString(), requestUri));
        request.addHeader(HttpHeaders.AUTHORIZATION, getBearerToken());
        logger.trace("HTTP Method: %s with body: '%s'",request, body);
        if( HttpClientFactory.isWireTraceEnabled("controller") ) {
            logger.info("Wire Trace POST Request: '%s' with Body: '%s'",request.toString(), body);
        }
        String json = null;
        try {
            request.setEntity( new StringEntity(body, "UTF8"));
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-Type", "application/json");
            json = client.execute( request, this.responseHandler);
            logger.trace("Data Returned: '%s'", json);
        } catch (ControllerBadStatusException controllerBadStatusException) {
            controllerBadStatusException.setURL(request.getURI().toString());
            throw controllerBadStatusException;
        } catch (IOException e) {
            logger.warn("Exception: %s",e.getMessage());
        }
        return json;
    }

    private String getRequest( String formatOrURI, Object... args ) throws ControllerBadStatusException {
        if( args == null || args.length == 0 ) return getRequest(formatOrURI);
        return getRequest( String.format(formatOrURI,args));
    }

    private String getRequest( String uri ) throws ControllerBadStatusException {
        HttpGet request = new HttpGet(String.format("%s%s", this.url.toString(), uri));
        request.addHeader(HttpHeaders.AUTHORIZATION, getBearerToken());
        if( HttpClientFactory.isWireTraceEnabled("controller") ) {
            logger.info("Wire Trace GET Request: '%s'",request.toString());
        }
        String json = null;
        try {
            json = client.execute(request, this.responseHandler);
        } catch (ControllerBadStatusException controllerBadStatusException) {
            controllerBadStatusException.setURL(request.getURI().toString());
            throw controllerBadStatusException;
        } catch (IOException e) {
            logger.warn("Exception: %s",e.getMessage());
        }
        return json;
    }

    Map<String,Long> _applicationIdMap = null;
    public long getApplicationId( String name ) {
        logger.trace("Get Application id for %s",name);
        if( _applicationIdMap == null ) { //go get em
            initApplicationIdMap();
        }
        if( !_applicationIdMap.containsKey(name) ) return -1;
        return _applicationIdMap.get(name);
    }

    private void initApplicationIdMap() {
        try {
            String json = getRequest("controller/restui/applicationManagerUiBean/getApplicationsAllTypes?output=json");
            ApplicationListing applicationListing = gson.fromJson(json, ApplicationListing.class);
            _applicationIdMap = new HashMap<>();
            for (com.singularity.ee.service.statisticalSampler.analysis.model.Application app : applicationListing.getApplications() )
                if( app.active ) _applicationIdMap.put(app.name, app.id);
        } catch (ControllerBadStatusException controllerBadStatusException) {
            logger.warn("Giving up on getting application id, not even going to retry");
        }
    }

    public Model getModel() {
        if( this.controllerModel == null ) {
            try {
                String json = getRequest("controller/rest/applications?output=json");
                this.controllerModel = new Model(gson.fromJson(json, com.singularity.ee.service.statisticalSampler.analysis.model.Application[].class));
                for (com.singularity.ee.service.statisticalSampler.analysis.model.Application application : this.controllerModel.getApplications()) {
                    json = getRequest("controller/rest/applications/%d/tiers?output=json", application.id);
                    application.tiers = gson.fromJson(json, Tier[].class);
                    json = getRequest("controller/rest/applications/%d/nodes?output=json", application.id);
                    application.nodes = gson.fromJson(json, Node[].class);
                }
            } catch (ControllerBadStatusException controllerBadStatusException) {
                logger.warn("Giving up on getting controller model, not even going to retry");
            }
        }
        return this.controllerModel;
    }


    public Baseline[] getAllBaselines( Application application ) {
        if( application == null ) return null;
        if( application.id == -1 ) application.id = getApplicationId(application.name);
        if( application.id == -1 ) return null;
        return getAllBaselines(application.id);
    }

    public Baseline[] getAllBaselines(long applicationId ) {
        try {
            String json = getRequest("controller/restui/baselines/getAllBaselines/%d?output=json", applicationId);
            Baseline[] baselines = gson.fromJson(json, Baseline[].class);
            return baselines;
        } catch (ControllerBadStatusException controllerBadStatusException) {
            logger.warn("Error using undocumented api to pull back listing of all application baselines, application '%s'", applicationId);
        }
        return null;
    }

    public void discardToken() {
        this.accessToken=null;
    }
}
