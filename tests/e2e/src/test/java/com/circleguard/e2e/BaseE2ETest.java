package com.circleguard.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseE2ETest {

    protected static String BASE_URL;
    protected static int AUTH_PORT;
    protected static int IDENTITY_PORT;
    protected static int FORM_PORT;
    protected static int PROMOTION_PORT;
    protected static int DASHBOARD_PORT;
    protected static int NOTIFICATION_PORT;
    protected static int GATEWAY_PORT;
    protected static int FILE_PORT;

    @BeforeAll
    static void initConfig() {
        BASE_URL          = System.getProperty("base.url", "http://localhost");
        AUTH_PORT         = Integer.parseInt(System.getProperty("auth.port", "8180"));
        IDENTITY_PORT     = Integer.parseInt(System.getProperty("identity.port", "8083"));
        FORM_PORT         = Integer.parseInt(System.getProperty("form.port", "8086"));
        PROMOTION_PORT    = Integer.parseInt(System.getProperty("promotion.port", "8088"));
        DASHBOARD_PORT    = Integer.parseInt(System.getProperty("dashboard.port", "8084"));
        NOTIFICATION_PORT = Integer.parseInt(System.getProperty("notification.port", "8082"));
        GATEWAY_PORT      = Integer.parseInt(System.getProperty("gateway.port", "8080"));
        FILE_PORT         = Integer.parseInt(System.getProperty("file.port", "8087"));

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    protected RequestSpecification authService() {
        return RestAssured.given().baseUri(BASE_URL).port(AUTH_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification identityService() {
        return RestAssured.given().baseUri(BASE_URL).port(IDENTITY_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification formService() {
        return RestAssured.given().baseUri(BASE_URL).port(FORM_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification promotionService() {
        return RestAssured.given().baseUri(BASE_URL).port(PROMOTION_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification dashboardService() {
        return RestAssured.given().baseUri(BASE_URL).port(DASHBOARD_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification notificationService() {
        return RestAssured.given().baseUri(BASE_URL).port(NOTIFICATION_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification gatewayService() {
        return RestAssured.given().baseUri(BASE_URL).port(GATEWAY_PORT).contentType(ContentType.JSON);
    }

    protected RequestSpecification fileService() {
        return RestAssured.given().baseUri(BASE_URL).port(FILE_PORT);
    }
}
