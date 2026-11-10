/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.authentication.authenticators.client.ClientIdAndSecretAuthenticator;
import org.keycloak.authentication.authenticators.client.JWTClientAuthenticator;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;
import org.keycloak.common.util.UriUtils;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyUse;
import org.keycloak.events.Details;
import org.keycloak.events.EventType;
import org.keycloak.jose.jwe.JWE;
import org.keycloak.jose.jwe.JWEConstants;
import org.keycloak.jose.jwe.JWEException;
import org.keycloak.jose.jwe.JWEHeader;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.keys.Attributes;
import org.keycloak.keys.KeyProvider;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.protocol.oidc.OIDCAdvancedConfigWrapper;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.ClaimsParameterTokenMapper;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.UserInfo;
import org.keycloak.representations.idm.CertificateRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.KeysMetadataRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.util.CertificateInfoHelper;
import org.keycloak.testframework.annotations.InjectEvents;
import org.keycloak.testframework.annotations.InjectHttpServer;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.events.EventAssertion;
import org.keycloak.testframework.events.Events;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.oauth.JwksProvider;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectJwksProvider;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.ClientConfig;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.remote.runonserver.InjectRunOnServer;
import org.keycloak.testframework.remote.runonserver.RunOnServerClient;
import org.keycloak.testframework.remote.timeoffset.InjectTimeOffSet;
import org.keycloak.testframework.remote.timeoffset.TimeOffSet;
import org.keycloak.testframework.ui.annotations.InjectPage;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.ui.page.ErrorPage;
import org.keycloak.testframework.ui.page.LoginPage;
import org.keycloak.testframework.ui.page.OAuthGrantPage;
import org.keycloak.testframework.ui.page.RegisterPage;
import org.keycloak.testframework.ui.webdriver.ManagedWebDriver;
import org.keycloak.tests.utils.KeyUtils;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.keycloak.testsuite.util.oauth.AuthorizationEndpointResponse;
import org.keycloak.util.JWKSUtils;
import org.keycloak.util.JsonSerialization;
import org.keycloak.util.TokenUtil;

import com.google.common.collect.ImmutableMap;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.keycloak.jose.jwe.JWEConstants.RSA_OAEP;
import static org.keycloak.jose.jwe.JWEConstants.RSA_OAEP_256;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for supporting advanced parameters of OIDC specs (max_age, prompt, ...)
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@KeycloakIntegrationTest
public class OIDCAdvancedRequestParamsTest {

    @InjectRealm(config = AdvancedRequestRealmConfig.class, lifecycle = LifeCycle.METHOD)
    ManagedRealm managedRealm;

    @InjectTimeOffSet
    TimeOffSet timeOffSet;

    @InjectRunOnServer
    RunOnServerClient runOnServer;

    @InjectWebDriver(lifecycle = LifeCycle.METHOD)
    ManagedWebDriver driver;

    @InjectOAuthClient(config = AdvancedRequestClientConfig.class, lifecycle = LifeCycle.METHOD)
    OAuthClient oauth;

    @InjectEvents
    Events events;

    @InjectPage
    protected RegisterPage registerPage;

    @InjectPage
    protected LoginPage loginPage;

    @InjectPage
    protected OAuthGrantPage grantPage;

    @InjectPage
    protected ErrorPage errorPage;

    @InjectHttpServer
    HttpServer httpServer;

    @InjectJwksProvider(lifecycle = LifeCycle.METHOD)
    JwksProvider jwksProvider;

    private RequestObjectProvider requestObjects;

    @BeforeEach
    public void startRequestObjectEndpoint() {
        requestObjects = new RequestObjectProvider(httpServer, jwksProvider);
        // Bind the client's request URI to the managed HTTP endpoint for this method.
        setRequestUris(requestObjects.getUri());
        ClientRepresentation client = oauth.clientResource().toRepresentation();
        client.setRootUrl(UriUtils.getOrigin(requestObjects.getUri()));
        oauth.clientResource().update(client);
    }

    @AfterEach
    public void stopRequestObjectEndpoint() {
        if (requestObjects != null) {
            requestObjects.close();
        }
    }

    // Max_age

    @Test
    public void testMaxAge1() {
        // Open login form and login successfully
        oauth.doLogin("test-user@localhost", "password");
        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);

        IDToken idToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Check that authTime is available and set to current time
        long authTime = idToken.getAuth_time();
        long currentTime = Time.currentTime();
        Assertions.assertTrue(authTime <= currentTime && authTime + 3 >= currentTime);

        // Set time offset
        timeOffSet.set(10);

        // Assert I need to login again through the login form. But username field is not present
        oauth.loginForm().maxAge(1).open();
        loginPage.assertCurrent();
        Assertions.assertFalse(loginPage.isUsernameInputPresent());
        loginPage.fillPassword("password");
        loginPage.submit();
        loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);

        idToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Assert that authTime was updated
        long authTimeUpdated = idToken.getAuth_time();
        Assertions.assertTrue(authTime + 10 <= authTimeUpdated);
    }

    @Test
    public void testMaxAge10000() {
        // Open login form and login successfully
        oauth.doLogin("test-user@localhost", "password");
        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);

        IDToken idToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Check that authTime is available and set to current time
        long authTime = idToken.getAuth_time();
        long currentTime = Time.currentTime();
        Assertions.assertTrue(authTime <= currentTime && authTime + 3 >= currentTime);

        // Set time offset
        timeOffSet.set(10);

        // Now open login form with maxAge=10000
        oauth.loginForm().maxAge(10000).open();

        // Assert that I will be automatically logged through cookie
        loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);

        idToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Assert that authTime is still the same
        long authTimeUpdated = idToken.getAuth_time();
        Assertions.assertEquals(authTime, authTimeUpdated);
    }

    @Test
    public void testMaxAgeIntegerOverflow() {
        // Open login form and login successfully
        oauth.doLogin("test-user@localhost", "password");
        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);

        IDToken idToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Check that authTime is available and set to current time
        long authTime = idToken.getAuth_time();
        long currentTime = Time.currentTime();
        Assertions.assertTrue(authTime <= currentTime && authTime + 3 >= currentTime);

        // Set time offset
        timeOffSet.set(10);

        // max_age close to Integer.MAX_VALUE must not overflow the authTime+maxAge sum and
        // incorrectly force re-authentication
        oauth.loginForm().maxAge(Integer.MAX_VALUE).open();

        // Assert that I will be automatically logged through cookie (no fresh login form shown)
        loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);

        idToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Assert that authTime is still the same (no re-auth happened)
        long authTimeUpdated = idToken.getAuth_time();
        Assertions.assertEquals(authTime, authTimeUpdated);
    }


    // Prompt

    @Test
    public void promptNoneNotLogged() {

        String expectedIssuer = oauth.doWellKnownRequest().getIssuer();

        // Send request with prompt=none
        oauth.loginForm().prompt("none").open();

        Assertions.assertTrue(oauth.parseLoginResponse().isError());

        Assertions.assertNull(events.poll());

        // Assert error response was sent because not logged in
        AuthorizationEndpointResponse resp = oauth.parseLoginResponse();
        Assertions.assertEquals(expectedIssuer, resp.getIssuer());
        Assertions.assertNull(resp.getCode());
        Assertions.assertEquals(OAuthErrorException.LOGIN_REQUIRED, resp.getError());
    }

    @Test
    public void promptNoneSuccess() {
        // Login user
        oauth.openLoginForm();
        loginPage.fillLogin("test-user@localhost", "password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        IDToken idToken = sendTokenRequestAndGetIDToken(loginEvent);
        long authTime = idToken.getAuth_time();

        // Set time offset
        timeOffSet.set(10);

        // Assert user still logged with previous authTime
        oauth.loginForm().prompt("none").open();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent);
        idToken = sendTokenRequestAndGetIDToken(loginEvent);
        long authTime2 = idToken.getAuth_time();

        Assertions.assertEquals(authTime, authTime2);
    }


    // Prompt=none with consent required for client
    @Test
    public void promptNoneConsentRequired() {
        // Require consent
        setConsentRequired(true);

        try {
            // Assert error shown when trying prompt=none and consent not yet granted
            oauth.loginForm().prompt("none").open();
            Assertions.assertTrue(oauth.parseLoginResponse().isError());

            AuthorizationEndpointResponse resp = oauth.parseLoginResponse();
            Assertions.assertNull(resp.getCode());
            Assertions.assertEquals(OAuthErrorException.LOGIN_REQUIRED, resp.getError());

            // Login and confirm consent
            oauth.openLoginForm();
            loginPage.assertCurrent();
            loginPage.fillLogin("test-user@localhost", "password");
            loginPage.submit();
            grantPage.assertCurrent();
            grantPage.accept();

            EventAssertion.expectLoginSuccess(events.poll())
                    .details(Details.USERNAME, "test-user@localhost")
                    .details(Details.CONSENT, Details.CONSENT_VALUE_CONSENT_GRANTED)
                    .details(Details.REDIRECT_URI, oauth.getRedirectUri());

            // Consent not required anymore. Login with prompt=none should success
            oauth.loginForm().prompt("none").open();
            Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

            resp = oauth.parseLoginResponse();
            Assertions.assertNotNull(resp.getCode());
            Assertions.assertNull(resp.getError());

            EventAssertion.expectLoginSuccess(events.poll())
                    .details(Details.USERNAME, "test-user@localhost")
                    .details(Details.CONSENT, Details.CONSENT_VALUE_PERSISTED_CONSENT)
                    .details(Details.REDIRECT_URI, oauth.getRedirectUri());

        } finally {
            // Revert consent
            UserResource user = managedRealm.admin().users().get(managedRealm.admin().users().searchByUsername("test-user@localhost", true).get(0).getId());
            user.revokeConsent("test-app");

            //  revert require consent
            setConsentRequired(false);
        }
    }


    // prompt=login
    @Test
    public void promptLogin() {
        // Login user
        oauth.openLoginForm();
        loginPage.fillLogin("test-user@localhost", "password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        IDToken oldIdToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Set time offset
        timeOffSet.set(10);

        // SSO login first WITHOUT prompt=login ( Tests KEYCLOAK-5248 )
        oauth.openLoginForm();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
        loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        IDToken newIdToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Assert that authTime wasn't updated
        Assertions.assertEquals(oldIdToken.getAuth_time(), newIdToken.getAuth_time());

        // Set time offset
        timeOffSet.set(20);

        // Assert need to re-authenticate with prompt=login
        oauth.loginForm().prompt("login").open();
        loginPage.assertCurrent();
        Assertions.assertFalse(loginPage.isUsernameInputPresent());
        loginPage.fillPassword("password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        newIdToken = sendTokenRequestAndGetIDToken(loginEvent);

        // Assert that authTime was updated
        Assertions.assertTrue(oldIdToken.getAuth_time() + 20 <= newIdToken.getAuth_time(),
                "Expected auth time to change. old auth time: " + oldIdToken.getAuth_time() + " , new auth time: " + newIdToken.getAuth_time());

        // Assert userSession didn't change
        Assertions.assertEquals(oldIdToken.getSessionState(), newIdToken.getSessionState());
    }

    // prompt=create
    @Test
    public void promptCreate() {

        // Assert registration page with prompt=create
        oauth.loginForm().prompt("create").open();
        registerPage.assertCurrent();
    }

    // prompt=create
    @Test
    public void promptCreateShouldFailWhenRegistrationsAreDisabled() {

        RealmRepresentation realmRep = managedRealm.admin().toRepresentation();
        Boolean registrationAllowed = realmRep.isRegistrationAllowed();
        realmRep.setRegistrationAllowed(false);
        managedRealm.admin().update(realmRep);

        // Assert registration page with prompt=create
        try {
            oauth.loginForm().prompt("create").open();
            errorPage.assertCurrent();
            assertTrue(errorPage.getError().contains("Registration not allowed"));
        } finally {
            realmRep.setRegistrationAllowed(registrationAllowed);
            managedRealm.admin().update(realmRep);
        }
    }

    // prompt=consent
    @Test
    public void promptConsent() {
        // Require consent
        setConsentRequired(true);

        try {
            // Login user
            oauth.openLoginForm();
            loginPage.fillLogin("test-user@localhost", "password");
            loginPage.submit();

            // Grant consent
            grantPage.assertCurrent();
            grantPage.accept();

            Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

            EventAssertion.expectLoginSuccess(events.poll())
                    .details(Details.USERNAME, "test-user@localhost")
                    .details(Details.CONSENT, Details.CONSENT_VALUE_CONSENT_GRANTED)
                    .details(Details.REDIRECT_URI, oauth.getRedirectUri());


            // Re-login without prompt=consent. The previous persistent consent was used
            oauth.openLoginForm();
            Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
            EventAssertion.expectLoginSuccess(events.poll())
                    .details(Details.USERNAME, "test-user@localhost")
                    .details(Details.CONSENT, Details.CONSENT_VALUE_PERSISTED_CONSENT)
                    .details(Details.REDIRECT_URI, oauth.getRedirectUri());

            // Re-login with prompt=consent.
            oauth.loginForm()
                    .prompt(OIDCLoginProtocol.PROMPT_VALUE_CONSENT)
                    .open();

            // Assert grant page displayed again. Will need to grant consent again
            grantPage.assertCurrent();
            grantPage.accept();

            Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

            EventAssertion.expectLoginSuccess(events.poll())
                    .details(Details.USERNAME, "test-user@localhost")
                    .details(Details.CONSENT, Details.CONSENT_VALUE_CONSENT_GRANTED);

        } finally {
            // Revert consent
            UserResource user = managedRealm.admin().users().get(managedRealm.admin().users().searchByUsername("test-user@localhost", true).get(0).getId());
            user.revokeConsent("test-app");

            //  revert require consent
            setConsentRequired(false);
        }
    }

    // DISPLAY & OTHERS
    @Test
    public void nonSupportedParams() {
        oauth.loginForm().param("display", "popup").param("foo", "foobar").param("claims_locales", "fr").open();

        loginPage.assertCurrent();
        loginPage.fillLogin("test-user@localhost", "password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        IDToken idToken = sendTokenRequestAndGetIDToken(loginEvent);

        Assertions.assertNotNull(idToken);
    }

    // REQUEST & REQUEST_URI
    @Test
    public void requestObjectNotRequiredNotProvided() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);

        // Send request without request object
        // Assert that the request is accepted
        AuthorizationEndpointResponse response = oauth.loginForm().state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response.getCode());
        Assertions.assertEquals("mystate2", response.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
    }

    @Test
    public void requestObjectNotRequiredProvidedInRequestParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "mystate2", "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response1 = oauth.loginForm().request(oidcClientEndpointsResource.getRequest()).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response1.getCode());
        Assertions.assertEquals("mystate2", response1.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
    }

    @Test
    public void requestObjectNotRequiredProvidedInRequestUriParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "mystate2", "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response2 = oauth.loginForm().requestUri(requestObjects.getUri()).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response2.getCode());
        Assertions.assertEquals("mystate2", response2.getState());
        Assertions.assertTrue(driver.getCurrentUrl().startsWith(oauth.getRedirectUri()));
    }

    @Test
    public void requestObjectRequiredNotProvided() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_OR_REQUEST_URI);
        clientResource.update(clientRep);

        // Send request without request object
        // Assert that the request is not accepted
        oauth.loginForm().state("mystate2").open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredProvidedInRequestParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_OR_REQUEST_URI);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "mystate2", "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response1 = oauth.loginForm().request(oidcClientEndpointsResource.getRequest()).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response1.getCode());
        Assertions.assertEquals("mystate2", response1.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectSupersedesQueryParameter() {
        String stateInRequestObject = "stateInRequestObject";
        String stateInQueryParameter = "stateInQueryParameter";

        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_OR_REQUEST_URI);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", stateInRequestObject, "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response1 = oauth.loginForm().request(oidcClientEndpointsResource.getRequest()).state(stateInQueryParameter).doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response1.getCode());
        Assertions.assertEquals(stateInRequestObject, response1.getState());
        Assertions.assertTrue(driver.getCurrentUrl().startsWith(oauth.getRedirectUri()));

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectClientIdAndResponseTypeTest() {
        // Test that "client_id" mandatory in the query even if set in the "request" object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "some-state", "none");
        String request = oidcClientEndpointsResource.getRequest();
        oauth.client(null);
        oauth.loginForm().request(request).state("some-state").open();
        errorPage.assertCurrent();

        // Test that "response_type" mandatory in the query even if set in the "request" object
        oauth.client("test-app", "password");
        oauth.responseType(null);
        oauth.loginForm().request(request).state("some-state").open();
        Assertions.assertTrue(oauth.parseLoginResponse().isError());
        AuthorizationEndpointResponse authorizationEndpointResponse = oauth.parseLoginResponse();
        Assertions.assertEquals("invalid_request", authorizationEndpointResponse.getError());
        Assertions.assertEquals("some-state", authorizationEndpointResponse.getState());

        // Test that different "client_id" in the query and in the request object is disallowed
        oauth.client("test-app-scope");
        oauth.responseType(OAuth2Constants.CODE);
        oauth.loginForm().request(request).state("some-state").open();
        errorPage.assertCurrent();

        // Test that different "response_type" in the query and in the request object is disallowed
        oauth.client("test-app", "password");
        oauth.responseType(OAuth2Constants.CODE + " " + OAuth2Constants.ID_TOKEN);
        oauth.loginForm().request(request).state("some-state").open();
        oauth.responseMode("query"); // Keycloak falls back to query in this case
        Assertions.assertTrue(oauth.parseLoginResponse().isError());
        authorizationEndpointResponse = oauth.parseLoginResponse();
        oauth.responseMode(null);
        Assertions.assertEquals("invalid_request", authorizationEndpointResponse.getError());
        Assertions.assertEquals("some-state", authorizationEndpointResponse.getState());

        // Test that "client_id" and "response_type" are not mandatory in the request object
        Map<String, Object> oidcRequest = new HashMap<>();
        oidcRequest.put(OIDCLoginProtocol.REDIRECT_URI_PARAM, oauth.getRedirectUri());
        oidcRequest.put(OIDCLoginProtocol.STATE_PARAM, "request-state");
        String requestObjectString = new JWSBuilder().jsonContent(oidcRequest).none();

        oauth.client("test-app", "password");
        oauth.responseType(OAuth2Constants.CODE);
        AuthorizationEndpointResponse response1 = oauth.loginForm().request(requestObjectString).state("some-state").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response1.getCode());
        Assertions.assertEquals("request-state", response1.getState());
        Assertions.assertTrue(driver.getCurrentUrl().startsWith(oauth.getRedirectUri()));
    }

    @Test
    public void requestObjectRequiredProvidedInRequestUriParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_OR_REQUEST_URI);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "mystate2", "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response2 = oauth.loginForm().requestUri(requestObjects.getUri()).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response2.getCode());
        Assertions.assertEquals("mystate2", response2.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredAsRequestParamNotProvided() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST);
        clientResource.update(clientRep);

        // Send request without request object
        // Assert that the request is not accepted
        oauth.loginForm().state("mystate2").open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredAsRequestParamProvidedInRequestParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "mystate2", "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response1 = oauth.loginForm().request(oidcClientEndpointsResource.getRequest()).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response1.getCode());
        Assertions.assertEquals("mystate2", response1.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredAsRequestParamProvidedInRequestUriParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "none");

        // Assert that the request is accepted
        oauth.loginForm().requestUri(requestObjects.getUri()).state("mystate2").open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredAsRequestUriParamNotProvided() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_URI);
        clientResource.update(clientRep);

        // Send request without request object
        // Assert that the request is not accepted
        oauth.loginForm().state("mystate2").open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredAsRequestUriParamProvidedInRequestParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_URI);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "none");

        // Assert that the request is not accepted
        oauth.loginForm().request(oidcClientEndpointsResource.getRequest()).state("mystate2").open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestObjectRequiredAsRequestUriParamProvidedInRequestUriParam() {
        // Set request object not required for client
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(OIDCConfigAttributes.REQUEST_OBJECT_REQUIRED_REQUEST_URI);
        clientResource.update(clientRep);

        // Set up a request object
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", oauth.getRedirectUri(), "10", "mystate2", "none");

        // Assert that the request is accepted
        AuthorizationEndpointResponse response1 = oauth.loginForm().requestUri(requestObjects.getUri()).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response1.getCode());
        Assertions.assertEquals("mystate2", response1.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        // Revert requiring request object for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectRequired(null);
        clientResource.update(clientRep);
    }

    @Test
    public void requestParamUnsigned() {
        String validRedirectUri = oauth.getRedirectUri();
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;

        // Send request object with invalid redirect uri.
        oidcClientEndpointsResource.setRequest("test-app", "http://invalid", null, "none");
        String requestStr = oidcClientEndpointsResource.getRequest();

        oauth.loginForm().request(requestStr).state("mystate2").open();
        errorPage.assertCurrent();
        assertEquals("Invalid parameter: redirect_uri", errorPage.getError());

        // Assert the value from request object has bigger priority then from the query parameter.
        oauth.redirectUri("http://invalid");
        oidcClientEndpointsResource.setRequest("test-app", validRedirectUri, "10", "mystate2", "none");
        requestStr = oidcClientEndpointsResource.getRequest();

        AuthorizationEndpointResponse response = oauth.loginForm().request(requestStr).state("mystate2").doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response.getCode());
        Assertions.assertEquals("mystate2", response.getState());
        Assertions.assertTrue(driver.getCurrentUrl().startsWith(validRedirectUri));
    }

    @Test
    public void requestUriParamUnsigned() {
        String validRedirectUri = oauth.getRedirectUri();
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;

        // Send request object with invalid redirect uri.
        oidcClientEndpointsResource.setRequest("test-app", "http://invalid", null, "mystate1", "none");

        String requestUri = requestObjects.getUri();

        oauth.loginForm().requestUri(requestUri).open();
        errorPage.assertCurrent();
        assertEquals("Invalid parameter: redirect_uri", errorPage.getError());

        // Assert the value from request object has bigger priority then from the query parameter.
        oauth.redirectUri("http://invalid");
        oidcClientEndpointsResource.setRequest("test-app", validRedirectUri, "10", "mystate1", "none");

        AuthorizationEndpointResponse response = oauth.loginForm().requestUri(requestUri).doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response.getCode());
        Assertions.assertEquals("mystate1", response.getState());
        Assertions.assertFalse(oauth.parseLoginResponse().isRedirected());
    }

    @Test
    public void requestUriParamWithAllowedRequestUris() {
        String validRedirectUri = oauth.getRedirectUri();
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;
        oidcClientEndpointsResource.setRequest("test-app", validRedirectUri, "10", "mystate1", "none");

        String loginRequestUri = requestObjects.getUri();

        // Test with the relative allowed request_uri - should pass
        String absoluteRequestUri = requestObjects.getUri();
        String requestUri = absoluteRequestUri.substring(UriUtils.getOrigin(absoluteRequestUri).length());
        setRequestUris(requestUri);

        oauth.loginForm().requestUri(loginRequestUri).open();
        loginPage.assertCurrent();

        // Test with the relative and star at the end - should pass
        requestUri = requestUri.replace("/get-oidc-request", "/*");
        setRequestUris(requestUri);

        oauth.loginForm().requestUri(loginRequestUri).open();
        loginPage.assertCurrent();

        // Test absolute and wildcard at the end - should pass
        requestUri = absoluteRequestUri.replace("/get-oidc-request", "/*");
        setRequestUris(requestUri);

        oauth.loginForm().requestUri(loginRequestUri).open();
        loginPage.assertCurrent();

        // Test star only as wildcard - should pass
        setRequestUris("*");

        oauth.loginForm().requestUri(loginRequestUri).open();
        loginPage.assertCurrent();

        // Test with multiple request_uris - should pass
        setRequestUris("/foo", requestUri);

        oauth.loginForm().requestUri(loginRequestUri).open();
        loginPage.assertCurrent();

        // Test invalid request_uris - should fail
        setRequestUris("/foo", requestUri.replace("/*", "/foo"));

        oauth.loginForm().requestUri(loginRequestUri).open();
        errorPage.assertCurrent();

        // Test with no request_uri set at all - should fail
        setRequestUris();

        oauth.loginForm().requestUri(loginRequestUri).open();
        errorPage.assertCurrent();

        // Revert
        setRequestUris(requestObjects.getUri());
    }

    @Test
    public void requestUriParamSigned() {
        String validRedirectUri = oauth.getRedirectUri();
        RequestObjectProvider oidcClientEndpointsResource = requestObjects;

        // Set required signature for request_uri
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectSignatureAlg(Algorithm.RS256);
        clientResource.update(clientRep);

        String requestUri = requestObjects.getUri();

        // Verify unsigned request_uri will fail
        oidcClientEndpointsResource.setRequest("test-app", validRedirectUri, "10", "none");
        oauth.loginForm().requestUri(requestUri).open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());

        // Generate keypair for client
        String clientPublicKeyPem = oidcClientEndpointsResource.generateKeys("RS256").get(JwksProvider.PUBLIC_KEY);

        // Verify signed request_uri will fail due to failed signature validation
        oidcClientEndpointsResource.setRequest("test-app", validRedirectUri, "10", "mystate3", Algorithm.RS256);
        oauth.loginForm().requestUri(requestUri).open();
        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());


        // Update clientModel with publicKey for signing
        clientRep = clientResource.toRepresentation();
        CertificateRepresentation cert = new CertificateRepresentation();
        cert.setPublicKey(clientPublicKeyPem);
        CertificateInfoHelper.updateClientRepresentationCertificateInfo(clientRep, cert, JWTClientAuthenticator.ATTR_PREFIX);
        clientResource.update(clientRep);

        // set time offset, so that new keys are downloaded
        timeOffSet.set(20);

        // Check signed request_uri will pass
        AuthorizationEndpointResponse response = oauth.loginForm().requestUri(requestUri).doLogin("test-user@localhost", "password");
        Assertions.assertNotNull(response.getCode());
        Assertions.assertEquals("mystate3", response.getState());
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        // Revert requiring signature for client
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectSignatureAlg(null);
        clientResource.update(clientRep);
    }

    private void requestUriParamSignedIn(String expectedAlgorithm, String actualAlgorithm) {
        requestUriParamSignedIn(expectedAlgorithm, actualAlgorithm, null);
    }

    private void requestUriParamSignedIn(String expectedAlgorithm, String actualAlgorithm, String curve) {
        ClientResource clientResource = null;
        ClientRepresentation clientRep = null;
        try {
            String validRedirectUri = oauth.getRedirectUri();
            RequestObjectProvider oidcClientEndpointsResource = requestObjects;

            // Set required signature for request_uri
            clientResource = oauth.clientResource();
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectSignatureAlg(expectedAlgorithm);
            clientResource.update(clientRep);

            // generate and register client keypair
            if (!"none".equals(actualAlgorithm)) oidcClientEndpointsResource.generateKeys(actualAlgorithm, curve);

            // register request object
            oidcClientEndpointsResource.setRequest("test-app", validRedirectUri, "10", "mystate3", actualAlgorithm);

            // use and set jwks_url
            clientResource = oauth.clientResource();
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setUseJwksUrl(true);
            String jwksUrl = jwksProvider.getUri();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setJwksUrl(jwksUrl);
            clientResource.update(clientRep);

            // set time offset, so that new keys are downloaded
            timeOffSet.set(20);

            oauth.realm(managedRealm.getName());
            oauth.client("test-app", "password");
            String requestUri = requestObjects.getUri();
            if (expectedAlgorithm == null || expectedAlgorithm.equals(actualAlgorithm)) {
                // Check signed request_uri will pass
                AuthorizationEndpointResponse response = oauth.loginForm().requestUri(requestUri).doLogin("test-user@localhost", "password");
                Assertions.assertNotNull(response.getCode());
                Assertions.assertEquals("mystate3", response.getState());
                Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
            } else {
                // Verify signed request_uri will fail due to failed signature validation
                oauth.loginForm().requestUri(requestUri).open();
                errorPage.assertCurrent();
                assertEquals("Invalid Request", errorPage.getError());
            }

        } finally {
            // Revert requiring signature for client
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectSignatureAlg(null);
            // Revert jwks_url settings
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setUseJwksUrl(false);
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setJwksUrl(null);
            clientResource.update(clientRep);
        }
    }

    @Test
    public void requestUriParamSignedExpectedES256ActualRS256() {
        // will fail
        requestUriParamSignedIn(Algorithm.ES256, Algorithm.RS256);
    }

    @Test
    public void requestUriParamSignedExpectedNoneActualES256() {
        // will fail
        requestUriParamSignedIn("none", Algorithm.ES256);
    }

    @Test
    public void requestUriParamSignedExpectedNoneActualNone() {
        // will success
        requestUriParamSignedIn("none", "none");
    }

    @Test
    public void requestUriParamSignedExpectedES256ActualES256() {
        // will success
        requestUriParamSignedIn(Algorithm.ES256, Algorithm.ES256);
    }

    @Test
    public void requestUriParamSignedExpectedES384ActualES384() {
        // will success
        requestUriParamSignedIn(Algorithm.ES384, Algorithm.ES384);
    }

    @Test
    public void requestUriParamSignedExpectedES512ActualES512() {
        // will success
        requestUriParamSignedIn(Algorithm.ES512, Algorithm.ES512);
    }

    @Test
    public void requestUriParamSignedExpectedRS384ActualRS384() {
        // will success
        requestUriParamSignedIn(Algorithm.RS384, Algorithm.RS384);
    }

    @Test
    public void requestUriParamSignedExpectedRS512ActualRS512() {
        // will success
        requestUriParamSignedIn(Algorithm.RS512, Algorithm.RS512);
    }

    @Test
    public void requestUriParamSignedExpectedPS256ActualPS256() {
        // will success
        requestUriParamSignedIn(Algorithm.PS256, Algorithm.PS256);
    }

    @Test
    public void requestUriParamSignedExpectedPS384ActualPS384() {
        // will success
        requestUriParamSignedIn(Algorithm.PS384, Algorithm.PS384);
    }

    @Test
    public void requestUriParamSignedExpectedPS512ActualPS512() {
        // will success
        requestUriParamSignedIn(Algorithm.PS512, Algorithm.PS512);
    }

    @Test
    public void requestUriParamSignedExpectedEd25519ActualEd25519() throws Exception {
        // will success
        requestUriParamSignedIn(Algorithm.EdDSA, Algorithm.EdDSA, Algorithm.Ed25519);
    }

    @Test
    public void requestUriParamSignedExpectedES256ActualEd448() throws Exception {
        // will fail
        requestUriParamSignedIn(Algorithm.ES256, Algorithm.EdDSA, Algorithm.Ed448);
    }

    @Test
    public void requestUriParamSignedExpectedAnyActualES256() throws Exception {
        // Algorithm is null if 'any'
        // will success
        requestUriParamSignedIn(null, Algorithm.ES256);
    }

    // LOGIN_HINT

    @Test
    public void loginHint() {
        // Assert need to re-authenticate with prompt=login
        oauth.loginForm().loginHint("test-user%40localhost").open();

        loginPage.assertCurrent();
        Assertions.assertEquals("test-user@localhost", loginPage.getUsername());
        loginPage.fillPassword("password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        EventAssertion.expectLoginSuccess(events.poll()).details(Details.USERNAME, "test-user@localhost");
    }

    // CLAIMS
    // included in the session client notes, so custom providers can make use of it

    @Test
    public void processClaimsQueryParam() throws IOException {
        Map<String, Object> claims = ImmutableMap.of(
                "id_token", ImmutableMap.of(
                        "test_claim", ImmutableMap.of(
                                "essential", true)));

        String claimsJson = JsonSerialization.writeValueAsString(claims);

        oauth.loginForm().param(OIDCLoginProtocol.CLAIMS_PARAM, URLEncoder.encode(claimsJson, StandardCharsets.UTF_8)).open();

        // need to login so session id can be read from event
        loginPage.assertCurrent();
        loginPage.fillLogin("test-user@localhost", "password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        String sessionId = loginEvent.getSessionId();
        String clientId = loginEvent.getClientId();

        runOnServer.run(session -> {
            RealmModel realmModel = session.getContext().getRealm();
            String clientUuid = realmModel.getClientByClientId(clientId).getId();
            UserSessionModel userSession = session.sessions().getUserSession(realmModel, sessionId);
            AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessions().get(clientUuid);

            String claimsInSession = clientSession.getNote(OIDCLoginProtocol.CLAIMS_PARAM);
            assertEquals(claimsJson, claimsInSession);
        });
    }

    @Test
    public void processClaimsRequestParam() throws Exception {
        Map<String, Object> claims = ImmutableMap.of(
                "id_token", ImmutableMap.of(
                        "test_claim", ImmutableMap.of(
                                "essential", true)));

        String claimsJson = JsonSerialization.writeValueAsString(claims);

        Map<String, Object> oidcRequest = new HashMap<>();
        oidcRequest.put(OIDCLoginProtocol.CLIENT_ID_PARAM, "test-app");
        oidcRequest.put(OIDCLoginProtocol.RESPONSE_TYPE_PARAM, OAuth2Constants.CODE);
        oidcRequest.put(OIDCLoginProtocol.REDIRECT_URI_PARAM, oauth.getRedirectUri());
        oidcRequest.put(OIDCLoginProtocol.CLAIMS_PARAM, claims);

        String request = new JWSBuilder().jsonContent(oidcRequest).none();

        oauth.loginForm().param(OIDCLoginProtocol.REQUEST_PARAM, request).open();

        // need to login so session id can be read from event
        loginPage.assertCurrent();
        loginPage.fillLogin("test-user@localhost", "password");
        loginPage.submit();
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        EventRepresentation loginEvent = events.poll();
        EventAssertion.expectLoginSuccess(loginEvent).details(Details.USERNAME, "test-user@localhost");
        String sessionId = loginEvent.getSessionId();
        String clientId = loginEvent.getClientId();

        runOnServer.run(session -> {
            RealmModel realmModel = session.getContext().getRealm();
            String clientUuid = realmModel.getClientByClientId(clientId).getId();
            UserSessionModel userSession = session.sessions().getUserSession(realmModel, sessionId);
            AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessionByClient(clientUuid);

            String claimsInSession = clientSession.getNote(OIDCLoginProtocol.CLAIMS_PARAM);
            assertEquals(claimsJson, claimsInSession);
        });
    }

    @Test
    public void processClaimsRequestParamSupported() {
        String clientScopeId = null;
        try {
            for (ClientScopeRepresentation rep : managedRealm.admin().clientScopes().findAll()) {
                if (rep.getName().equals("profile")) {
                    clientScopeId = rep.getId();
                    break;
                }
            }
            oauth.clientResource().removeDefaultClientScope(clientScopeId);

            ClientResource app = oauth.clientResource();
            ProtocolMappersResource res = app.getProtocolMappers();
            res.createMapper(ModelToRepresentation.toRepresentation(ClaimsParameterTokenMapper.createMapper("claimsParameterTokenMapper", true, false))).close();

            Map<String, Object> claims = ImmutableMap.of(
                "id_token", ImmutableMap.of(
                        "email", ImmutableMap.of("essential", true),
                        "preferred_username", ImmutableMap.of("essential", true),
                        "family_name", ImmutableMap.of("essential", false),
                        "given_name", ImmutableMap.of("wesentlich", true),
                        "name", ImmutableMap.of("essential", true)),
                "userinfo", ImmutableMap.of(
                        "preferred_username", ImmutableMap.of("essential", "Ja"),
                        "family_name", ImmutableMap.of("essential", true),
                        "given_name", ImmutableMap.of("essential", true)));
            Map<String, Object> oidcRequest = new HashMap<>();

            oidcRequest.put(OIDCLoginProtocol.CLIENT_ID_PARAM, "test-app");
            oidcRequest.put(OIDCLoginProtocol.RESPONSE_TYPE_PARAM, OAuth2Constants.CODE);
            oidcRequest.put(OIDCLoginProtocol.REDIRECT_URI_PARAM, oauth.getRedirectUri());
            oidcRequest.put(OIDCLoginProtocol.CLAIMS_PARAM, claims);
            oidcRequest.put(OIDCLoginProtocol.SCOPE_PARAM, "openid");
            String request = new JWSBuilder().jsonContent(oidcRequest).none();

            oauth.loginForm().request(request).doLogin("test-user@localhost", "password");
            EventRepresentation loginEvent = events.poll();
            EventAssertion.expectLoginSuccess(loginEvent);

            AccessTokenResponse accessTokenResponse = sendTokenRequestAndGetResponse(loginEvent);
            IDToken idToken = oauth.verifyIDToken(accessTokenResponse.getIdToken());
            assertEquals("test-user@localhost", idToken.getEmail());
            assertEquals("test-user@localhost", idToken.getPreferredUsername());
            assertNull(idToken.getFamilyName());
            assertNull(idToken.getGivenName());
            assertEquals("Tom Brady", idToken.getName());

            {
                UserInfo userInfo = oauth.doUserInfoRequest(accessTokenResponse.getAccessToken()).getUserInfo();
                assertEquals("test-user@localhost", userInfo.getEmail());
                assertNull(userInfo.getPreferredUsername());
                assertEquals("Brady", userInfo.getFamilyName());
                assertEquals("Tom", userInfo.getGivenName());
                assertNull(userInfo.getName());
            }
            EventAssertion.assertSuccess(events.poll()).type(EventType.USER_INFO_REQUEST).sessionId(accessTokenResponse.getSessionState()).clientId("test-app");

            oauth.doLogout(accessTokenResponse.getRefreshToken());
            EventAssertion.assertSuccess(events.poll()).type(EventType.LOGOUT)
                    .sessionId(accessTokenResponse.getSessionState()).clientId(oauth.getClientId()).withoutDetails(Details.REDIRECT_URI);


            claims = ImmutableMap.of(
                    "id_token", ImmutableMap.of(
                            "test_claim", ImmutableMap.of(
                                    "essential", true)),
                    "access_token", ImmutableMap.of(
                            "email", ImmutableMap.of("essential", true),
                            "preferred_username", ImmutableMap.of("essential", true),
                            "family_name", ImmutableMap.of("essential", true),
                            "given_name", ImmutableMap.of("essential", true),
                            "name", ImmutableMap.of("essential", true)));
            oidcRequest = new HashMap<>();
            oidcRequest.put(OIDCLoginProtocol.CLIENT_ID_PARAM, "test-app");
            oidcRequest.put(OIDCLoginProtocol.RESPONSE_TYPE_PARAM, OAuth2Constants.CODE);
            oidcRequest.put(OIDCLoginProtocol.REDIRECT_URI_PARAM, oauth.getRedirectUri());
            oidcRequest.put(OIDCLoginProtocol.CLAIMS_PARAM, claims);
            oidcRequest.put(OIDCLoginProtocol.SCOPE_PARAM, "openid");
            request = new JWSBuilder().jsonContent(oidcRequest).none();

            oauth.loginForm().request(request).doLogin("test-user@localhost", "password");
            loginEvent = events.poll();
            EventAssertion.expectLoginSuccess(loginEvent);

            accessTokenResponse = sendTokenRequestAndGetResponse(loginEvent);
            idToken = oauth.verifyIDToken(accessTokenResponse.getIdToken());
            assertEquals("test-user@localhost", idToken.getEmail()); // "email" default scope still remains
            assertNull(idToken.getPreferredUsername());
            assertNull(idToken.getFamilyName());
            assertNull(idToken.getGivenName());
            assertNull(idToken.getName());

            {
                UserInfo userInfo = oauth.doUserInfoRequest(accessTokenResponse.getAccessToken()).getUserInfo();
                assertEquals("test-user@localhost", userInfo.getEmail());
                assertNull(userInfo.getPreferredUsername());
                assertNull(userInfo.getFamilyName());
                assertNull(userInfo.getGivenName());
                assertNull(userInfo.getName());
            }

        } finally {
            // revert "profile" default client scope
            oauth.clientResource().addDefaultClientScope(clientScopeId);
        }
    }

    @Test
    public void testSignedRequestObject() throws IOException {
        oauth.loginForm().request(createAndSignRequestObject()).doLogin("test-user@localhost", "password");
        EventAssertion.expectLoginSuccess(events.poll());
    }

    @Test
    public void testWrongEncryptionAlgorithm() throws Exception {
        try {
            ClientResource clientResource = oauth.clientResource();
            ClientRepresentation clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionAlg(RSA_OAEP_256);
            clientResource.update(clientRep);
            oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP)).doLogin("test-user@localhost", "password");
            fail("Should fail due to invalid encryption algorithm");
        } catch (Exception ignore) {
            errorPage.assertCurrent();
            oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP_256)).doLogin("test-user@localhost", "password");
            Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
        } finally {
            ClientResource clientResource = oauth.clientResource();
            ClientRepresentation clientRep = clientResource.toRepresentation();
            // The admin update ignores null values; an empty value removes the attribute.
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionAlg("");
            clientResource.update(clientRep);
        }

        String code = oauth.parseLoginResponse().getCode();
        String idTokenHint = oauth.doAccessTokenRequest(code).getIdToken();
        oauth.logoutForm().idTokenHint(idTokenHint).open();
        oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP_256)).doLogin("test-user@localhost", "password");
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
    }

    @Test
    public void testWrongContentEncryptionAlgorithm() throws Exception {
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        try {
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionAlg(RSA_OAEP_256);
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionEnc(JWEConstants.A192GCM);
            clientResource.update(clientRep);
            clientRep = clientResource.toRepresentation();
            assertEquals(JWEConstants.A192GCM, OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).getRequestObjectEncryptionEnc());
            oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP_256)).doLogin("test-user@localhost", "password");
            fail("Should fail due to invalid content encryption algorithm");
        } catch (Exception ignore) {
            errorPage.assertCurrent();
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionEnc(JWEConstants.A256GCM);
            clientResource.update(clientRep);
            clientRep = clientResource.toRepresentation();
            assertEquals(JWEConstants.A256GCM, OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).getRequestObjectEncryptionEnc());
            oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP_256)).doLogin("test-user@localhost", "password");
            Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());
        } finally {
            clientResource = oauth.clientResource();
            clientRep = clientResource.toRepresentation();
            // The admin update ignores null values; empty values remove the attributes.
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionAlg("");
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectEncryptionEnc("");
            clientResource.update(clientRep);
        }

        String code = oauth.parseLoginResponse().getCode();
        String idTokenHint = oauth.doAccessTokenRequest(code).getIdToken();
        oauth.logoutForm().idTokenHint(idTokenHint).open();
        oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP_256)).doLogin("test-user@localhost", "password");
        Assertions.assertTrue(oauth.parseLoginResponse().isSuccess());

        clientRep = clientResource.toRepresentation();
        assertNull(OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).getRequestObjectEncryptionAlg());
        assertNull(OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).getRequestObjectEncryptionEnc());
    }

    @Test
    public void testSignedAndEncryptedRequestObject() throws IOException, JWEException {
        oauth.loginForm().request(createEncryptedRequestObject(RSA_OAEP_256)).doLogin("test-user@localhost", "password");
        EventAssertion.expectLoginSuccess(events.poll());
    }

    private String createEncryptedRequestObject(String encAlg) throws IOException, JWEException {
        JSONWebKeySet jsonWebKeySet = oauth.keys().getRealmKeys();
        Map<String, PublicKey> keysForUse = JWKSUtils.getKeysForUse(jsonWebKeySet, JWK.Use.ENCRYPTION);
        KeysMetadataRepresentation.KeyMetadataRepresentation encKey = KeyUtils
                .findActiveEncryptingKey(managedRealm.admin(), encAlg);
        String keyId = encKey.getKid();

        PublicKey decryptionKEK = keysForUse.get(keyId);
        JWE jwe = new JWE()
                .header(new JWEHeader(encAlg, JWEConstants.A256GCM, null))
                .content(createAndSignRequestObject().getBytes(StandardCharsets.UTF_8));

        jwe.getKeyStorage()
                .setEncryptionKey(decryptionKEK);

        return jwe.encodeJwe();
    }

    @Test
    public void testRealmPublicKeyEncryptedRequestObjectUsingRSA_OAEP_256WithA256GCM() throws Exception {
        assertRequestObjectEncryption(new JWEHeader(RSA_OAEP_256, JWEConstants.A256GCM, null));
    }

    @Test
    public void testRealmPublicKeyEncryptedRequestObjectUsingRSA_OAEPWithA128CBC_HS256() throws Exception {
        assertRequestObjectEncryption(new JWEHeader(RSA_OAEP, JWEConstants.A128CBC_HS256, null));
    }

    @Test
    public void testRealmPublicKeyEncryptedRequestObjectUsingKid() throws Exception {
        KeysMetadataRepresentation.KeyMetadataRepresentation encKey = KeyUtils.findActiveEncryptingKey(managedRealm.admin(),
                Algorithm.RSA_OAEP);
        JWEHeader jweHeader = new JWEHeader(RSA_OAEP, JWEConstants.A128CBC_HS256, null, encKey.getKid());
        assertRequestObjectEncryption(jweHeader);
    }

    /**
     * A JWE whose header {@code alg} differs from the provisioned algorithm of the decryption key must be rejected.
     */
    @Test
    public void testRequestObjectEncryptedWithWrongKeyManagementAlgorithmIsRejected() throws Exception {
        KeysMetadataRepresentation.KeyMetadataRepresentation encKey =
                KeyUtils.findActiveEncryptingKey(managedRealm.admin(), Algorithm.RSA_OAEP);

        oauth.loginForm().request(encryptRequestObject(
                new JWEHeader(JWEConstants.RSA1_5, JWEConstants.A128CBC_HS256, null, encKey.getKid()), encKey.getKid())).open();

        errorPage.assertCurrent();
        assertEquals("Invalid Request", errorPage.getError());
    }

    /**
     * A passive key is still advertised in the JWKS, so it must be usable when referenced by {@code kid},
     * but must not be picked when the {@code kid} is missing.
     */
    @Test
    public void testRequestObjectEncryptedWithPassiveKey() throws Exception {
        KeysMetadataRepresentation.KeyMetadataRepresentation encKey =
                KeyUtils.findActiveEncryptingKey(managedRealm.admin(), Algorithm.RSA_OAEP);
        setKeyActive(encKey.getProviderId(), false);
        try {
            oauth.loginForm().request(encryptRequestObject(
                    new JWEHeader(RSA_OAEP, JWEConstants.A128CBC_HS256, null, encKey.getKid()), encKey.getKid())).open();
            loginPage.assertCurrent();

            oauth.loginForm().request(encryptRequestObject(
                    new JWEHeader(RSA_OAEP, JWEConstants.A128CBC_HS256, null), encKey.getKid())).open();
            errorPage.assertCurrent();
        } finally {
            setKeyActive(encKey.getProviderId(), true);
        }
    }

    private void setKeyActive(String componentId, boolean active) {
        ComponentRepresentation component = managedRealm.admin().components().component(componentId).toRepresentation();
        component.getConfig().putSingle(Attributes.ACTIVE_KEY, String.valueOf(active));
        managedRealm.admin().components().component(componentId).update(component);
    }

    private String encryptRequestObject(JWEHeader header, String publicKeyKid) throws IOException, JWEException {
        JWE jwe = new JWE().header(header).content(createAndSignRequestObject().getBytes(StandardCharsets.UTF_8));
        jwe.getKeyStorage().setEncryptionKey(
                JWKSUtils.getKeysForUse(oauth.keys().getRealmKeys(), JWK.Use.ENCRYPTION).get(publicKeyKid));
        return jwe.encodeJwe();
    }

    private String createAndSignRequestObject() throws IOException {
        Map<String, Object> requestObject = createRequestObject();

        RequestObjectProvider client = requestObjects;

        // use and set jwks_url
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setUseJwksUrl(true);
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setJwksUrl(jwksProvider.getUri());
        clientResource.update(clientRep);
        client.generateKeys(Algorithm.RS256);
        client.registerRequest(requestObject, Algorithm.RS256);

        return client.getRequest();
    }

    private void assertRequestObjectEncryption(JWEHeader jweHeader) throws Exception {
        Map<String, Object> requestObject = createRequestObject();

        byte[] contentBytes = JsonSerialization.writeValueAsBytes(requestObject);
        JSONWebKeySet jsonWebKeySet = oauth.keys().getRealmKeys();
        Map<String, PublicKey> keysForUse = JWKSUtils.getKeysForUse(jsonWebKeySet, JWK.Use.ENCRYPTION);
        String keyId = jweHeader.getKeyId();

        if (keyId == null) {
            KeysMetadataRepresentation.KeyMetadataRepresentation encKey = KeyUtils.findActiveEncryptingKey(managedRealm.admin(),
                    jweHeader.getAlgorithm());
            keyId = encKey.getKid();
        }

        PublicKey decryptionKEK = keysForUse.get(keyId);
        JWE jwe = new JWE()
                .header(jweHeader)
                .content(contentBytes);

        jwe.getKeyStorage()
                .setEncryptionKey(decryptionKEK);

        oauth.loginForm().request(jwe.encodeJwe()).doLogin("test-user@localhost", "password");
        EventAssertion.expectLoginSuccess(events.poll());
    }

    /**
     * When a client requires signed request objects, a JWE-encrypted request object whose
     * decrypted content is raw JSON (not a nested JWS) must be rejected.
     */
    @Test
    public void testJweWithUnsignedJsonShouldBeRejectedWhenSignatureRequired() throws Exception {
        ClientResource clientResource = oauth.clientResource();
        ClientRepresentation clientRep = clientResource.toRepresentation();
        try {
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectSignatureAlg(Algorithm.RS256);
            clientResource.update(clientRep);

            Map<String, Object> requestObject = createRequestObject();
            requestObject.put(OIDCLoginProtocol.NONCE_PARAM, KeycloakModelUtils.generateId());

            byte[] contentBytes = JsonSerialization.writeValueAsBytes(requestObject);

            JSONWebKeySet jsonWebKeySet = oauth.keys().getRealmKeys();
            Map<String, PublicKey> keysForUse = JWKSUtils.getKeysForUse(jsonWebKeySet, JWK.Use.ENCRYPTION);

            KeysMetadataRepresentation.KeyMetadataRepresentation encKey = KeyUtils
                    .findActiveEncryptingKey(managedRealm.admin(), Algorithm.RSA_OAEP);
            PublicKey encryptionKey = keysForUse.get(encKey.getKid());

            JWE jwe = new JWE().header(new JWEHeader(RSA_OAEP, JWEConstants.A256GCM, null)).content(contentBytes);
            jwe.getKeyStorage().setEncryptionKey(encryptionKey);

            oauth.loginForm().request(jwe.encodeJwe()).open();

            errorPage.assertCurrent();
            assertEquals("Invalid Request", errorPage.getError());
        } finally {
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setRequestObjectSignatureAlg(null);
            clientResource.update(clientRep);
        }
    }

    private void setConsentRequired(boolean required) {
        ClientRepresentation client = oauth.clientResource().toRepresentation();
        client.setConsentRequired(required);
        oauth.clientResource().update(client);
    }

    private void setRequestUris(String... uris) {
        ClientRepresentation client = oauth.clientResource().toRepresentation();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(client).setRequestUris(List.of(uris));
        oauth.clientResource().update(client);
    }

    private Map<String, Object> createRequestObject() {
        Map<String, Object> request = new HashMap<>();
        long issuedAt = Time.currentTime();
        request.put("jti", KeycloakModelUtils.generateId());
        request.put("iat", issuedAt);
        request.put("exp", issuedAt + 300);
        request.put("nbf", issuedAt);
        request.put(OIDCLoginProtocol.CLIENT_ID_PARAM, oauth.getClientId());
        request.put(OIDCLoginProtocol.RESPONSE_TYPE_PARAM, OAuth2Constants.CODE);
        request.put(OIDCLoginProtocol.REDIRECT_URI_PARAM, oauth.getRedirectUri());
        request.put(OIDCLoginProtocol.SCOPE_PARAM, "openid");
        return request;
    }

    private IDToken sendTokenRequestAndGetIDToken(EventRepresentation loginEvent) {
        return oauth.verifyIDToken(sendTokenRequestAndGetResponse(loginEvent).getIdToken());
    }

    private AccessTokenResponse sendTokenRequestAndGetResponse(EventRepresentation loginEvent) {
        events.clear();
        AccessTokenResponse response = oauth.doAccessTokenRequest(oauth.parseLoginResponse().getCode());
        assertEquals(200, response.getStatusCode());
        EventAssertion.expectCodeToTokenSuccess(events.poll())
                .sessionId(loginEvent.getSessionId())
                .userId(loginEvent.getUserId())
                .details(Details.CODE_ID, loginEvent.getDetails().get(Details.CODE_ID))
                .details(Details.REFRESH_TOKEN_TYPE, TokenUtil.TOKEN_TYPE_REFRESH)
                .details(Details.CLIENT_AUTH_METHOD, ClientIdAndSecretAuthenticator.PROVIDER_ID);
        return response;
    }

    public static class AdvancedRequestClientConfig implements ClientConfig {
        @Override
        public ClientBuilder configure(ClientBuilder client) {
            return client.clientId("test-app").secret("password").directAccessGrantsEnabled(true);
        }
    }

    public static class AdvancedRequestRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.registrationAllowed(true)
                    // USER_INFO_REQUEST is not persisted by default, unlike the legacy event queue.
                    .enabledEventTypes(EventType.LOGIN.name(), EventType.LOGIN_ERROR.name(),
                            EventType.CODE_TO_TOKEN.name(), EventType.LOGOUT.name(), EventType.USER_INFO_REQUEST.name())
                    .users(UserBuilder.create("test-user@localhost").password("password")
                            .email("test-user@localhost").name("Tom", "Brady"))
                    .clients(ClientBuilder.create("test-app-scope").secret("password"));
            MultivaluedHashMap<String, ComponentExportRepresentation> components = new MultivaluedHashMap<>();
            components.add(KeyProvider.class.getName(), encryptionKey(RSA_OAEP, 150));
            components.add(KeyProvider.class.getName(), encryptionKey(RSA_OAEP_256, 200));
            realm.build().setComponents(components);
            return realm;
        }

        private ComponentExportRepresentation encryptionKey(String algorithm, int priority) {
            ComponentExportRepresentation key = new ComponentExportRepresentation();
            key.setName("enc-generated-" + algorithm);
            key.setProviderId("rsa-enc-generated");
            key.setConfig(new MultivaluedHashMap<>());
            key.getConfig().putSingle(Attributes.PRIORITY_KEY, Integer.toString(priority));
            key.getConfig().putSingle(Attributes.KEY_USE, KeyUse.ENC.getSpecName());
            key.getConfig().putSingle(Attributes.ALGORITHM_KEY, algorithm);
            return key;
        }
    }
}
