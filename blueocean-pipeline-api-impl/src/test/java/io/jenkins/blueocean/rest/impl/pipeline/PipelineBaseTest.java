package io.jenkins.blueocean.rest.impl.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.blueocean.commons.JsonConverter;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import static io.jenkins.blueocean.auth.jwt.JwtToken.X_BLUEOCEAN_JWT;
import static org.junit.Assert.fail;

/**
 * @author Vivek Pandey
 */
public abstract class PipelineBaseTest{
    private static  final Logger LOGGER = LoggerFactory.getLogger(PipelineBaseTest.class);

    public PipelineBaseTest() {
        System.setProperty("BLUEOCEAN_FEATURE_JWT_AUTHENTICATION", "true");
        j = new JenkinsRule();
    }
    @Rule
    public JenkinsRule j;

    protected  String baseUrl;

    protected String jwtToken;

    protected String getContextPath(){
        return "blue/rest";
    }

    @Before
    public void setup() throws Exception {
        if(System.getProperty("DISABLE_HTTP_HEADER_TRACE") == null) {
            InputStream is = this.getClass().getResourceAsStream("/logging.properties");
            LogManager.getLogManager().readConfiguration(is);
        }
        this.baseUrl = j.jenkins.getRootUrl() + getContextPath();
        this.jwtToken = getJwtToken(j.jenkins);
        Unirest.setObjectMapper(new ObjectMapper() {
            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    if(value.isEmpty()){
                        value = "{}";
                    }
                    T r =  JsonConverter.om.readValue(value, valueType);
                    LOGGER.info("Response:\n"+JsonConverter.om.writeValueAsString(r));
                    return r;
                } catch (IOException e) {
                    LOGGER.info("Failed to parse JSON: "+value+". "+e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            public String writeValue(Object value) {
                try {
                    String str = JsonConverter.om.writeValueAsString(value);
                    LOGGER.info("Request:\n"+str);
                    return str;
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });

//        HttpClientParams params = new HttpClientParams();
//
//        HttpClient client = new HttpClient();
//        Unirest.setHttpClient();
    }

    protected <T> T  get(String path, Class<T> type){
        return get(path,200, type);
    }

    protected Map<String, Object> get(String path){
        return get(path,200, Map.class);
    }
    @SuppressWarnings("unchecked")
    protected <T> T get(String path, int expectedStatus, Class<T> type){
        assert path.startsWith("/");
        return get(path, expectedStatus, "*/*", type);
    }

    @SuppressWarnings("unchecked")
    protected <T> T get(String path, int expectedStatus, String accept, Class<T> type){
        assert path.startsWith("/");
        try {
            if(HttpResponse.class.isAssignableFrom(type)){
                HttpResponse<String> response = Unirest.get(getBaseUrl(path)).header("Accept", accept)
                    .header("Accept-Encoding","")
                    .header("Authorization", "Bearer "+jwtToken)
                    .asString();
                Assert.assertEquals(expectedStatus, response.getStatus());
                return (T) response;
            }

            HttpResponse<T> response = Unirest.get(getBaseUrl(path)).header("Accept", accept)
                .header("Authorization", "Bearer "+jwtToken).asObject(type);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    protected Map delete(String path){
        assert path.startsWith("/");
        try {
            HttpResponse<Map> response = Unirest.delete(getBaseUrl(path)).asObject(Map.class);
            Assert.assertEquals(200, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }


    protected Map<String, Object> post(String path, Object body) {
        return post(path,body,200);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> post(String path, Object body, int expectedStatus){
        assert path.startsWith("/");
        try {
            HttpResponse<Map> response = Unirest.post(getBaseUrl(path))
                .header("Content-Type","application/json")
                .header("Authorization", "Bearer "+jwtToken)
                .body(body).asObject(Map.class);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }

    protected String post(String path, String body, String contentType, int expectedStatus){
        assert path.startsWith("/");
        try {
            HttpResponse<String> response = Unirest.post(getBaseUrl(path))
                .header("Content-Type",contentType)
                .header("Accept-Encoding","")
                .header("Authorization", "Bearer "+jwtToken)
                .body(body).asObject(String.class);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }



    protected Map<String, Object> put(String path, Object body) {
        return put(path, body, 200);
    }
    @SuppressWarnings("unchecked")
    protected Map<String, Object> put(String path, Object body, int expectedStatus){
        assert path.startsWith("/");
        try {
            HttpResponse<Map> response = Unirest.put(getBaseUrl(path))
                .header("Content-Type","application/json")
                .header("Accept","application/json")
                .header("Authorization", "Bearer "+jwtToken)
                //Unirest by default sets accept-encoding to gzip but stapler is sending malformed gzip value if
                // the response length is small (in this case its 20 chars).
                // Needs investigation in stapler to see whats going on there.
                // For time being gzip compression is disabled
                .header("Accept-Encoding","")
                .body(body).asObject(Map.class);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }


    protected String put(String path, String body, String contentType, int expectedStatus){
        assert path.startsWith("/");
        try {
            HttpResponse<String> response = Unirest.put(getBaseUrl(path))
                .header("Content-Type",contentType)
                .header("Authorization", "Bearer "+jwtToken)
                .body(body).asObject(String.class);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, Object> patch(String path, Object body) {
        return patch(path, body, 200);
    }
    @SuppressWarnings("unchecked")
    protected Map<String, Object> patch(String path, Object body, int expectedStatus){
        assert path.startsWith("/");
        try {
            HttpResponse<Map> response = Unirest.patch(getBaseUrl(path))
                .header("Content-Type","application/json")
                .header("Authorization", "Bearer "+jwtToken)
                .body(body).asObject(Map.class);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }

    }

    protected String patch(String path, String body, String contentType, int expectedStatus){
        assert path.startsWith("/");
        try {
            HttpResponse<String> response = Unirest.patch(getBaseUrl(path))
                .header("Content-Type",contentType)
                .header("Authorization", "Bearer "+jwtToken)
                .body(body).asObject(String.class);
            Assert.assertEquals(expectedStatus, response.getStatus());
            return response.getBody();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }

    protected void validateMultiBranchPipeline(WorkflowMultiBranchProject p, Map resp, int numBranches){
        validateMultiBranchPipeline(p, resp, numBranches, -1, -1);
    }
    protected void validateMultiBranchPipeline(WorkflowMultiBranchProject p, Map resp, int numBranches, int numSuccBranches, int numOfFailingBranches){
        Assert.assertEquals("jenkins", resp.get("organization"));
        Assert.assertEquals(p.getName(), resp.get("name"));
        Assert.assertEquals(p.getDisplayName(), resp.get("displayName"));
        Assert.assertNull(resp.get("lastSuccessfulRun"));
        Assert.assertEquals(numBranches, resp.get("totalNumberOfBranches"));
        if(numOfFailingBranches >= 0) {
            Assert.assertEquals(numOfFailingBranches, resp.get("numberOfFailingBranches"));
        }
        if(numSuccBranches >= 0) {
            Assert.assertEquals(numSuccBranches, resp.get("numberOfSuccessfulBranches"));
        }
        Assert.assertEquals(p.getBuildHealth().getScore(), resp.get("weatherScore"));
    }

    protected void validatePipeline(Job p, Map resp){
        Assert.assertEquals("jenkins", resp.get("organization"));
        Assert.assertEquals(p.getName(), resp.get("name"));
        Assert.assertEquals(p.getDisplayName(), resp.get("displayName"));
        Assert.assertEquals(p.getFullName(), resp.get("fullName"));
        Assert.assertEquals(p.getBuildHealth().getScore(), resp.get("weatherScore"));
        if(p.getLastSuccessfulBuild() != null){
            Run b = p.getLastSuccessfulBuild();
            String s = baseUrl + "/organizations/jenkins/pipelines/" +
                p.getName() + "/runs/" + b.getId()+"/";
            if(p instanceof WorkflowJob && p.getParent() instanceof MultiBranchProject){
                s = baseUrl + "/organizations/jenkins/pipelines/" +
                    ((MultiBranchProject) p.getParent()).getName() +"/branches/"+ Util.rawEncode(p.getName())+"/runs/" + b.getId()+"/";
            }
            Assert.assertEquals(s, resp.get("lastSuccessfulRun"));

        }else{
            Assert.assertNull(resp.get("lastSuccessfulRun"));
        }

        if(p.getLastBuild() != null){
            Run r = p.getLastBuild();
            validateRun(r, (Map) resp.get("latestRun"), "FINISHED");
        }else{
            Assert.assertNull(resp.get("latestRun"));
        }
    }

    protected void validateRun(Run r, Map resp){
        validateRun(r,resp, "FINISHED");
    }
    protected void validateRun(Run r, Map resp, String state){
        Assert.assertNotNull(resp);
        Assert.assertEquals(r.getId(), resp.get("id"));
        Assert.assertEquals("jenkins", resp.get("organization"));
        Assert.assertEquals(r.getParent().getName(), resp.get("pipeline"));
        Assert.assertEquals(new SimpleDateFormat(JsonConverter.DATE_FORMAT_STRING)
            .format(new Date(r.getStartTimeInMillis())), resp.get("startTime"));
        Assert.assertEquals(r.getResult().toString(), resp.get("result"));
        Assert.assertEquals(state, resp.get("state"));
    }

    protected String getNodeName(FlowNode n){
        return n.getAction(ThreadNameAction.class) != null
            ? n.getAction(ThreadNameAction.class).getThreadName()
            : n.getDisplayName();
    }

    private String getBaseUrl(String path){
        return baseUrl + path;
    }


    protected List<FlowNode> getStages(FlowGraphTable nodeGraphTable){
        List<FlowNode> nodes = new ArrayList<>();
        for(FlowGraphTable.Row row: nodeGraphTable.getRows()){
            if(PipelineNodeUtil.isStage(row.getNode()) ||
                PipelineNodeUtil.isParallelBranch(row.getNode())){
                nodes.add(row.getNode());
            }
        }
        return nodes;
    }

    protected List<FlowNode> getStages(NodeGraphBuilder builder){
        List<FlowNode> nodes = new ArrayList<>();
        for(FlowNodeWrapper node: builder.getPipelineNodes()){
            if(node.type == FlowNodeWrapper.NodeType.STAGE){
                nodes.add(node.getNode());
            }
        }

        return nodes;
    }
    protected List<FlowNode> getStagesAndParallels(NodeGraphBuilder builder){
        List<FlowNode> nodes = new ArrayList<>();
        for(FlowNodeWrapper node: builder.getPipelineNodes()){
            if(node.type == FlowNodeWrapper.NodeType.STAGE || node.type == FlowNodeWrapper.NodeType.PARALLEL){
                nodes.add(node.getNode());
            }
        }

        return nodes;
    }

    protected List<FlowNode> getParallelNodes(NodeGraphBuilder builder){
        List<FlowNode> nodes = new ArrayList<>();
        for(FlowNodeWrapper node: builder.getPipelineNodes()){
            if(node.type == FlowNodeWrapper.NodeType.PARALLEL){
                nodes.add(node.getNode());
            }
        }

        return nodes;
    }

    protected String getHrefFromLinks(Map resp, String link){
        Map links = (Map) resp.get("_links");
        if(links == null){
            return null;
        }

        Map l = (Map)links.get(link);
        if(l == null){
            return null;
        }
        return (String) l.get("href");
    }

    protected String getUrlFromHref(String href){
        if(href.startsWith("/blue/rest")){
            return href.substring("/blue/rest".length());
        }
        return href;
    }

    protected List<FlowNode> getParallelNodes(FlowGraphTable nodeGraphTable){
        List<FlowNode> parallelNodes = new ArrayList<>();

        for(FlowGraphTable.Row row: nodeGraphTable.getRows()){
            if(PipelineNodeUtil.isParallelBranch(row.getNode())){
                parallelNodes.add(row.getNode());
            }
        }
        return parallelNodes;
    }

    public RequestBuilder request() {
        return new RequestBuilder(baseUrl);
    }
    public  class RequestBuilder {
        private String url;
        private String username;
        private String method;
        private String password;
        private Map data;
        private String contentType = "application/json";
        private String baseUrl;
        private int expectedStatus = 200;
        private String token;


        private String getBaseUrl(String path){
            return baseUrl + path;
        }

        public RequestBuilder status(int status) {
            this.expectedStatus = status;
            return this;
        }

        public RequestBuilder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public RequestBuilder url(String url) {
            this.url = url;
            return this;
        }

        public RequestBuilder auth(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public RequestBuilder authAlice() {
            this.username = "alice";
            this.password = "alice";
            return this;
        }

        public RequestBuilder jwtToken(String token){
            this.token = token;
            return this;
        }

        public RequestBuilder data(Map data) {
            this.data = data;
            return this;
        }

        public RequestBuilder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public RequestBuilder put(String url) {
            this.url = url;
            this.method = "PUT";
            return this;
        }


        public RequestBuilder get(String url) {
            this.url = url;
            this.method = "GET";
            return this;
        }

        public RequestBuilder post(String url) {
            this.url = url;
            this.method = "POST";
            return this;
        }


        public RequestBuilder delete(String url) {
            this.url = url;
            this.method = "DELETE";
            return this;
        }

        public <T> T build(Class<T> clzzz) {
            assert url != null;
            assert url.startsWith("/");
            try {
                HttpRequest request;
                switch (method) {
                    case "PUT":
                        request = Unirest.put(getBaseUrl(url));
                        break;
                    case "POST":
                        request = Unirest.post(getBaseUrl(url));
                        break;
                    case "GET":
                        request = Unirest.get(getBaseUrl(url));
                        break;
                    case "DELETE":
                        request = Unirest.delete(getBaseUrl(url));
                        break;
                    default:
                        throw new RuntimeException("No default options");

                }
                request.header("Accept-Encoding","");
                if(!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)){
                    request.basicAuth(username, password);
                }else{
                    if (token == null) {
                        request.header("Authorization", "Bearer " + PipelineBaseTest.this.jwtToken);
                    }else{
                        request.header("Authorization", "Bearer " + token);
                    }
                }

                request.header("Content-Type", contentType);


                if(request instanceof HttpRequestWithBody && data != null) {
                    ((HttpRequestWithBody)request).body(data);
                }
                HttpResponse<T> response = request.asObject(clzzz);
                Assert.assertEquals(expectedStatus, response.getStatus());
                return response.getBody();
            } catch (UnirestException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String getJwtToken(Jenkins jenkins) throws UnirestException {
        HttpResponse<String> response = Unirest.get(jenkins.getRootUrl()+"jwt-auth/token/").header("Accept", "*/*")
            .header("Accept-Encoding","").asString();

        String token = response.getHeaders().getFirst(X_BLUEOCEAN_JWT);
        Assert.assertNotNull(token);
        //we do not validate it for test optimization and for the fact that there are separate
        // tests that test token generation and validation
        return token;
    }

    public static String getJwtToken(Jenkins jenkins, String username, String password) throws UnirestException {
        GetRequest request = Unirest.get(jenkins.getRootUrl()+"jwt-auth/token/").header("Accept", "*/*")
            .header("Accept-Encoding","");
        if(username!= null && password!= null){
            request.basicAuth(username,password);
        }

        HttpResponse<String> response = request.asString();
        String token = response.getHeaders().getFirst(X_BLUEOCEAN_JWT);
        Assert.assertNotNull(token);
        //we do not validate it for test optimization and for the fact that there are separate
        // tests that test token generation and validation
        return token;
    }

    protected WorkflowJob scheduleAndFindBranchProject(WorkflowMultiBranchProject mp,  String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    protected void scheduleAndFindBranchProject(WorkflowMultiBranchProject mp) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
    }

    protected WorkflowJob findBranchProject(WorkflowMultiBranchProject mp,  String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        if (p == null) {
            mp.getIndexing().writeWholeLogTo(System.out);
            fail(name + " project not found");
        }
        return p;
    }

}
