"""Typed exceptions raised by the GateWise client.

Every non-2xx response from the gateway is translated into one of these. The gateway
emits an OpenAI-style error envelope::

    {"error": {"message": "...", "type": "...", "code": "..."}}

so the human-readable reason is always available on ``GateWiseError.message`` and the raw
envelope on ``GateWiseError.body``.

The mapping is driven primarily by the HTTP status code, because a couple of distinct
conditions share the same ``type`` (for example a 401 bad key and a 400 bad request both
report ``invalid_request_error``). The ``type`` string is used only as a fallback when a
status code does not map to a more specific class.
"""

from __future__ import annotations

from typing import Any, Mapping, Optional

__all__ = [
    "GateWiseError",
    "APIConnectionError",
    "APITimeoutError",
    "AuthenticationError",
    "BadRequestError",
    "PolicyDeniedError",
    "NotFoundError",
    "RateLimitError",
    "UpstreamError",
]


class GateWiseError(Exception):
    """Base class for every error raised by the GateWise client.

    Attributes:
        message: The human-readable reason, taken from ``error.message`` when present.
        status_code: The HTTP status code, or ``None`` for transport-level failures.
        type: The gateway's ``error.type`` discriminator, when present.
        code: The gateway's ``error.code``, when present (e.g. ``invalid_api_key``).
        body: The full decoded response body, when one was returned.
    """

    def __init__(
        self,
        message: str,
        *,
        status_code: Optional[int] = None,
        type: Optional[str] = None,
        code: Optional[str] = None,
        body: Any = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.type = type
        self.code = code
        self.body = body


class APIConnectionError(GateWiseError):
    """The request never reached the gateway (DNS, refused connection, TLS, etc.)."""


class APITimeoutError(APIConnectionError):
    """The request timed out before the gateway responded."""


class BadRequestError(GateWiseError):
    """400 — the request was malformed or failed validation."""


class AuthenticationError(GateWiseError):
    """401 — the API key is missing, malformed, unknown, revoked or expired."""


class PolicyDeniedError(GateWiseError):
    """403 — the call was blocked by tenant policy or flagged as a prompt injection."""


class NotFoundError(GateWiseError):
    """404 — the requested resource does not exist for this tenant."""


class RateLimitError(GateWiseError):
    """429 — the tenant's rate limit or call budget was exceeded."""


class UpstreamError(GateWiseError):
    """502/503/504 — the upstream model provider was unavailable or timed out."""


# Status codes that map to a dedicated exception class. Checked before ``type``.
_STATUS_TO_ERROR = {
    400: BadRequestError,
    401: AuthenticationError,
    403: PolicyDeniedError,
    404: NotFoundError,
    429: RateLimitError,
    502: UpstreamError,
    503: UpstreamError,
    504: UpstreamError,
}

# Gateway ``error.type`` values, used as a fallback when the status code is unmapped.
_TYPE_TO_ERROR = {
    "invalid_request_error": BadRequestError,
    "policy_violation": PolicyDeniedError,
    "prompt_injection": PolicyDeniedError,
    "rate_limit_exceeded": RateLimitError,
    "upstream_error": UpstreamError,
    "not_found": NotFoundError,
}


def error_from_response(status_code: int, body: Any) -> GateWiseError:
    """Build the most specific ``GateWiseError`` for a non-2xx response.

    Args:
        status_code: The HTTP status code of the response.
        body: The decoded response body. Expected to be the OpenAI-style envelope
            ``{"error": {...}}``, but any shape (including a non-dict, e.g. when the
            gateway returns a plain-text 5xx) is handled gracefully.

    Returns:
        An ``GateWiseError`` subclass instance, populated from the envelope when available.
    """
    message = f"HTTP {status_code}"
    err_type: Optional[str] = None
    code: Optional[str] = None

    if isinstance(body, Mapping):
        envelope = body.get("error")
        if isinstance(envelope, Mapping):
            message = str(envelope.get("message") or message)
            raw_type = envelope.get("type")
            raw_code = envelope.get("code")
            err_type = str(raw_type) if raw_type is not None else None
            code = str(raw_code) if raw_code is not None else None
        elif "message" in body:
            # Some clients/proxies flatten the envelope; tolerate that too.
            message = str(body.get("message") or message)

    cls = _STATUS_TO_ERROR.get(status_code)
    if cls is None and err_type is not None:
        cls = _TYPE_TO_ERROR.get(err_type)
    if cls is None:
        # Unknown 4xx → generic client error; everything else → base error.
        cls = BadRequestError if 400 <= status_code < 500 else GateWiseError

    return cls(
        message,
        status_code=status_code,
        type=err_type,
        code=code,
        body=body,
    )
