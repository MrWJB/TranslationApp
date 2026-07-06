package com.translationapp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SiteKeyUtil 核心路径映射与分类逻辑单元测试。
 */
class SiteKeyUtilTest {

    @Test
    void deriveSiteKeyFromSpringBootDocsUrl() {
        String siteKey = SiteKeyUtil.deriveSiteKey("https://docs.spring.io/spring-boot/reference/");
        assertThat(siteKey).isEqualTo("spring-boot-reference");
    }

    @Test
    void deriveDisplayCategoryMapsSpringBootUrl() {
        assertThat(SiteKeyUtil.deriveDisplayCategory("https://docs.spring.io/spring-boot/reference/"))
                .isEqualTo("spring-boot");
    }

    @Test
    void deriveDisplayCategoryMapsSpringFrameworkUrl() {
        assertThat(SiteKeyUtil.deriveDisplayCategory("https://docs.spring.io/spring-framework/reference/"))
                .isEqualTo("spring");
    }

    @Test
    void toStorageLocalPathPrefixesSiteKey() {
        assertThat(SiteKeyUtil.toStorageLocalPath("spring-boot", "reference/index.html"))
                .isEqualTo("spring-boot/reference/index.html");
    }

    @Test
    void navHrefToStorageLocalPathStripsAntoraComponentPrefix() {
        String localPath = SiteKeyUtil.navHrefToStorageLocalPath(
                "spring-boot-reference",
                "spring-boot/reference/actuator/index.html");
        assertThat(localPath).isEqualTo("spring-boot-reference/reference/actuator/index.html");
    }

    @Test
    void isValidSiteKeyRejectsTraversal() {
        assertThat(SiteKeyUtil.isValidSiteKey("../escape")).isFalse();
        assertThat(SiteKeyUtil.isValidSiteKey("spring-data/reference")).isTrue();
    }

    @Test
    void normalizeSiteKeyReturnsNullForInvalidInput() {
        assertThat(SiteKeyUtil.normalizeSiteKey("../escape")).isNull();
        assertThat(SiteKeyUtil.normalizeSiteKey("spring-boot")).isEqualTo("spring-boot");
    }

    @Test
    void matchesDisplayCategorySupportsSpringAlias() {
        assertThat(SiteKeyUtil.matchesDisplayCategory(
                "spring-framework-reference",
                "spring",
                "https://docs.spring.io/spring-framework/reference/"))
                .isTrue();
    }
}
