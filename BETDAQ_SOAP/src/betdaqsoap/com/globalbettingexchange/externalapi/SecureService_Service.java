package com.globalbettingexchange.externalapi;

import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.Service;

/**
 * This class was generated by Apache CXF 3.3.5
 * 2020-01-22T16:23:34.134Z
 * Generated source version: 3.3.5
 *
 */
@WebServiceClient(name = "SecureService",
                  wsdlLocation = "http://api.betdaq.com/v2.0/API.wsdl",
                  targetNamespace = "http://www.GlobalBettingExchange.com/ExternalAPI/")
public class SecureService_Service extends Service {

    public final static URL WSDL_LOCATION;

    public final static QName SERVICE = new QName("http://www.GlobalBettingExchange.com/ExternalAPI/", "SecureService");
    public final static QName SecureService = new QName("http://www.GlobalBettingExchange.com/ExternalAPI/", "SecureService");
    static {
        URL url = null;
        try {
            url = new URL("http://api.betdaq.com/v2.0/API.wsdl");
        } catch (MalformedURLException e) {
            java.util.logging.Logger.getLogger(SecureService_Service.class.getName())
                .log(java.util.logging.Level.INFO,
                     "Can not initialize the default wsdl from {0}", "http://api.betdaq.com/v2.0/API.wsdl");
        }
        WSDL_LOCATION = url;
    }

    public SecureService_Service(URL wsdlLocation) {
        super(wsdlLocation, SERVICE);
    }

    public SecureService_Service(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public SecureService_Service() {
        super(WSDL_LOCATION, SERVICE);
    }

    public SecureService_Service(WebServiceFeature ... features) {
        super(WSDL_LOCATION, SERVICE, features);
    }

    public SecureService_Service(URL wsdlLocation, WebServiceFeature ... features) {
        super(wsdlLocation, SERVICE, features);
    }

    public SecureService_Service(URL wsdlLocation, QName serviceName, WebServiceFeature ... features) {
        super(wsdlLocation, serviceName, features);
    }




    /**
     *
     * @return
     *     returns SecureService
     */
    @WebEndpoint(name = "SecureService")
    public SecureService getSecureService() {
        return super.getPort(SecureService, SecureService.class);
    }

    /**
     *
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns SecureService
     */
    @WebEndpoint(name = "SecureService")
    public SecureService getSecureService(WebServiceFeature... features) {
        return super.getPort(SecureService, SecureService.class, features);
    }

}
