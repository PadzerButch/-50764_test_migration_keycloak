/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.tests.oidc;

import java.io.Closeable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.OAuth2Constants;
import org.keycloak.common.util.PemUtils;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.ServerECDSASignatureSignerContext;
import org.keycloak.crypto.ServerEdDSASignatureSignerContext;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.testframework.oauth.JwksProvider;
import org.keycloak.testframework.util.HttpServerUtil;

import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

/**
 * Serves request objects using the managed HTTP server and signing keys.
 * Replaces the legacy test application's request-object endpoint for this migration.
 */
class RequestObjectProvider implements Closeable {

    private static final String PATH = "/request-object/get-oidc-request";

    private final HttpServer server;
    private final JwksProvider jwks;
    private volatile String request;
    private String curve;

    RequestObjectProvider(HttpServer server, JwksProvider jwks) {
        this.server = server;
        this.jwks = jwks;
        server.createContext(PATH, exchange -> HttpServerUtil.sendResponse(exchange, 200,
                Map.of("Content-Type", List.of("application/jwt")), request));
    }

    String getUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
    }

    String getRequest() {
        return request;
    }

    Map<String, String> generateKeys(String algorithm) {
        return generateKeys(algorithm, null);
    }

    Map<String, String> generateKeys(String algorithm, String curve) {
        this.curve = curve == null && Algorithm.EdDSA.equals(algorithm) ? Algorithm.Ed25519 : curve;
        return jwks.generateKeys(algorithm, this.curve, true, false, null);
    }

    void setRequest(String clientId, String redirectUri, String maxAge, String algorithm) {
        setRequest(clientId, redirectUri, maxAge, null, algorithm);
    }

    void setRequest(String clientId, String redirectUri, String maxAge, String state, String algorithm) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(OIDCLoginProtocol.CLIENT_ID_PARAM, clientId);
        claims.put(OIDCLoginProtocol.RESPONSE_TYPE_PARAM, OAuth2Constants.CODE);
        claims.put(OIDCLoginProtocol.REDIRECT_URI_PARAM, redirectUri);
        if (state != null) {
            claims.put(OIDCLoginProtocol.STATE_PARAM, state);
        }
        if (maxAge != null) {
            claims.put(OIDCLoginProtocol.MAX_AGE_PARAM, Integer.parseInt(maxAge));
        }
        registerRequest(claims, algorithm);
    }

    void registerRequest(Map<String, Object> claims, String algorithm) {
        if ("none".equals(algorithm)) {
            request = new JWSBuilder().jsonContent(claims).none();
            return;
        }

        KeyWrapper key = new KeyWrapper();
        key.setAlgorithm(algorithm);
        key.setKid(jwks.getJwks().getKeys()[0].getKeyId());
        key.setPrivateKey(privateKey(algorithm));
        key.setCurve(curve);
        SignatureSignerContext signer = switch (algorithm) {
            case Algorithm.ES256, Algorithm.ES384, Algorithm.ES512 -> new ServerECDSASignatureSignerContext(key);
            case Algorithm.EdDSA -> new ServerEdDSASignatureSignerContext(key);
            default -> new AsymmetricSignatureSignerContext(key);
        };
        request = new JWSBuilder().kid(key.getKid()).jsonContent(claims).sign(signer);
    }

    private PrivateKey privateKey(String algorithm) {
        String pem = jwks.getKeysAsPem().get(JwksProvider.PRIVATE_KEY);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try {
            if (algorithm.startsWith("ES")) {
                // The SEC1 export omits the curve; recover it from the matching public key.
                ECPrivateKey privateKey = ECPrivateKey.getInstance(PemUtils.pemToDer(pem));
                ECPublicKey publicKey = (ECPublicKey) PemUtils.decodePublicKey(jwks.getKeysAsPem().get(JwksProvider.PUBLIC_KEY));
                return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(privateKey.getKey(), publicKey.getParams()));
            }
            if (Algorithm.EdDSA.equals(algorithm)) {
                return converter.getPrivateKey(PrivateKeyInfo.getInstance(PemUtils.pemToDer(pem)));
            }
            return PemUtils.decodePrivateKey(pem);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot decode request-object signing key", e);
        }
    }

    @Override
    public void close() {
        server.removeContext(PATH);
    }
}
