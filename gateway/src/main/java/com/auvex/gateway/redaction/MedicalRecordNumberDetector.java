package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks a medical record number (MRN), anchored on an "MRN" / "medical record number" label —
 * health data the gateway should keep out of prompts under HIPAA-style handling.
 */
@Component
public class MedicalRecordNumberDetector extends RegexDetector {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?i:mrn|medical record(?:\\s+(?:number|no\\.?|#))?)\\s*[:\\-]?\\s+([A-Z0-9\\-]{4,12})\\b");

  public MedicalRecordNumberDetector() {
    super(PiiType.MEDICAL_RECORD_NUMBER, 7, PATTERN);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}
