package org.study.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OpenSeaarchRestClient<T> {

    Logger logger = LoggerFactory.getLogger(OpenSeaarchRestClient.class.getName());

    @Value("${opensearch.url}")
    private String openSearchUrl;

    private RestTemplate restTemplate;

    public OpenSeaarchRestClient(@Value("${opensearch.authentication.certificate}") String certificateFileName,
                                 @Value("${opensearch.authentication.key}") String keyFileName,
                                 @Value("${opensearch.authentication.keypassword:}") String keyFilePassword,
                                 @Value("${opensearch.authentication.user:admin}") String user,
                                 @Value("${opensearch.authentication.password:admin}") String password) {
        try {
            restTemplate = restTemplate(certificateFileName, keyFileName, keyFilePassword, user, password);
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }

    private static FileInputStream getFileFromResource(String fileName) {
        ClassLoader classLoader = OpenSeaarchRestClient.class.getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource(fileName)).getFile());
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    private static KeyStore loadKeyStoreFromCrtAndKey(String certificateFileName, String keyFileName, String keyPassword)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);  // Initialize an empty keystore

        try (InputStream certInput = getFileFromResource(certificateFileName);
             InputStream keyInput = getFileFromResource(keyFileName)
             //InputStream caInput = new FileInputStream(CaPath);
        ) {

            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInput);
            //X509Certificate ca = (X509Certificate) certFactory.generateCertificate(caInput);

            // Load private key from PEM file
            PrivateKey privateKey = readPrivateKeyFromKeyFile(keyInput);

            // Load certificate into the keystore
            keyStore.setCertificateEntry("public-key", cert);
            //keyStore.setCertificateEntry("cacert", ca);

            // Load private key into the keystore
            keyStore.setKeyEntry("private-key", privateKey, keyPassword.toCharArray(),
                    new java.security.cert.Certificate[]{cert /*, ca*/});
        }

        return keyStore;
    }

    private static PrivateKey readPrivateKeyFromKeyFile(InputStream keyInput) throws IOException {
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(keyInput))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            Object keyObject = pemParser.readObject();
            if (keyObject instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) keyObject).getPrivateKeyInfo());
            } else if (keyObject instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) keyObject);
            } else {
                throw new IOException("Invalid private key format in key file");
            }
        }
    }

    private static RestTemplate restTemplate(
            String certificateFileName,
            String keyFileName,
            String keyPassword,
            String user,
            String password) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException,
            CertificateException, IOException, UnrecoverableKeyException {

        KeyStore keyStore = loadKeyStoreFromCrtAndKey(certificateFileName, keyFileName, keyPassword);

        SSLContext sslContext = new SSLContextBuilder().loadKeyMaterial(keyStore,
                keyPassword.toCharArray()).loadTrustMaterial((cert, url) -> true).build();
        SSLConnectionSocketFactory sslConFactory = new SSLConnectionSocketFactory(sslContext);
        HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(sslConFactory).build();
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
        ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate rt = new RestTemplate(requestFactory);
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
        var list = new ArrayList<ClientHttpRequestInterceptor>();
        list.add((request, body, execution) -> {
            request.getHeaders().add("Authorization", basicAuth);
            return execution.execute(request, body);
        });

        rt.setInterceptors(list);

        return rt;
    }

    public void createIndex(String indexName) {
        String url = openSearchUrl + "/" + indexName.toLowerCase();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> out = restTemplate.getForEntity(url, String.class, entity);
            if (out.getStatusCode().is2xxSuccessful()) {
                logger.info("Index {indexName} already exists");
            }
        } catch (HttpClientErrorException.NotFound e) {
            restTemplate.put(url, null, "");
            logger.info("Index {indexName} created successfully");
        }
    }

    public void insertDocument(String indexName, T document) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<T> entity = new HttpEntity<>(document, headers);
        String url = openSearchUrl + "/" + indexName.toLowerCase() + "/_doc";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        logger.info("Document created successfully " + response);
    }

    public void insertAllDocuments(String indexName, List<T> documents) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ObjectMapper objectMapper = new ObjectMapper();
        String bulk = documents.stream().
                map(document -> {
                    String jsondoc = "";
                    try {
                        jsondoc = objectMapper.writeValueAsString(document);
                    } catch (JsonProcessingException e) {
                        logger.error(e.getMessage());
                    }
                    return "{\"index\":{}}\n" + jsondoc + "\n";
                }).collect(Collectors.joining());
        HttpEntity<String> entity = new HttpEntity<>(bulk, headers);
        String url = openSearchUrl + "/" + indexName.toLowerCase() + "/_bulk";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        logger.info("Documents created successfully " + response);
    }
}
