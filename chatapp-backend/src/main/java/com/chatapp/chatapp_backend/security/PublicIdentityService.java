package com.chatapp.chatapp_backend.security;

import com.chatapp.chatapp_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Public API identities never contain the account's login email. */
@Service
public class PublicIdentityService {
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final byte[] key;
    private static final Set<String> IDENTITIES = Set.of("email", "senderEmail", "recipientEmail", "ownerEmail", "userEmail", "caller", "callee");

    public PublicIdentityService(UserRepository users, ObjectMapper mapper, @Value("${jwt.secret}") String secret) {
        this.users = users; this.mapper = mapper; this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    private String signature(String id) {
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(mac.doFinal(("public-user:" + id).getBytes(StandardCharsets.UTF_8)), 16));
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException(error); }
    }

    public String publicId(String email) {
        if (email == null || !email.contains("@")) return email;
        return users.findByEmail(email).map(user -> "usr_" + user.getId() + "_" + signature(user.getId().toString())).orElse("deleted-user");
    }

    public String resolve(String identity) {
        if (identity == null || !identity.startsWith("usr_")) return identity;
        String[] parts = identity.split("_", 3);
        if (parts.length != 3 || !parts[1].matches("[1-9][0-9]*") || !MessageDigest.isEqual(signature(parts[1]).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Contato indisponível");
        try { return users.findById(Long.valueOf(parts[1])).orElseThrow(() -> new IllegalArgumentException("Contato indisponível")).getEmail(); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Contato indisponível"); }
    }

    public JsonNode external(Object body, boolean selfProfile) {
        JsonNode tree = mapper.valueToTree(body);
        JsonNode profile = selfProfile ? tree.path("data") : null;
        Map<String, String> cache = new HashMap<>();
        transform(tree, profile, cache);
        return tree;
    }

    private void transform(JsonNode node, JsonNode selfProfile, Map<String, String> cache) {
        if (node instanceof ObjectNode object) {
            var fields = new ArrayList<String>(); object.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                var value = object.get(field);
                if (IDENTITIES.contains(field) && value.isTextual()) {
                    String identity = cache.computeIfAbsent(value.asText(), this::publicId);
                    if (node == selfProfile && field.equals("email")) object.put("publicId", identity);
                    else object.put(field, identity);
                } else transform(value, selfProfile, cache);
            }
        } else if (node.isArray()) node.forEach(child -> transform(child, selfProfile, cache));
    }

    public byte[] externalFrame(byte[] body) {
        try { return mapper.writeValueAsBytes(external(mapper.readTree(body), false)); }
        catch (java.io.IOException error) { throw new IllegalArgumentException("Evento inválido", error); }
    }
}
