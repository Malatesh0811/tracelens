package com.tracelens.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskerTest {

    private final SecretMasker masker = new SecretMasker();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "password=hunter2                                  | password=****",
            "DB_PASSWORD: hunter2                              | DB_PASSWORD: ****",
            "\"token\": \"abc123\"                             | \"token\": \"****\"",
            "api_key=xyz789                                    | api_key=****",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.e30.x  | Authorization: Bearer ****",
            "https://admin:s3cret@internal.company.com/api     | https://****:****@internal.company.com/api",
            "jdbc:mysql://prod-db.company.com:3306/shop        | jdbc:mysql://****",
            "key sk-ant-api03-AbCdEfGhIjKlMnOpQrSt was used    | key sk-**** was used",
            "AKIAIOSFODNN7EXAMPLE                              | AKIA****",
            "mail ravi.kumar@shop.example.com failed           | mail ****@**** failed",
    })
    void hidesEachKindOfSecret(String input, String expected) {
        SecretMasker.Masked result = masker.mask(input);

        assertEquals(expected, result.text());
        assertEquals(1, result.count());
    }

    @Test
    void countsEverySecretInALongText() {
        String log = "Connecting to jdbc:mysql://db:3306/shop?user=root&password=x\n"
                + "using password=hunter2 and api_key=abc";

        SecretMasker.Masked result = masker.mask(log);

        assertEquals(3, result.count(), "the whole JDBC URL counts once, then two key=value secrets");
        assertFalse(result.text().contains("hunter2"));
        assertFalse(result.text().contains("db:3306"));
        assertFalse(result.text().contains("abc"));
    }

    @Test
    void normalErrorTextIsLeftAlone() {
        String text = "java.lang.NullPointerException at UserService.java:42";

        SecretMasker.Masked result = masker.mask(text);

        assertEquals(text, result.text());
        assertEquals(0, result.count());
    }

    @Test
    void textWithDollarSignsIsNotBrokenByTheReplacement() {
        assertTrue(masker.mask("price $5 password=a$b").text().startsWith("price $5 password=****"));
    }
}
