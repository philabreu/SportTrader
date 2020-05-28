package tools;


import Trader.SportsTrader;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.jsoup.select.Evaluator;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tools.printer.*;

public class Requester {

    private static final Logger log = Logger.getLogger(SportsTrader.class.getName());

    HttpClient httpClient;
    public HashMap<String, String> headers;
    XMLInputFactory xmlInputFactory;
    ReentrantLock headerLock = new ReentrantLock();



    public Requester() {
        //httpClient = HttpClients.createDefault();
        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD)
                        .setConnectTimeout(5000)
                        .build())
                .build();

        headers = new HashMap<>();
    }


    public static void main(String[] args){
        try {
            Requester r = new Requester();
            r.get("https://google.com");


        } catch (Exception e){
            e.printStackTrace();
        }
        print("END");
    }



    public static Requester JSONRequester(){
        Requester requester = new Requester();
        requester.setHeader("Content-Type", "application/json");
        requester.setHeader("Accept", "application/json");
        return requester;
    }


    public static Requester SOAPRequester(){
        Requester requester = new Requester();
        requester.setHeader("Content-Type", "text/xml");
        requester.xmlInputFactory = XMLInputFactory.newFactory();
        return requester;
    }


    public void setHeader(String key, String value){
        headerLock.lock();
        headers.put(key, value);
        headerLock.unlock();
    }


    public static String SOAP2XML(Object SOAP_obj) throws JAXBException {
        Class obj_type = SOAP_obj.getClass();
        Marshaller marshaller = JAXBContext.newInstance(obj_type).createMarshaller();
        StringWriter sw = new StringWriter();
        marshaller.marshal(SOAP_obj, sw);
        return sw.toString();
    }

    public static String SOAP2XMLnull(Object SOAP_obj)  {
        try{
            return SOAP2XML(SOAP_obj);
        }
        catch (JAXBException e){
            return String.format("<JAXB EXCEPTION - %s>", e.toString());
        }
    }


    public Object SOAPRequest(String url, String soap_header, String soap_body, Class<?> return_class)
            throws IOException, URISyntaxException {
        return SOAPRequest(url, soap_header, soap_body, return_class, false);
    }


    public Object SOAPRequest(String url, String soap_header, Object soap_java_obj, Class<?> return_class)
            throws IOException, URISyntaxException {

        String xml_body = null;
        try {
            xml_body = SOAP2XML(soap_java_obj);
        }
        catch (JAXBException e){
            log.severe(String.format("Error trying to convert soap java obj of type %s to XML",
                    return_class.getSimpleName()));
            return null;
        }
        return SOAPRequest(url, soap_header, xml_body, return_class, false);
    }


    public Object SOAPRequest(String url, String soap_header, Object soap_java_obj, Class<?> return_class, boolean print)
            throws IOException, URISyntaxException, JAXBException {
        return SOAPRequest(url, soap_header, SOAP2XML(soap_java_obj), return_class, print);
    }


    public Object SOAPRequest(String url, String soap_header, String soap_body, Class<?> return_class, boolean print)
            throws IOException, URISyntaxException {

        // Build soap xml
        String soap_xml =
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ext=\"http://www.GlobalBettingExchange.com/ExternalAPI/\">" +
                soap_header +
                "<soapenv:Body>" +
                soap_body +
                "</soapenv:Body>" +
                "</soapenv:Envelope>";

        if (print){
            print("\nvv Request vv");
            ppx (soap_xml);
        }


        // Create new http POST object
        HttpPost httpPost = new HttpPost(new URI(url));

        // Set default headers from requester object
        headerLock.lock();
        for (Entry<String, String> header: this.headers.entrySet()){
            httpPost.setHeader(header.getKey(), header.getValue());
        }
        headerLock.unlock();

        // Pass in JSON as string to body entity then send request
        httpPost.setEntity(new StringEntity(soap_xml));
        HttpResponse response = httpClient.execute(httpPost);

        // Check response code is valid
        int status_code = response.getStatusLine().getStatusCode();
        if (status_code < 200 || status_code >= 300) {
            String response_body = EntityUtils.toString(response.getEntity());
            String msg = String.format("ERROR %d in HTTP SOAP request - %s\n%s\n%s\n%s",
                    status_code,
                    response.toString(),
                    response_body,
                    response.getStatusLine().toString(),
                    xmlstring(soap_xml));
            log.severe(msg);
            throw new IOException();
        }

        // Convert body to json and return
        String response_body = EntityUtils.toString(response.getEntity());

        if (print){
            print("\nvv Response vv");
            ppx(response_body);
        }

        try {

            // Find corresponding object in xml response
            XMLStreamReader xml_reader = xmlInputFactory.createXMLStreamReader(
                    new ByteArrayInputStream(response_body.getBytes()));
            xml_reader.nextTag();
            while (!xml_reader.getLocalName().equals(return_class.getSimpleName())) {
                xml_reader.nextTag();
            }

            // Turn that xml object into java object
            JAXBContext jaxbContext = JAXBContext.newInstance(return_class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            Object return_object = jaxbUnmarshaller.unmarshal(xml_reader);
            return return_object;
        }
        catch (XMLStreamException | JAXBException e) {
            String msg = String.format("Could not turn SOAP response into object of type %s\n%s",
                    return_class.getSimpleName(), response_body);
            log.severe(msg);
            throw new IOException(msg);
        }
    }


    public Object post(String url, String json, Map<String, String> headers, boolean return400) throws IOException, URISyntaxException {

        // Create new http POST object
        HttpPost httpPost = new HttpPost(new URI(url));


        // Set default headers from requester object
        headerLock.lock();
        for (Entry<String, String> header: this.headers.entrySet()){
            httpPost.setHeader(header.getKey(), header.getValue());
        }
        headerLock.unlock();

        // Set extra headers if given
        if (headers != null){
            for (Entry<String, String> entry : headers.entrySet()) {
                httpPost.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // Pass in JSON as string to body entity then send request
        httpPost.setEntity(new StringEntity(json));
        HttpResponse response = httpClient.execute(httpPost);

        // Check response code is valid
        int status_code = response.getStatusLine().getStatusCode();
        if (return400 && status_code == 400){
            // Do nothing
        }
        else if (status_code < 200 || status_code >= 300){
            String response_body = EntityUtils.toString(response.getEntity());
            String msg = String.format("ERROR %d in HTTP POST request - %s\n%s\n%s\n%s",
                    status_code, response.toString(), response_body, response.getStatusLine().toString(), json);
            log.severe(msg);
            throw new IOException(msg);
        }

        // Convert body to json and return
        String response_body = EntityUtils.toString(response.getEntity());
        return JSONValue.parse(response_body);
    }


    public Object post(String url, String json) throws IOException, URISyntaxException {
        return post(url, json, null, false);
    }


    public Object post(String url, JSONObject json, boolean return400) throws IOException, URISyntaxException {
        return post(url, json.toString(), null, return400);
    }


    public Object post(String url, JSONObject json, Map<String, String> headers) throws IOException, URISyntaxException {
        return post(url, json.toString(), headers, false);
    }


    public Object post(String url, JSONArray json, Map<String, String> headers) throws IOException, URISyntaxException {
        return post(url, json.toString(), headers, false);
    }


    public Object post(String url, JSONObject json) throws IOException, URISyntaxException {
        return post(url, json, null);
    }


    public Object post(String url, JSONArray json) throws IOException, URISyntaxException {
        return post(url, json, null);
    }


    public Object get(String url, Map<String, Object> params) throws IOException, URISyntaxException,
            InterruptedException {

        String raw_response = getRaw(url, params);
        return JSONValue.parse(raw_response);
    }


    public Object get(String url) throws IOException, URISyntaxException,
            InterruptedException {

        String raw_response = getRaw(url);
        return JSONValue.parse(raw_response);
    }


    public String getRaw(String url, Map<String, Object> params) throws IOException, URISyntaxException {

        // Add in the paramters as the uri is made
        URIBuilder uriBuilder = new URIBuilder(url);
        if (params != null) {
            for (Entry<String, Object> entry : params.entrySet()) {
                String param_name = entry.getKey();
                String param_value = String.valueOf(entry.getValue());
                uriBuilder.addParameter(param_name, param_value);
            }
        }

        // Create http GET object
        HttpGet httpGet = new HttpGet(uriBuilder.build());

        headerLock.lock();
        // Add in default headers form requester object
        for (Entry<String, String> header: headers.entrySet()){
            httpGet.setHeader(header.getKey(), header.getValue());
        }
        headerLock.unlock();

        HttpResponse response = httpClient.execute(httpGet);

        // Check response code is valid
        int status_code = response.getStatusLine().getStatusCode();
        if (status_code == 429){
            String response_body = EntityUtils.toString(response.getEntity());
            if (response_body == null){
                response_body = "null";
            }
            return response_body;
        }

        if (status_code == 502){
            String response_body = EntityUtils.toString(response.getEntity());
            if (response_body == null){
                response_body = "null";
            }
            log.warning(String.format("502 error error for GET request '%s', trying again.\n%s", url, response_body));
            return getRaw(url, params);
        }

        if (status_code < 200 || status_code >= 300){
            String response_body = EntityUtils.toString(response.getEntity());
            if (response_body == null){
                response_body = "null";
            }
            if (params == null){
                params = new HashMap<>();
            }
            String msg = String.format("ERROR %d in HTTP GET request\n%s\nurl: %s\nparams: %s\nURI:%s\n%s\n%s",
                    status_code,
                    response.toString(),
                    url,
                    String.valueOf(params),
                    httpGet.getURI().toString(),
                    response_body,
                    response.getStatusLine().toString());
            throw new HttpResponseException(status_code, msg);
        }

        // Convert body to json and return
        String response_body = EntityUtils.toString(response.getEntity());
        return response_body;
    }


    public String getRaw(String url) throws IOException, URISyntaxException {
        return getRaw(url, null);
    }


    public Object delete(String url) throws URISyntaxException, IOException {

        HttpDelete httpDelete = new HttpDelete(new URIBuilder(url).build());

        headerLock.lock();
        // Add in default headers form requester object
        for (Entry<String, String> header: headers.entrySet()){
            httpDelete.setHeader(header.getKey(), header.getValue());
        }
        headerLock.unlock();

        HttpResponse response = httpClient.execute(httpDelete);

        // Check response code is valid
        int status_code = response.getStatusLine().getStatusCode();
        if (status_code < 200 || status_code >= 300){
            String response_body = EntityUtils.toString(response.getEntity());
            if (response_body == null){
                response_body = "null";
            }
            String msg = String.format("ERROR %d in HTTP DELETE request\n%s\nurl: %s\nURI:%s\n%s\n%s",
                    status_code,
                    response.toString(),
                    url,
                    httpDelete.getURI().toString(),
                    response_body,
                    response.getStatusLine().toString());
            log.severe(msg);
            throw new HttpResponseException(status_code, msg);
        }

        // Convert body to json and return
        String response_body = EntityUtils.toString(response.getEntity());
        return JSONValue.parse(response_body);
    }

}
