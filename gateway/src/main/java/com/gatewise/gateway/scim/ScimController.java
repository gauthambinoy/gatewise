package com.gatewise.gateway.scim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatewise.gateway.auth.TenantContext;
import com.gatewise.gateway.config.ScimProperties;
import com.gatewise.gateway.member.Member;
import com.gatewise.gateway.member.MemberRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCIM 2.0 provisioning API ({@code /scim/v2}) — lets an IdP create, read, update and deactivate
 * console members automatically, backed by the same {@code member} table the console uses.
 *
 * <p>It maps the SCIM User to our member: {@code userName} ⇄ email, {@code name}/{@code
 * displayName} ⇄ name, {@code active} ⇄ status ({@code active} / {@code inactive}); the role isn't
 * a SCIM concept, so a provisioned member gets the configured default. Every response is
 * SCIM-shaped (schemas, id, {@code meta.resourceType}, and a {@code ListResponse} with {@code
 * totalResults}); errors use the SCIM error envelope via {@link ScimException}. Auth and
 * tenant-scoping are handled upstream by {@link ScimAuthFilter}, which binds the configured tenant
 * to the request.
 */
@RestController
@RequestMapping(value = "/scim/v2", produces = ScimController.SCIM_JSON)
public class ScimController {

  static final String SCIM_JSON = "application/scim+json";

  private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
  private static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
  private static final String INACTIVE = "inactive";
  private static final String ACTIVE = "active";
  // Only the simple, mandatory SCIM filter form: userName eq "value".
  private static final Pattern USERNAME_FILTER =
      Pattern.compile("(?i)\\s*userName\\s+eq\\s+\"(.*)\"\\s*");

  private final MemberRepository members;
  private final ScimProperties properties;
  private final ObjectMapper json;

  public ScimController(MemberRepository members, ScimProperties properties, ObjectMapper json) {
    this.members = members;
    this.properties = properties;
    this.json = json;
  }

  /** Lists users, optionally filtered by {@code userName eq "x"}, with SCIM pagination. */
  @GetMapping("/Users")
  public ObjectNode listUsers(
      @RequestParam(value = "filter", required = false) String filter,
      @RequestParam(value = "startIndex", defaultValue = "1") int startIndex,
      @RequestParam(value = "count", required = false) Integer count) {

    UUID tenantId = tenantId();
    List<Member> all;
    if (filter != null && !filter.isBlank()) {
      all = filtered(filter, tenantId);
    } else {
      all = members.findByTenantIdOrderByCreatedAtAsc(tenantId);
    }

    int from = Math.max(startIndex, 1) - 1; // SCIM startIndex is 1-based
    int to = count == null ? all.size() : Math.min(all.size(), from + Math.max(count, 0));
    List<Member> page =
        from >= all.size() ? List.of() : all.subList(Math.min(from, all.size()), to);

    ArrayNode resources = json.createArrayNode();
    page.forEach(member -> resources.add(toUser(member)));

    ObjectNode body = json.createObjectNode();
    body.set("schemas", json.createArrayNode().add(LIST_SCHEMA));
    body.put("totalResults", all.size());
    body.put("startIndex", Math.max(startIndex, 1));
    body.put("itemsPerPage", page.size());
    body.set("Resources", resources);
    return body;
  }

  /** Fetches one user by id, or 404 in the SCIM error envelope. */
  @GetMapping("/Users/{id}")
  public ObjectNode getUser(@PathVariable String id) {
    return toUser(requireMember(id));
  }

  /** Creates a user; 409 ({@code uniqueness}) if the userName already exists for this tenant. */
  @PostMapping("/Users")
  public ResponseEntity<ObjectNode> createUser(@RequestBody JsonNode body) {
    UUID tenantId = tenantId();
    String userName = text(body, "userName");
    if (userName.isBlank()) {
      throw new ScimException(400, "userName is required.", "invalidValue");
    }
    if (members.findByTenantIdAndEmailIgnoreCase(tenantId, userName).isPresent()) {
      throw new ScimException(409, "A user with this userName already exists.", "uniqueness");
    }
    String name = displayName(body, userName);
    String status = readActive(body, true) ? ACTIVE : INACTIVE;
    Member saved =
        members.save(new Member(tenantId, userName, name, properties.defaultRole(), status));
    ObjectNode user = toUser(saved);
    return ResponseEntity.created(URI.create(location(saved.getId()))).body(user);
  }

  /** Replaces a user (PUT): userName, name and active are all overwritten. */
  @PutMapping("/Users/{id}")
  public ObjectNode replaceUser(@PathVariable String id, @RequestBody JsonNode body) {
    Member member = requireMember(id);
    String userName = text(body, "userName");
    if (userName.isBlank()) {
      throw new ScimException(400, "userName is required.", "invalidValue");
    }
    // A userName change must not collide with another member in the tenant.
    if (!userName.equalsIgnoreCase(member.getEmail())) {
      Optional<Member> clash =
          members.findByTenantIdAndEmailIgnoreCase(member.getTenantId(), userName);
      if (clash.isPresent() && !clash.get().getId().equals(member.getId())) {
        throw new ScimException(409, "A user with this userName already exists.", "uniqueness");
      }
      member.changeEmail(userName);
    }
    String status = readActive(body, true) ? ACTIVE : INACTIVE;
    member.update(displayName(body, userName), member.getRole(), status);
    return toUser(members.save(member));
  }

  /** Applies a PATCH; supports at least deactivation ({@code active=false}) and name changes. */
  @PatchMapping("/Users/{id}")
  public ObjectNode patchUser(@PathVariable String id, @RequestBody JsonNode body) {
    Member member = requireMember(id);
    JsonNode operations = body.path("Operations");
    if (!operations.isArray() || operations.isEmpty()) {
      throw new ScimException(400, "PATCH requires a non-empty Operations array.", "invalidValue");
    }
    String name = member.getName();
    String status = member.getStatus();
    for (JsonNode operation : operations) {
      String op = operation.path("op").asText("").toLowerCase(java.util.Locale.ROOT);
      if (!op.equals("replace") && !op.equals("add")) {
        continue; // we don't support "remove" for these attributes
      }
      String path = operation.path("path").asText("");
      JsonNode value = operation.path("value");
      if ("active".equalsIgnoreCase(path)) {
        status = asBoolean(value, true) ? ACTIVE : INACTIVE;
      } else if (path.isBlank() && value.isObject()) {
        if (value.has("active")) {
          status = value.path("active").asBoolean(true) ? ACTIVE : INACTIVE;
        }
        name = displayName(value, name);
      } else if ("displayname".equalsIgnoreCase(path) || "name.formatted".equalsIgnoreCase(path)) {
        name = value.asText(name);
      }
    }
    member.update(name, member.getRole(), status);
    return toUser(members.save(member));
  }

  /** Hard-deletes a user; returns 204. */
  @DeleteMapping("/Users/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable String id) {
    members.delete(requireMember(id));
    return ResponseEntity.noContent().build();
  }

  /** Minimal Groups read: we don't model groups, so this is always an empty ListResponse. */
  @GetMapping("/Groups")
  public ObjectNode listGroups() {
    ObjectNode body = json.createObjectNode();
    body.set("schemas", json.createArrayNode().add(LIST_SCHEMA));
    body.put("totalResults", 0);
    body.put("startIndex", 1);
    body.put("itemsPerPage", 0);
    body.set("Resources", json.createArrayNode());
    return body;
  }

  private List<Member> filtered(String filter, UUID tenantId) {
    Matcher matcher = USERNAME_FILTER.matcher(filter);
    if (!matcher.matches()) {
      throw new ScimException(
          400, "Unsupported filter; only userName eq is supported.", "invalidFilter");
    }
    return members
        .findByTenantIdAndEmailIgnoreCase(tenantId, matcher.group(1))
        .map(List::of)
        .orElseGet(List::of);
  }

  private Member requireMember(String id) {
    UUID memberId;
    try {
      memberId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new ScimException(404, "No user with id " + id + ".");
    }
    return members
        .findByIdAndTenantId(memberId, tenantId())
        .orElseThrow(() -> new ScimException(404, "No user with id " + id + "."));
  }

  // The SCIM User representation of a member.
  private ObjectNode toUser(Member member) {
    ObjectNode user = json.createObjectNode();
    user.set("schemas", json.createArrayNode().add(USER_SCHEMA));
    user.put("id", member.getId().toString());
    user.put("userName", member.getEmail());
    user.put("active", !INACTIVE.equals(member.getStatus()));

    if (member.getName() != null && !member.getName().isBlank()) {
      user.put("displayName", member.getName());
      ObjectNode name = json.createObjectNode();
      name.put("formatted", member.getName());
      user.set("name", name);
    }

    ArrayNode emails = json.createArrayNode();
    ObjectNode email = json.createObjectNode();
    email.put("value", member.getEmail());
    email.put("primary", true);
    email.put("type", "work");
    emails.add(email);
    user.set("emails", emails);

    ObjectNode meta = json.createObjectNode();
    meta.put("resourceType", "User");
    if (member.getCreatedAt() != null) {
      meta.put("created", member.getCreatedAt().toString());
      meta.put("lastModified", member.getCreatedAt().toString());
    }
    meta.put("location", location(member.getId()));
    user.set("meta", meta);
    return user;
  }

  private static String location(UUID id) {
    return "/scim/v2/Users/" + id;
  }

  private static UUID tenantId() {
    return TenantContext.require().tenantId();
  }

  private static String text(JsonNode body, String field) {
    return body.path(field).asText("").trim();
  }

  // Display name from name.formatted, then displayName, then the fallback.
  private static String displayName(JsonNode body, String fallback) {
    String formatted = body.path("name").path("formatted").asText("").trim();
    if (!formatted.isBlank()) {
      return formatted;
    }
    String display = body.path("displayName").asText("").trim();
    return display.isBlank() ? fallback : display;
  }

  private static boolean readActive(JsonNode body, boolean fallback) {
    JsonNode active = body.path("active");
    return active.isMissingNode() || active.isNull() ? fallback : active.asBoolean(fallback);
  }

  private static boolean asBoolean(JsonNode value, boolean fallback) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    return value.asBoolean(fallback);
  }
}
