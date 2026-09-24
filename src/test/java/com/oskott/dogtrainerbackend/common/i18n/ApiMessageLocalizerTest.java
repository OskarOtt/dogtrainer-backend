package com.oskott.dogtrainerbackend.common.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiMessageLocalizerTest {

    private final ApiMessageLocalizer localizer = new ApiMessageLocalizer();

    @Test
    void respectsLanguageWeightsAndWildcardFallback() {
        MockHttpServletRequest request = requestWithLanguage("*;q=1, nb;q=0");

        assertThat(SupportedLocale.resolve(request)).isEqualTo(SupportedLocale.ENGLISH);
        assertThat(localizer.localize("Validation failed", request)).isEqualTo("Validation failed");
    }

    @Test
    void localizesDecimalConstraints() {
        MockHttpServletRequest request = requestWithLanguage("nb");

        assertThat(localizer.localize("must be greater than 0.0", request))
                .isEqualTo("må være større enn 0.0");
        assertThat(localizer.localize("must be less than or equal to 999.99", request))
                .isEqualTo("må være mindre enn eller lik 999.99");
    }

    @Test
    void localizesEveryResourceEntityName() {
        MockHttpServletRequest request = requestWithLanguage("nb");

        assertThat(localizer.localize("Goal not found with id: 1", request))
                .isEqualTo("Målet finnes ikke med ID: 1");
        assertThat(localizer.localize("Follow not found with id: 2", request))
                .isEqualTo("Følgeforholdet finnes ikke med ID: 2");
        assertThat(localizer.localize("Block not found with id: 3", request))
                .isEqualTo("Blokkeringen finnes ikke med ID: 3");
    }

    private MockHttpServletRequest requestWithLanguage(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", language);
        return request;
    }
}
