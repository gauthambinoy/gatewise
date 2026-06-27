package com.auvex.gateway.audit;

import com.auvex.gateway.crypto.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts an audit text column on write and decrypts it on read, applied to the
 * redacted prompt/response fields of {@link AuditLog}.
 *
 * <p>Doing it in a converter keeps encryption invisible to every read and write site: the rest of
 * the code only ever sees plaintext, which matters because the hash chain is computed over
 * plaintext (in {@link AuditService#append}) and re-verified over plaintext (in {@link
 * AuditService#firstBrokenLink}). The converter sits strictly between the entity and the database.
 *
 * <p>It's a Spring bean so it can be given the configured {@link FieldCipher}; Spring Boot points
 * Hibernate's converter instantiation at the application context, so the managed instance — with
 * its cipher — is the one Hibernate uses. JPA can also instantiate a converter directly (outside
 * Spring), so the no-arg path leaves the cipher null and the converter degrades to pass-through
 * rather than throwing an NPE.
 */
@Converter
@Component
public class AuditFieldConverter implements AttributeConverter<String, String> {

  private final FieldCipher cipher;

  @Autowired
  public AuditFieldConverter(FieldCipher cipher) {
    this.cipher = cipher;
  }

  /** Fallback for non-Spring instantiation: no cipher, so values pass through unchanged. */
  public AuditFieldConverter() {
    this.cipher = null;
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return cipher == null ? attribute : cipher.encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    return cipher == null ? dbData : cipher.decrypt(dbData);
  }
}
