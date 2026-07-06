package com.gatewise.gateway.residency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gatewise.gateway.config.ResidencyProperties;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the pure residency decision and the disabled-mode short circuit. */
class ResidencyEnforcerTest {

  private static ResidencyEnforcer enforcer(Map<String, String> modelRegions) {
    // The tenant repository is unused by check(); pass null to keep the test pure and fast.
    return new ResidencyEnforcer(new ResidencyProperties(true, modelRegions), null);
  }

  @Test
  void aTenantWithNoPinnedRegionIsUnrestricted() {
    ResidencyEnforcer enforcer = enforcer(Map.of("bedrock/", "eu-west-1"));
    assertThatCode(() -> enforcer.check(null, "openai/gpt-4o")).doesNotThrowAnyException();
    assertThatCode(() -> enforcer.check("  ", "openai/gpt-4o")).doesNotThrowAnyException();
  }

  @Test
  void aModelInThePinnedRegionIsAllowed() {
    ResidencyEnforcer enforcer = enforcer(Map.of("bedrock/", "eu-west-1"));
    assertThatCode(() -> enforcer.check("eu-west-1", "bedrock/eu.anthropic.claude"))
        .doesNotThrowAnyException();
  }

  @Test
  void thePinnedRegionMatchesCaseInsensitively() {
    ResidencyEnforcer enforcer = enforcer(Map.of("bedrock/", "eu-west-1"));
    assertThatCode(() -> enforcer.check("EU-WEST-1", "bedrock/eu.anthropic.claude"))
        .doesNotThrowAnyException();
  }

  @Test
  void aModelInAnotherRegionIsBlocked() {
    ResidencyEnforcer enforcer = enforcer(Map.of("bedrock/", "eu-west-1", "openai/", "us-east-1"));
    assertThatThrownBy(() -> enforcer.check("eu-west-1", "openai/gpt-4o"))
        .isInstanceOf(DataResidencyException.class)
        .hasMessageContaining("us-east-1");
  }

  @Test
  void aModelWithNoConfiguredRegionFailsClosed() {
    ResidencyEnforcer enforcer = enforcer(Map.of("bedrock/", "eu-west-1"));
    assertThatThrownBy(() -> enforcer.check("eu-west-1", "mystery/model"))
        .isInstanceOf(DataResidencyException.class)
        .hasMessageContaining("no configured region");
  }

  @Test
  void theLongestMatchingPrefixWins() {
    ResidencyProperties props =
        new ResidencyProperties(true, Map.of("bedrock/", "us-east-1", "bedrock/eu.", "eu-west-1"));
    assertThat(props.regionFor("bedrock/eu.anthropic.claude")).isEqualTo("eu-west-1");
    assertThat(props.regionFor("bedrock/us.anthropic.claude")).isEqualTo("us-east-1");
    assertThat(props.regionFor("azure/gpt-4o")).isNull();
  }

  @Test
  void enforceIsANoOpWhenResidencyIsDisabled() {
    ResidencyEnforcer enforcer =
        new ResidencyEnforcer(new ResidencyProperties(false, Map.of()), null);
    // The tenant repo is null; a disabled enforcer must short-circuit before ever touching it.
    assertThatCode(() -> enforcer.enforce(UUID.randomUUID(), "openai/gpt-4o"))
        .doesNotThrowAnyException();
  }
}
