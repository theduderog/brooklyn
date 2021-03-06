package brooklyn.launcher;

import brooklyn.config.BrooklynProperties;
import brooklyn.management.internal.LocalManagementContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public class BrooklynWebServerTest {

    public static final Logger log = LoggerFactory.getLogger(BrooklynWebServer.class);

    private BrooklynProperties brooklynProperties;

    @BeforeMethod
    public void setUp(){
        brooklynProperties = BrooklynProperties.Factory.newDefault();
    }

    @Test
    public void verifyHttp() throws Exception {
        BrooklynWebServer webServer = new BrooklynWebServer(new LocalManagementContext(brooklynProperties));
        try {
            webServer.start();
    
            DefaultHttpClient httpclient = new DefaultHttpClient();
    
            HttpGet httpget = new HttpGet(webServer.getRootUrl());
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            InputStream instream = entity.getContent();
        } finally {
            webServer.stop();
        }
    }

    @Test
    public void verifyHttps() throws Exception {
        BrooklynWebServer webServer = buildWebServer();
        try {
            DefaultHttpClient httpclient = buildHttpsClient(webServer);
    
            HttpGet httpget = new HttpGet(webServer.getRootUrl());
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            InputStream instream = entity.getContent();
        } finally {
            webServer.stop();
        }
    }

    private BrooklynWebServer buildWebServer() throws Exception {
        Map flags = new HashMap();
        flags.put("httpsEnabled", true);
        flags.put("keystorePath", getFile("server.ks"));
        flags.put("keystorePassword", "password");
        flags.put("truststorePath", getFile("server.ts"));
        flags.put("trustStorePassword", "password");

        BrooklynWebServer webServer = new BrooklynWebServer(flags, new LocalManagementContext(brooklynProperties));
        webServer.start();
        return webServer;
    }

    private DefaultHttpClient buildHttpsClient(BrooklynWebServer webServer) throws Exception {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        KeyStore keyStore = load("client.ks", "password");
        KeyStore trustStore = load("client.ts", "password");
        SSLSocketFactory socketFactory = new SSLSocketFactory(keyStore, "password", trustStore);
        socketFactory.setHostnameVerifier(new AllowAllHostnameVerifier());

        Scheme sch = new Scheme("https", webServer.getActualPort(), socketFactory);
        httpclient.getConnectionManager().getSchemeRegistry().register(sch);
        return httpclient;
    }

    private KeyStore load(String name, String password) throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream instream = new FileInputStream(new File(getFile(name)));
        keystore.load(instream, password.toCharArray());
        return keystore;
    }

    private String getFile(String file) {
        return new File(getClass().getResource("/" + file).getFile()).getAbsolutePath();
    }
}
