/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-present IxorTalk CVBA
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.ixortalk.authorization.server.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ixortalk.authorization.server.AbstractSpringIntegrationTest;
import com.ixortalk.authorization.server.domain.UserProfile;
import com.ixortalk.authorization.server.rest.FieldDescriptions.UserProfileFields;
import org.junit.Test;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.google.common.collect.Lists.newArrayList;
import static com.ixortalk.authorization.server.TestConfigConstants.THIRD_PARTY_LOGIN_IXORTALK_CLIENT_ID;
import static com.ixortalk.authorization.server.domain.AuthorityTestBuilder.authority;
import static com.ixortalk.authorization.server.domain.LoginProvider.IXORTALK;
import static com.ixortalk.authorization.server.domain.UserProfileTestBuilder.aUserProfile;
import static com.ixortalk.test.util.Randomizer.nextString;
import static com.jayway.restassured.RestAssured.given;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document;

public class UserInfoController_GetUserInfo_IntegrationAndRestDocTest extends AbstractSpringIntegrationTest {

    private static final String REFRESHED_ACCESS_TOKEN = "refreshedAccessToken";

    @Test
    public void getUserInfo_NoProfileExists() {
        UserProfile userProfile =
                given(this.restDocSpecification)
                        .auth().preemptive().oauth2(getAccessTokenWithAuthorizationCode().getValue())
                        .when()
                        .filter(
                                document("user/get",
                                        preprocessRequest(staticUris(), prettyPrint()),
                                        preprocessResponse(prettyPrint()),
                                        requestHeaders(describeAuthorizationTokenHeader()),
                                        responseFields(
                                                UserProfileFields.NAME,
                                                UserProfileFields.EMAIL,
                                                UserProfileFields.FIRST_NAME,
                                                UserProfileFields.LAST_NAME,
                                                UserProfileFields.PROFILE_PICTURE_URL,
                                                UserProfileFields.AUTHORITIES,
                                                UserProfileFields.LOGIN_PROVIDER
                                        )
                                )
                        )
                        .get("/user")
                        .then()
                        .statusCode(HTTP_OK)
                        .extract().as(UserProfile.class);

        assertThat(userProfile)
                .isEqualToIgnoringGivenFields(
                        aUserProfile()
                                .withName(PRINCIPAL_NAME_IXORTALK)
                                .withEmail(PRINCIPAL_NAME_IXORTALK)
                                .withFirstName(FIRST_NAME_IXORTALK_PRINCIPAL)
                                .withLastName(LAST_NAME_IXORTALK_PRINCIPAL)
                                .withProfilePictureUrl(PROFILE_PICTURE_URL_IXORTALK_PRINCIPAL)
                                .withAuthorities(authority(ROLE_IXORTALK_ROLE_1), authority(ROLE_IXORTALK_ROLE_2))
                                .withLoginProvider(IXORTALK)
                                .build()
                );
    }

    @Test
    public void getUserInfo_ProfileExists() {

        userProfileRestResource.save(
                aUserProfile()
                        .withName(PRINCIPAL_NAME_IXORTALK)
                        .withEmail(PRINCIPAL_NAME_IXORTALK)
                        .withFirstName(nextString("persistedFirstName"))
                        .withLastName(nextString("persistedLastName"))
                        .withProfilePictureUrl(nextString("persistedProfilePictureUrl"))
                        .withLoginProvider(IXORTALK)
                        .build());

        UserProfile userProfile =
                given()
                        .auth().preemptive().oauth2(getAccessTokenWithAuthorizationCode().getValue())
                        .when()
                        .get("/user")
                        .then()
                        .statusCode(HTTP_OK)
                        .extract().as(UserProfile.class);

        assertThat(userProfile)
                .isEqualToIgnoringGivenFields(
                        aUserProfile()
                                .withName(PRINCIPAL_NAME_IXORTALK)
                                .withEmail(PRINCIPAL_NAME_IXORTALK)
                                .withFirstName(FIRST_NAME_IXORTALK_PRINCIPAL)
                                .withLastName(LAST_NAME_IXORTALK_PRINCIPAL)
                                .withProfilePictureUrl(PROFILE_PICTURE_URL_IXORTALK_PRINCIPAL)
                                .withAuthorities(authority(ROLE_IXORTALK_ROLE_1), authority(ROLE_IXORTALK_ROLE_2))
                                .withLoginProvider(IXORTALK)
                                .build(),
                        "id"
                );
    }

    @Test
    public void getUserInfo_InternalAccessTokenRefresh() throws JsonProcessingException {

        OAuth2AccessToken oAuth2AccessToken = getAccessTokenWithAuthorizationCode();

        given()
                .auth().preemptive().oauth2(oAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_OK);

        updateThirdPartyUserInfo();
        clearCaches();

        UserProfile userProfile =
                given()
                        .auth().preemptive().oauth2(getAccessTokenWithRefreshToken(oAuth2AccessToken.getRefreshToken()).getValue())
                        .when()
                        .get("/user")
                        .then()
                        .statusCode(HTTP_OK)
                        .extract().as(UserProfile.class);


        assertThat(userProfile.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(userProfile.getAuthorities()).containsOnly(authority(UPDATED_ROLE));
    }

    @Test
    public void getUserInfo_ThirdPartyProfileUpdated() throws JsonProcessingException {
        String accessToken = getAccessTokenWithAuthorizationCode().getValue();

        UserProfile userProfile =
                given()
                        .auth().preemptive().oauth2(accessToken)
                        .when()
                        .get("/user")
                        .then()
                        .statusCode(HTTP_OK)
                        .extract().as(UserProfile.class);

        assertThat(userProfile.getFirstName()).isEqualTo(FIRST_NAME_IXORTALK_PRINCIPAL);
        assertThat(userProfile.getAuthorities()).containsOnly(authority(ROLE_IXORTALK_ROLE_1), authority(ROLE_IXORTALK_ROLE_2));

        updateThirdPartyUserInfo();
        clearCaches();

        userProfile =
                given()
                        .auth().preemptive().oauth2(accessToken)
                        .when()
                        .get("/user")
                        .then()
                        .statusCode(HTTP_OK)
                        .extract().as(UserProfile.class);

        assertThat(userProfile.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(userProfile.getAuthorities()).containsOnly(authority(UPDATED_ROLE));
    }

    @Test
    public void getUserInfo_ThirdPartyAccessTokenRefresh() throws JsonProcessingException {
        String accessToken = getAccessTokenWithAuthorizationCode().getValue();

        given()
                .auth().preemptive().oauth2(accessToken)
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_OK);

        expirePersistedThirdPartyAccessToken();
        updateThirdPartyUserInfo();
        clearCaches();

        thirdPartyIxorTalkWireMockRule.stubFor(
                post(urlEqualTo("/oauth/token"))
                        .withRequestBody(containing("grant_type=refresh_token&refresh_token=" + IXORTALK_THIRD_PARTY_REFRESH_TOKEN))
                        .willReturn(okJson(objectMapper.writeValueAsString(createOAuth2AccessToken(REFRESHED_ACCESS_TOKEN, nextString("refreshToken"))))));

        UserProfile userProfile =
                given()
                        .auth().preemptive().oauth2(accessToken)
                        .when()
                        .get("/user")
                        .then()
                        .statusCode(HTTP_OK)
                        .extract().as(UserProfile.class);

        assertThat(userProfile.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(userProfile.getAuthorities()).containsOnly(authority(UPDATED_ROLE));
        assertThat(thirdPartyTokenStore.findTokensByClientIdAndUserName(THIRD_PARTY_LOGIN_IXORTALK_CLIENT_ID.configValue(), PRINCIPAL_NAME_IXORTALK))
                .hasSize(1)
                .extracting(OAuth2AccessToken::getValue)
                .containsExactly(REFRESHED_ACCESS_TOKEN);
    }

    @Test
    public void getUserInfo_Cached() {
        OAuth2AccessToken oAuth2AccessToken = getAccessTokenWithAuthorizationCode();
        given()
                .auth().preemptive().oauth2(oAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(UserProfile.class);
        given()
                .auth().preemptive().oauth2(oAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(UserProfile.class);

        List<String> expectedInvocations = newArrayList("during login", "actual first /user call");

        thirdPartyIxorTalkWireMockRule.verify(expectedInvocations.size(), getRequestedFor(urlPathEqualTo("/user-info")));
    }

    @Test
    public void getUserInfo_CacheExpiry() throws InterruptedException {
        OAuth2AccessToken oAuth2AccessToken = getAccessTokenWithAuthorizationCode();
        given()
                .auth().preemptive().oauth2(oAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(UserProfile.class);

        sleep(ixorTalkConfigProperties.getSecurity().getUserInfoCache().getTtlInSeconds() * 1000);

        given()
                .auth().preemptive().oauth2(oAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_OK)
                .extract().as(UserProfile.class);

        List<String> expectedInvocations = newArrayList("during login", "actual first /user call", "after expiry /user call");

        thirdPartyIxorTalkWireMockRule.verify(expectedInvocations.size(), getRequestedFor(urlPathEqualTo("/user-info")));
    }

    @Test
    public void getUserInfo_NoStoredThirdPartyToken() {

        OAuth2AccessToken oAuth2AccessToken = getAccessTokenWithAuthorizationCode();

        thirdPartyTokenStore.removeAccessToken(thirdPartyTokenStore.readAccessToken(IXORTALK_THIRD_PARTY_ACCESS_TOKEN));

        given()
                .auth().preemptive().oauth2(oAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_UNAUTHORIZED);
    }

    @Test
    public void getUserInfo_Refresh_NoStoredThirdPartyToken() {

        OAuth2AccessToken refreshedOAuth2AccessToken = getAccessTokenWithRefreshToken(getAccessTokenWithAuthorizationCode().getRefreshToken());

        thirdPartyTokenStore.removeAccessToken(thirdPartyTokenStore.readAccessToken(IXORTALK_THIRD_PARTY_ACCESS_TOKEN));

        given()
                .auth().preemptive().oauth2(refreshedOAuth2AccessToken.getValue())
                .when()
                .get("/user")
                .then()
                .statusCode(HTTP_UNAUTHORIZED);
    }

    private void expirePersistedThirdPartyAccessToken() {
        OAuth2AccessToken thirdPartyOAuth2AccessToken = thirdPartyTokenStore.readAccessToken(IXORTALK_THIRD_PARTY_ACCESS_TOKEN);
        OAuth2Authentication thirdPartyOAuth2Authentication = thirdPartyTokenStore.readAuthentication(thirdPartyOAuth2AccessToken);
        ((DefaultOAuth2AccessToken) thirdPartyOAuth2AccessToken).setExpiration(new Date(currentTimeMillis() - 1));
        thirdPartyTokenStore.storeAccessToken(thirdPartyOAuth2AccessToken, thirdPartyOAuth2Authentication);
    }
}