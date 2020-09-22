package ch.qos.logback.core.subst;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpClientUtil {
	private static RequestConfig requestConfig;
    private static Map<String, CloseableHttpClient> httpClientMap = new ConcurrentHashMap<>();
    
    private HttpClientUtil(){}
    
    static{
    	requestConfig = RequestConfig.custom()
    			.setConnectionRequestTimeout(5000).setSocketTimeout(5000).setConnectTimeout(5000)
    			.build();
    }
    
    private static synchronized CloseableHttpClient createClient(String host, int port){
    	String key = host + ":" + port;
    	CloseableHttpClient client = httpClientMap.get(key);
    	
    	if(client == null){
    		Registry<ConnectionSocketFactory> socketFactoryRegistry = null;
            try {
            	SSLContextBuilder builder = new SSLContextBuilder();
				builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
				
				SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
				socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register(
						"http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslsf).build();
				
			}catch (Exception e) {
				e.printStackTrace();
			}
            
            PoolingHttpClientConnectionManager poolConnManager = socketFactoryRegistry != null ?
            		new PoolingHttpClientConnectionManager(socketFactoryRegistry) : new PoolingHttpClientConnectionManager();
            poolConnManager.setMaxTotal(10000);
            poolConnManager.setDefaultMaxPerRoute(5000);
            
            client = HttpClients.custom()
        			.setConnectionManager(poolConnManager)
        			.setDefaultRequestConfig(requestConfig)
        			.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
        			.build();
            
            httpClientMap.put(key, client);
    	}
    	
	    return client;
    }
    
    private static CloseableHttpClient getClient(String url) throws MalformedURLException{
    	URL urlObj = new URL(url);
    	String host = urlObj.getHost();
    	int port = urlObj.getPort();
    	if(port < 0){
    		port = urlObj.getDefaultPort();
    	}
    	if(port < 0){
    		port = 80;
    	}
    	
    	CloseableHttpClient client = httpClientMap.get(host + ":" + port);
    	if(client == null){
    		client = createClient(host, port);
    	}
    	
    	return client;
    }
    
    private static RequestConfig getConfig(String httpProxyIp, Integer httpProxyPort){
    	if(StringUtils.isNotEmpty(httpProxyIp) && httpProxyPort != null){
    		return RequestConfig.custom()
        			.setConnectionRequestTimeout(5000).setSocketTimeout(5000).setConnectTimeout(5000)
        			.setProxy(new HttpHost(httpProxyIp, httpProxyPort, "http"))
        			.build();
    	}else{
    		return requestConfig;
    	}
    }
	
	private static HttpGet getHttpGet(String url, String httpProxyIp, Integer httpProxyPort){
		HttpGet httpGet = new HttpGet(url);
		httpGet.setConfig(getConfig(httpProxyIp, httpProxyPort));
		
		return httpGet;
	}
	
	private static void release(HttpRequestBase postGet){
		if(postGet != null){
			try{
				postGet.releaseConnection();
			}catch (Exception ex){
				ex.printStackTrace();
			}
		}
	}
	
	private static void release(CloseableHttpResponse response){
		try {
			if(response != null){
				response.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	public static String httpGet(String url, String httpProxyIp, Integer httpProxyPort) throws IOException, NotHttp200Exception {
		CloseableHttpClient httpClient = getClient(url);
		HttpGet httpGet = getHttpGet(url, httpProxyIp, httpProxyPort);
		CloseableHttpResponse response = null;
		
		try {
			response = httpClient.execute(httpGet);
			
			int status = response.getStatusLine().getStatusCode();
        	String content = inputStream2String(response.getEntity().getContent(), StandardCharsets.UTF_8, true);
        	
        	if(status == 200){
				return content;
	        	
	        }else{
	        	throw new NotHttp200Exception(status, content);
	        }
	        
		}finally{
			release(response);
        	release(httpGet);
		}
	}
	
	public static String httpGet(String url) throws IOException, NotHttp200Exception {
		return httpGet(url, null, null);
	}
	
	private static String inputStream2String(InputStream is, Charset charSet, boolean closeOnReturn) throws IOException{ 
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        try{
        	byte[] tempbytes = new byte[128];
            int byteread = 0;
            while ((byteread = is.read(tempbytes)) != -1) {
            	baos.write(tempbytes, 0, byteread);
            }
        }finally{
        	closeStream(baos);
        	if(closeOnReturn){
        		closeStream(is);
        	}
        }
        return new String(baos.toByteArray(), charSet);
	}
	
	private static void closeStream(Closeable os){
		try{
			if(os != null){
				os.close(); 
			}
 		}catch(Exception ex){
 			//ignore exception
 		}
	}
}
